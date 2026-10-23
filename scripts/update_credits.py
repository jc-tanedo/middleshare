#!/usr/bin/env python3
"""Regenerate the offline About catalog after :app:exportThirdPartyArtifacts.

Uses the exact resolved artifacts, their POMs and shipped bundle contents. Never
executes downloaded package recipes or binaries. Unknown native/Python components
fail generation so their attribution must be reviewed before a build can ship.
"""
import hashlib
import io
import json
import pathlib
import re
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
LICENSES = ASSETS / 'licenses'
POM_CACHE = ROOT / 'app/build/credit-poms'
POM_CACHE.mkdir(parents=True, exist_ok=True)
records = []
registry = json.loads((ROOT / 'third_party/native-components.json').read_text())
notices = {p.stem: 'licenses/' + p.name for p in LICENSES.glob('*.txt')}

def sha(data):
    return hashlib.sha256(data).hexdigest()

def standard_license(name):
    lower = name.lower()
    if 'apache' in lower and '2' in lower: return 'Apache-2.0'
    if 'lgpl' in lower or 'lesser general public' in lower:
        for version in ['3.0', '2.1', '2.0']:
            if version in lower: return 'LGPL-' + version + '-only'
        return None
    if 'gpl' in lower or 'general public' in lower:
        if '3' in lower: return 'GPL-3.0-only'
        if '2' in lower: return 'GPL-2.0-only'
        return None
    if 'eclipse' in lower and '1' in lower: return 'EPL-1.0'
    return {'mit': 'MIT', 'bsd 2-clause': 'BSD-2-Clause', 'bsd 3-clause': 'BSD-3-Clause'}.get(lower)

pom_cache = {}
def pom(coordinate, artifact=None):
    if coordinate in pom_cache: return pom_cache[coordinate]
    group, name, version = coordinate.split(':')
    cached = list(artifact.parent.parent.glob(f'*/{name}-{version}.pom')) if artifact else []
    target = POM_CACHE / (coordinate.replace(':', '_') + '.xml')
    if cached:
        data = cached[0].read_bytes()
    elif target.exists():
        data = target.read_bytes()
    else:
        base = 'https://dl.google.com/dl/android/maven2' if group.startswith('androidx.') else 'https://repo.maven.apache.org/maven2'
        url = f'{base}/{group.replace(".", "/")}/{name}/{version}/{name}-{version}.pom'
        data = urllib.request.urlopen(url, timeout=30).read()
        target.write_bytes(data)
    tree = ET.fromstring(data)
    for node in tree.iter(): node.tag = node.tag.rsplit('}', 1)[-1]
    result = {'name': tree.findtext('name') or name, 'source': tree.findtext('url') or tree.findtext('scm/url') or '',
              'licenses': [(node.findtext('name') or '', node.findtext('url') or '') for node in tree.findall('licenses/license')]}
    pom_cache[coordinate] = result
    parent = tree.find('parent')
    if parent is not None and (not result['licenses'] or not result['source'] or '${' in result['source']):
        inherited = pom(':'.join(parent.findtext(k) for k in ['groupId','artifactId','version']))
        if not result['licenses']: result['licenses'] = inherited['licenses']
        if not result['source'] or '${' in result['source']: result['source'] = inherited['source']
    if result['source'].startswith('scm:git:'): result['source'] = result['source'][8:]
    if result['source'].startswith('git://'): result['source'] = 'https://' + result['source'][6:]
    if not result['licenses'] or not result['source'].startswith(('http://', 'https://')):
        raise ValueError(f'Review missing publisher license/source metadata for {coordinate}: {result}')
    return result

def embedded_notices(data, identity):
    result = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in archive.namelist():
            base = name.rsplit('/', 1)[-1].lower()
            if name.endswith('/') or not re.match(r'^(license|notice|copying)(\.|$)', base): continue
            content = archive.read(name)
            try: text = content.decode('utf-8')
            except UnicodeDecodeError: continue
            filename = 'notice-' + sha((identity + name).encode())[:20] + '.txt'
            (LICENSES / filename).write_text(text)
            result.append('licenses/' + filename)
    return result

