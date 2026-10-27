# Android entry points are retained by the Android Gradle plugin.

# Commons Compress registers ZIP extra-field handlers with Class.newInstance().
# The Python/FFmpeg runtime archives cannot be opened if R8 removes their public
# no-arg constructors or makes the handlers abstract. Names may still be shortened.
-keep,allowobfuscation class * implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    public <init>();
}
