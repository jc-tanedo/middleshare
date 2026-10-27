#!/usr/bin/env bash
set -euo pipefail

ANDROID_API=35
ANDROID_BUILD_TOOLS=35.0.0
MIDDLESHARE_RUNNER_TOOLS_DIR=${MIDDLESHARE_RUNNER_TOOLS_DIR:-"$HOME/.local/share/middleshare-runner"}

enable_archive_tools() {
    local directory="$MIDDLESHARE_RUNNER_TOOLS_DIR/usr/bin"
    if [[ -d "$directory" ]]; then
        export PATH="$directory:$PATH"
        printf '%s\n' "$directory" >> "$GITHUB_PATH"
    fi
}

install_archive_tools() (
    local prefix="$MIDDLESHARE_RUNNER_TOOLS_DIR" download_dir package
    local options=(
        -c "$prefix/apt/user.conf"
        -o "Dir::State::lists=$prefix/apt/lists"
        -o "Dir::Cache=$prefix/apt/cache"
        -o "APT::Sandbox::User=$(id -un)"
    )
    mkdir -p "$prefix/apt/lists/partial" "$prefix/apt/cache/archives/partial"
    # System apt hooks can write root-owned caches even for a private apt update.
    printf '%s\n' '#clear APT::Update::Pre-Invoke;' '#clear APT::Update::Post-Invoke;' \
        '#clear APT::Update::Post-Invoke-Success;' > "$prefix/apt/user.conf"
    # A private apt index/cache avoids changing the system package database.
    # apt still verifies repository signatures with the configured distro keys.
    apt-get "${options[@]}" update
    download_dir=$(mktemp -d "${RUNNER_TEMP:-/tmp}/middleshare-archives.XXXXXX")
    trap 'rm -rf "$download_dir"' EXIT
    cd "$download_dir"
    apt-get "${options[@]}" download "$@"
    for package in ./*.deb; do
        dpkg-deb --extract "$package" "$prefix"
    done
    for package in "$@"; do
        "$prefix/usr/bin/$package" -v >/dev/null
    done
)

can_install_system_packages() {
    (( EUID == 0 )) || { command -v sudo >/dev/null && sudo -n true; }
}

install_packages() {
    local elevated=() package archives_only=true
    if ! can_install_system_packages; then
        for package in "$@"; do
            if [[ "$package" != zip && "$package" != unzip ]]; then
                archives_only=false
            fi
        done
        if [[ "$archives_only" == true ]]; then
            echo 'Installing archive tools in the runner user directory.'
            install_archive_tools "$@"
            enable_archive_tools
            return
        fi
        echo "::error::Missing packages: $*. Run the runner as root or enable passwordless sudo for dependency installation."
        return 1
    fi
    if (( EUID != 0 )); then
        elevated=(sudo -n)
    fi
    "${elevated[@]}" apt-get -o DPkg::Lock::Timeout=120 update
    "${elevated[@]}" env DEBIAN_FRONTEND=noninteractive \
        apt-get -o DPkg::Lock::Timeout=120 install -y --no-install-recommends "$@"
}

ensure_tools() {
    local packages=() tool
    for tool in git curl unzip zip tar gzip python3 gh; do
        if ! command -v "$tool" >/dev/null; then
            packages+=("$tool")
        fi
    done
    if [[ $(dpkg-query -W -f='${Status}' ca-certificates 2>/dev/null || true) != 'install ok installed' ]]; then
        packages+=(ca-certificates)
    fi
    if (( ${#packages[@]} )); then
        echo "Installing missing runner packages: ${packages[*]}"
        install_packages "${packages[@]}"
    else
        echo 'Base runner tools are already installed.'
    fi
}

reuse_java() {
    local candidate=$1 tool version
    [[ -n "$candidate" ]] || return 1
    for tool in java javac javap keytool; do
        [[ -x "$candidate/bin/$tool" ]] || return 1
    done
    version=$("$candidate/bin/java" -version 2>&1) || return 1
    [[ $version =~ \"(17|21)(\.|\") ]] || return 1
    printf 'JAVA_HOME=%s\n' "$candidate" >> "$GITHUB_ENV"
    printf '%s/bin\n' "$candidate" >> "$GITHUB_PATH"
    echo 'Reusing the installed JDK.'
}

sdk_complete() {
    local root=$1 tool
    [[ -f "$root/platforms/android-$ANDROID_API/android.jar" ]] || return 1
    [[ -f "$root/platforms/android-$ANDROID_API/source.properties" ]] || return 1
    [[ -f "$root/build-tools/$ANDROID_BUILD_TOOLS/source.properties" ]] || return 1
    [[ -f "$root/platform-tools/source.properties" ]] || return 1
    [[ -x "$root/platform-tools/adb" ]] || return 1
    [[ -s "$root/licenses/android-sdk-license" ]] || return 1
    for tool in aapt aapt2 apksigner dexdump zipalign; do
        [[ -x "$root/build-tools/$ANDROID_BUILD_TOOLS/$tool" ]] || return 1
    done
}

prepare_android() {
    local java_ready=false sdk_ready=false java_bin root
    if reuse_java "${JAVA_HOME:-}"; then
        java_ready=true
    elif command -v javac >/dev/null; then
        java_bin=$(readlink -f "$(command -v javac)")
        if reuse_java "$(dirname "$(dirname "$java_bin")")"; then
            java_ready=true
        fi
    fi
    printf 'java_ready=%s\n' "$java_ready" >> "$GITHUB_OUTPUT"

    root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-"$HOME/.android/sdk"}}
    if sdk_complete "$root"; then
        sdk_ready=true
        echo 'Reusing the installed Android SDK.'
    elif ! mkdir -p "$root" 2>/dev/null || [[ ! -w "$root" ]]; then
        root="$HOME/.android/sdk"
        echo 'Using the runner user SDK directory for missing Android packages.'
        mkdir -p "$root"
    fi
    if [[ $sdk_ready == false && ! -w "$root" ]]; then
        echo '::error::The SDK directory is not writable. Set ANDROID_HOME to a writable directory.'
        return 1
    fi
    printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$root" "$root" >> "$GITHUB_ENV"
    printf '%s/platform-tools\n%s/build-tools/%s\n' "$root" "$root" "$ANDROID_BUILD_TOOLS" >> "$GITHUB_PATH"
    printf 'sdk_ready=%s\n' "$sdk_ready" >> "$GITHUB_OUTPUT"
    printf 'android_packages=platform-tools platforms;android-%s build-tools;%s\n' \
        "$ANDROID_API" "$ANDROID_BUILD_TOOLS" >> "$GITHUB_OUTPUT"
}

main() {
    if [[ $(uname -s) != Linux ]] || ! command -v apt-get >/dev/null; then
        echo '::error::The dependency setup requires an Ubuntu/Debian Linux runner.'
        return 1
    fi
    enable_archive_tools
    ensure_tools
    if [[ ${SETUP_ANDROID:-false} == true ]]; then
        prepare_android
    fi
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
    main "$@"
fi