artifacts = json.loads((ROOT / 'app/build/third-party-artifacts.json').read_text())
artifact_hashes = {}
for artifact in artifacts:
    path = pathlib.Path(artifact['file'])
    if sha(path.read_bytes()) != artifact['sha256']: raise ValueError(f'Artifact changed during inventory: {path}')
    artifact_hashes[artifact['id']] = artifact['sha256']
    info = pom(artifact['id'], path)
    license_assets = [notices[key] for name, _ in info['licenses'] if (key := standard_license(name))]
    license_assets += embedded_notices(path.read_bytes(), artifact['id'])
    if path.suffix == '.aar':
        with zipfile.ZipFile(path) as aar:
            if 'classes.jar' in aar.namelist(): license_assets += embedded_notices(aar.read('classes.jar'), artifact['id'] + ':classes')
    records.append(dict(id=artifact['id'], name=info['name'], version=artifact['id'].split(':')[-1],
                        category='Optional video add-on · Android libraries', license='; '.join(name for name, _ in info['licenses']),
                        source=info['source'], licenseUrls=[url for _, url in info['licenses'] if url],
                        noticeAssets=sorted(set(license_assets))))

# Audit native libraries inside every ABI package, not just the phone architecture.
files = {key: [] for key in registry}
unknown = []
def native_owner(name):
    if name.startswith('usr/lib/python3.12/site-packages/'):
        package = name.split('/')[4]
        return {'Cryptodome': 'pycryptodome', 'mutagen': 'mutagen'}.get(package)
    if '/lib-dynload/_decimal.' in name: return 'mpdecimal'
    if '/lib-dynload/unicodedata.' in name: return 'unicode'
    if name.startswith('usr/lib/python3.12/'): return 'python'
    if name.startswith(('usr/lib/engines-3/', 'usr/lib/ossl-modules/')): return 'openssl'
    if name.startswith('usr/lib/libv4l/'): return 'libv4l'
    if name.endswith('/cert.pem'): return 'mozilla-ca'
    base = name.rsplit('/', 1)[-1]
    if base in ('v4l1compat.so', 'v4l2convert.so'): return 'libv4l'
    for key, entry in registry.items():
        if any(re.fullmatch('lib' + re.escape(prefix) + r'(?:\.so(?:\..*)?|\.a|\.la)', base)
               for prefix in entry.get('prefixes', [])): return key
    return None

for artifact in artifacts:
    if not artifact['id'].startswith('io.github.junkfood02.youtubedl-android:'): continue
    with zipfile.ZipFile(artifact['file']) as aar:
        for entry in aar.namelist():
            if not entry.startswith('jni/') or entry.endswith('/'): continue
            abi = entry.split('/')[1]
            if entry.endswith('.zip.so'):
                with zipfile.ZipFile(io.BytesIO(aar.read(entry))) as inner:
                    for name in inner.namelist():
                        if name.endswith('/'): continue
                        is_native = re.search(r'\.(?:so(?:\.[\d.]+)?|a|la)$', name)
                        is_python_package = '/site-packages/' in name
                        if not (is_native or is_python_package or name.endswith('/cert.pem')): continue
                        owner = native_owner(name)
                        if owner is None:
                            unknown.append(f'{entry}: {name}')
                            continue
                        files[owner].append(f'{abi}/{name}')
            else:
                owner = native_owner(entry)
                if owner is None:
                    unknown.append(entry)
                    continue
                files[owner].append(entry)

if unknown: raise ValueError('Add attribution for bundled components:\n' + '\n'.join(sorted(set(unknown))))

for key, names in files.items():
    if not names: raise ValueError(f'Attribution registry entry no longer matches shipped files: {key}')
    entry = registry[key]
    records.append(dict(id='native:' + key, name=entry['name'], version=entry['version'],
                        category='Optional video add-on · runtimes and codecs', license=entry['license'], source=entry['source'],
                        licenseUrls=[entry.get('licenseSource') or entry['metadataSource']],
                        noticeAssets=entry['noticeAssets'], files=sorted(set(names))))

