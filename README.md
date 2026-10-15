# Tripper Dash: screen-off navigation and iPhone bridge

An experimental Android Xposed/LSPosed module for the Royal Enfield Tripper Dash.
Keep navigation running from Android with its screen off, and relay iPhone music
and call information through the Android phone to the Dash.

**Keep the official, originally signed Royal Enfield app installed.** This project
builds a separate companion/module APK. Re-signing the official app broke backend
account access during the investigation; that approach is not the installation path.
No Royal Enfield APKs, firmware, decompiled application sources, personal logs or
private signing keys are included in this repository.

## Current status

This is a version-pinned prototype, not universal Android support.

- Screen-off navigation: reported working by the owner on the tested OnePlus 13
  and Tripper Dash after the lifecycle fix. Battery, thermal and wider-device
  behavior are not certified.
- iPhone media: ANCS/AMS pairing, metadata and command forwarding implemented;
  the owner reported the media bridge working. Physical Dash button mappings
  still need explicit validation for the call-controls update.
- Calls: a real incoming iPhone call was detected, Answer was sent and confirmed
  by an active-call state, then Disconnect was sent and confirmed by call removal.
  This verifies the bridge path, not every bike/headset button combination.
- **Known unresolved issue:** the owner reported that earphone call and music
  buttons stopped working with the earphones connected to the iPhone. The Android
  HFP channel was temporarily disconnected for an isolation test; its result is
  pending. Do not assume simultaneous headset controls are solved.
- Cover art: optional exact artist/title matching against Apple's catalog;
  downloaded artwork is not an image supplied by the iPhone. End-to-end artwork
  delivery is not fully verified.
- Custom native speedometer/clock wallpaper, caller profile photos, iPhone video
  streaming and arbitrary outbound dialing are **not implemented**.

See [testing evidence and open issues](docs/TESTING.md),
[installation](docs/SETUP.md), [build instructions](docs/BUILDING.md),
[architecture](docs/ARCHITECTURE.md) and [troubleshooting](docs/TROUBLESHOOTING.md).

## Requirements

- Original Royal Enfield Android package `com.royalenfield.reprime`, **version code
  193**, version `10.1.23(45)`. Hooks deliberately disable on other app versions.
- Android 12/API 31 or newer for the module; **only OnePlus 13 on Android 15/API 35
  has been used for this setup**. The optional Bluetooth compatibility hook is
  restricted to API 35. API support in the manifest does not establish device support.
- A rooted Android phone with a compatible LSPosed/Vector runtime. The tested
  installation uses KernelSU/KernelSU Next and Vector. Rooting/framework
  installation itself is outside this repository's setup automation.
- Tripper Dash and the original app's normal navigation/network permissions.
- For iPhone integration: BLE peripheral support on Android, an iPhone supporting
  ANCS/AMS, notification sharing and initial pairing through LightBlue.
- For full call state/Disconnect: the ROM must contain a working HFP client, and
  KernelSU must separately grant Superuser to **Tripper iPhone Bridge**. ADB root
  permission alone is insufficient.

## Build and install

A device-tested experimental APK and its SHA-256 are available under
[GitHub Releases](https://github.com/SUMITDROY/royalenfield-App--Reverse-engineered/releases).
Read the release notes and signing guidance before installing. CI artifacts use
new development signing keys and cannot be assumed to update an existing install.


Install Python 3.10+, JDK 17+ and the Android SDK, then:

```sh
git clone --recurse-submodules https://github.com/SUMITDROY/royalenfield-App--Reverse-engineered.git
cd royalenfield-App--Reverse-engineered
export JAVA_HOME=/path/to/jdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
sdkmanager 'platform-tools' 'platforms;android-35' 'build-tools;36.1.0'
python3 scripts/bootstrap.py
python3 runtime-module/tests/run.py
python3 runtime-module/build.py
adb install -r output/tripper-screen-off-lsposed.apk
```

These commands target macOS/Linux. Windows builds are not tested; use a Linux
build environment if needed. `bootstrap.py` downloads only the pinned,
checksum-verified compile-only Xposed API. The build creates a **local development
signing key** under ignored `.local/`; maintainers can configure their own key.
A newly generated key cannot update an APK signed by someone else's key. Read
[signing and reproducibility](docs/BUILDING.md) before replacing an existing module.

In LSPosed/Vector, enable the module and select Royal Enfield. Restart Royal
Enfield after finishing any current navigation session. For optional HFP calls,
read the Bluetooth scope and rollback instructions **before enabling that scope**.
Continue with the [full setup guide](docs/SETUP.md).

## How it works

```text
 iPhone -- BLE ANCS/AMS --> Android bridge -- private IPC --> official RE app --> Dash
        -- optional HFP -> root call worker ------^
 iPhone -- call/music audio --> iPhone or headset
```

Version-pinned runtime hooks retain selected navigation/rendering lifecycle
callbacks while the Dash encoder is running and the Android display is off.
The separate BLE service publishes iPhone media/call state. Hooks in the official
app forward controls and add an optional overlay to its navigation projection.
This does not change Dash firmware or its native wallpaper.

The optional root worker uses Android's HFP client for actual call state and
per-call Answer/Disconnect. Android call audio routing is disabled, but an extra
HFP connection can still interact with iPhone/headset behavior. See the known
issue above; the repository does not claim a transparent headset experience.

## Logs and failure reports

On Android, tap **Export bridge logs**. Logs persist locally without USB and
rotate; Android may kill a process without giving it a chance to record a final
cause. Warnings about old frames or missing callbacks are observations, not proof
of an exact cause.

With an authorized USB connection:

```sh
python3 patches/export_iphone_bridge_logs.py --output output/my-bridge-report
python3 patches/export_logs.py --output output/my-navigation-report
python3 patches/check_device.py --output output/my-device-check
```

Specify `--serial` when multiple devices are attached. Private bridge file export
requires authorized ADB root; the in-app export is the fallback. Exports can
contain personal/device data from system or vendor logs. Review and redact them
before attaching to an issue. Read [privacy](docs/PRIVACY.md).

## Source layout

- `runtime-module/`: Android bridge, Xposed hooks, resources and tests.
- `patches/java/`: shared navigation diagnostics used by the module.
- `patches/export_*.py`, `patches/check_device.py`: local diagnostic helpers.
- `patches/prepare_screen_off.py`, `add_diagnostics.py`, `package_base.py`,
  `install_patch.py`: historical re-packaging experiments, **not the supported
  install route**. They require user-supplied artifacts and are retained as research.
- `iphone-companion/`: optional Swift pairing-helper source; unsigned build was
  checked, device installation was not completed. LightBlue is the tested route.
- `rea/`: pinned [REA](https://github.com/morluto/rea) investigation tool submodule,
  with its own license and installation instructions; not needed to build the module.
- `scripts/`: portable dependency/build configuration.
- `docs/`: setup, design, validation, privacy and known limitations.

## Contributing and license

See [CONTRIBUTING.md](CONTRIBUTING.md) for validation and issue-report requirements,
[SECURITY.md](SECURITY.md) for private reporting, and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency attribution.
Project-authored source is available under the [MIT License](LICENSE). Third-party
software retains its own license. Royal Enfield names identify the compatible
product; this project is independent and is not affiliated with Royal Enfield.
Test interactions while stationary.
