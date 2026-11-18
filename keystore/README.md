# 🔐 AniStream Release Keystore

This directory contains the production release keystore used to sign the Android Mobile and Android TV applications.

### Keystore Details:
* **Keystore File**: `anistream-release.jks`
* **Key Alias**: `anistream`
* **Keystore Password**: `anistream_release_pass`
* **Key Password**: `anistream_release_pass`
* **Algorithm**: RSA 2048-bit (SHA384withRSA)
* **Validity**: 10,000 days (Valid until year 2054)

### Building Signed Release APKs:
```bash
# Mobile:
cd android-app
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleRelease

# TV:
cd android-tv
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleRelease
```
Output APKs will be signed with both v1 (JAR signing) and v2 (APK Signature Scheme v2).