payload = ROOT / 'app/src/video/res/raw/ytdlp'
with zipfile.ZipFile(payload) as archive:
    roots = {name.split('/')[0] for name in archive.namelist() if '/' in name and name.endswith('.py')}
    if roots != {'yt_dlp', 'yt_dlp_ejs'}: raise ValueError(f'Review new vendored Python code in the zipapp: {roots}')
    library_js = archive.read('yt_dlp_ejs/yt/solver/lib.min.js').decode()
    banner = re.match(r'/\*![\s\S]*?\*/', library_js).group(0)
    (LICENSES / 'ejs-third-party-notices.txt').write_text(banner)
    dependencies = re.findall(r'Name: ([^\n]+)\n \* Version: ([^\n]+)\n \* License: ([^\n]+)\n \* Repository: ([^\n]+)', banner)
    if not dependencies: raise ValueError('Review EJS dependency notices: no dependencies were identified')
    for name, ver, license_id, source in dependencies:
        records.append(dict(id='javascript:' + name, name=name, version=ver,
                            category='Optional video add-on · runtimes and codecs', license=license_id,
                            source=source.removeprefix('git+').removesuffix('.git'),
                            licenseUrls=[], noticeAssets=['licenses/ejs-third-party-notices.txt']))
    ejs = re.search(r"__version__ = version = '([^']+)'", archive.read('yt_dlp_ejs/_version.py').decode()).group(1)
version = re.search(r'String VERSION = "([^"]+)"', (ROOT / 'app/src/video/java/dev/middleshare/BundledYtDlp.java').read_text()).group(1)
for key,name,ver,source in [('yt-dlp','yt-dlp',version,'https://github.com/yt-dlp/yt-dlp'),('yt-dlp-ejs','yt-dlp EJS',ejs,'https://github.com/yt-dlp/ejs')]:
    records.append(dict(id='vendored:' + key, name=name, version=ver, category='Optional video add-on · runtimes and codecs',
                        license='Unlicense', source=source, licenseUrls=[source + '/blob/master/LICENSE'], noticeAssets=['licenses/Unlicense.txt']))

vendored_files = {'app/src/video/res/raw/ytdlp': sha(payload.read_bytes())}
for entry in json.loads((ROOT / 'third_party/additional-components.json').read_text()):
    for path in entry['files']:
        file = (ROOT / path).resolve()
        if not file.is_relative_to(ROOT) or not file.is_file(): raise ValueError(f'Invalid credited source file: {path}')
        vendored_files[path] = sha(file.read_bytes())
    records.append(entry)

# Build/test tools are shown separately and are not claimed to be packaged in the APK.
build = (ROOT / 'app/build.gradle').read_text()
tools = {
 'Gradle': re.search(r'gradle-([\d.]+)-bin', (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text()).group(1),
 'Android Gradle Plugin': re.search(r"com.android.application' version '([^']+)'", (ROOT / 'build.gradle').read_text()).group(1),
 'JUnit': re.search(r"junit:junit:([^']+)'", build).group(1),
 'Robolectric': re.search(r"org.robolectric:robolectric:([^']+)'", build).group(1)}
for name,source,license_id in [('Gradle','https://github.com/gradle/gradle','Apache-2.0'),('Android Gradle Plugin','https://android.googlesource.com/platform/tools/base','Apache-2.0'),('JUnit','https://github.com/junit-team/junit4','EPL-1.0'),('Robolectric','https://github.com/robolectric/robolectric','MIT')]:
    records.append(dict(id='tool:' + name, name=name, version=tools[name], category='Build and test tools',
                        license=license_id, source=source, licenseUrls=[], noticeAssets=[notices[license_id]]))

for record in records:
    if not record['noticeAssets']: raise ValueError(f'Include the license text for {record["id"]}')
    for asset in record['noticeAssets']:
        if not (ASSETS / asset).is_file(): raise ValueError(f'Missing offline notice {asset} for {record["id"]}')
records.sort(key=lambda entry: (entry['category'], entry['name'].lower(), entry['id']))
catalog = dict(schema=1, artifactHashes=artifact_hashes, toolVersions=tools,
               registryHashes={str(p.relative_to(ROOT)):sha(p.read_bytes()) for p in [*sorted((ROOT / 'third_party').glob('*.json')), pathlib.Path(__file__).resolve()]},
               licenseHashes={'licenses/'+p.name:sha(p.read_bytes()) for p in sorted(LICENSES.glob('*.txt'))},
               vendoredFiles=vendored_files, components=records)
(ASSETS / 'third_party.json').write_text(json.dumps(catalog,indent=2,ensure_ascii=False)+'\n')
print(f'Generated {len(records)} credits ({len(artifacts)} resolved runtime artifacts); all have offline license texts.')
