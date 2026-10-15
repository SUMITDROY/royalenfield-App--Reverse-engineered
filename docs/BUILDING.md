# Build from source

## Host prerequisites

Use macOS or Linux, Python 3.10+ (stdlib only), a full JDK 17+ with `javac`/`keytool`,
and Android command-line tools. Set `JAVA_HOME` and `ANDROID_SDK_ROOT` or
`ANDROID_HOME`. SDK discovery also checks the usual macOS and Linux home paths.

```sh
sdkmanager 'platform-tools' 'platforms;android-35' 'build-tools;36.1.0'
python3 scripts/bootstrap.py
python3 runtime-module/tests/run.py
python3 runtime-module/build.py
```

`ANDROID_BUILD_TOOLS_VERSION` can override the build-tools directory; alternatives
are not verified. API 35 is the compile SDK, API 31 the minimum runtime SDK.
There is no Gradle dependency. Java is compiled to DEX, resources are packaged
with AAPT, then the APK is ZIP-aligned and signed. Output:
`output/tripper-screen-off-lsposed.apk`. The script does not install anything.

The compile-only Xposed API 82 is pinned to XposedBridge commit
`79f70cf567fdb42f83313f6cad97a6ffc878f3ac` and verified against SHA-256
`f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25`.
It is not bundled into the APK. Dependency tools are ignored, not vendored.

## Signing

The default build generates a key under `.local/signing/debug.jks`, alias
`tripper-debug`, using the public development password `android`. Keep this key
if you want subsequent local builds to update your first local installation.
This is a development identity, not a repository-wide release identity.

For your own stable signing key:

```sh
export RE_KEYSTORE=/private/path/tripper.jks
export RE_KEY_ALIAS=your-alias
export RE_KEYSTORE_PASSWORD_FILE=/private/path/password.txt
python3 runtime-module/build.py
```

Alternatively set `RE_KEYSTORE_PASSWORD` in your environment. Do not commit keys,
password files or environment files. Store and key passwords must match for the
current signing helper. The original workspace's private signer is not published.
A source build therefore need not have the same signer or hash as the APK already
on a phone. Builds are source-reproducible, not claimed byte-for-byte reproducible.

A different signer cannot update an installed module. Use the same private key
or uninstall **only the bridge module**, accepting loss of bridge settings and
re-pairing. If HFP is enabled, stop the call channel, reboot and disable automatic
HFP startup before removing Bluetooth scope. Never uninstall the official app
just to resolve the companion's signing error.

## Optional original-APK hook validation

The build does not need the official APK. To verify hook signatures independently,
obtain your own official version-code-193 APK and decode it locally with APKTool.
Do not upload that APK or its decoded output.

```sh
java -jar /path/to/apktool.jar d /path/to/your/base.apk -o analysis/apktool
python3 runtime-module/build.py --check-hooks
# Or use a decode elsewhere:
python3 runtime-module/tests/check_hook_contracts.py --decoded /path/to/decode
```

Only the declared bridge targets are checked by this script; it is not a complete
verification of lifecycle semantics, Bluetooth firmware or map delivery.
Runtime hooks independently enforce version code 193.

## Tests

Pure Java protocol/action tests require no phone or Android SDK:

```sh
python3 runtime-module/tests/run.py
```

To build isolated Android instrumentation tests:

```sh
python3 runtime-module/tests/build_device_test.py
adb install -r output/bridge-overlay-tests/overlay-test.apk
adb shell am instrument -w local.re.screenoff.overlaytest/local.re.screenoff.tests.OverlayDeviceTest
adb shell am instrument -w local.re.screenoff.overlaytest/local.re.screenoff.CallStateDeviceTest
adb uninstall local.re.screenoff.overlaytest
```

They test synthetic state and bitmap rendering without placing calls or connecting
to the Dash. They do not prove hardware behavior. GitHub CI builds the module and
test APK and runs JVM tests; device instrumentation is a separate manual step.

## Optional REA tool

```sh
git submodule update --init rea
cd rea
npm ci
npm run build
```

Read `rea/README.md` for its supported Node version, commands and integrations.
No REA MCP configuration or global installation is required for this module.
