#!/usr/bin/env bash
set -euo pipefail

# Supply credentials from a private file or secret manager; never commit them.
: "${NTUNE_RELEASE_STORE_FILE:?Set the absolute release keystore path}"
: "${NTUNE_RELEASE_STORE_PASSWORD:?Set the release keystore password}"
: "${NTUNE_RELEASE_KEY_ALIAS:?Set the release key alias}"
: "${NTUNE_RELEASE_KEY_PASSWORD:?Set the release key password}"
: "${ANDROID_HOME:?Set the Android SDK path}"

app_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
repo_root="$(cd -- "$app_root/.." && pwd)"
build_tools="$ANDROID_HOME/build-tools/36.0.0"
cd "$app_root"

bash ./gradlew :pleos-app:assembleRelease :pleos-app:testDebugUnitTest \
    :app:testDebugUnitTest :vehicle-probe:testDebugUnitTest \
    --console=plain --no-daemon --max-workers=2 "$@"

source_apk="$app_root/pleos-app/build/outputs/apk/release/pleos-app-release.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$source_apk"
badging="$("$build_tools/aapt" dump badging "$source_apk")"
if [[ "$badging" != *"package: name='kr.nostalife.pleos.contest'"* ]] || \
   [[ "$badging" == *"application-debuggable"* ]]; then
    echo 'Refusing to publish an unexpected package or a debuggable APK.' >&2
    exit 1
fi
"$build_tools/zipalign" -c -P 16 4 "$source_apk"
mkdir -p "$repo_root/apk"
cp "$source_apk" "$repo_root/apk/Nostalife_NTune_release.apk"
printf '%s\n' "$badging"
sha256sum "$repo_root/apk/Nostalife_NTune_release.apk"
