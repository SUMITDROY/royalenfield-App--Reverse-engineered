# BLE iPhone bridge

For the current 0.4 call update, use [CALL-CONTROLS.md](CALL-CONTROLS.md) and
[setup](../docs/SETUP.md). The BLE transport below is retained.


The Android phone remains the only Dash connection. The bridge advertises as a
Bluetooth LE accessory, bonds with the iPhone, and consumes Apple's ANCS and AMS
services over the iPhone-initiated connection. Call audio and media audio stay on
the iPhone/headset. No iPhone app is required after initial pairing.

## Setup

1. Install `output/tripper-screen-off-lsposed.apk` as an update to the existing
   module. Keep its LSPosed scope on Royal Enfield only; restart Royal Enfield to
   load new hooks. This does not replace or resign Royal Enfield.
2. Open **Tripper iPhone Bridge** on Android, tap **Start bridge**, and allow
   Nearby Devices and notifications. Set the bridge battery usage to Unrestricted.
3. On the iPhone open LightBlue, find the Android phone (solicited service
   `7905F431-B5CE-4E99-A40F-4B1E122D00D0`), and connect. For an unusually long
   Android Bluetooth name it may appear unnamed; the scan response contains a
   `TripperBridge` service-data marker instead. Accept BLE pairing and system
   notification sharing. If no pairing dialog appears, read the encrypted local
   characteristic `EAE10101-238E-4A5C-9D1A-623139AEEE10`.
4. Close LightBlue after bonding. If required, enable **Share System Notifications**
   in the iPhone's Bluetooth device settings. Turn off Focus for the first test.
5. Play media on iPhone. The bridge should show **Calls: ready**, **Media: ready**
   and the current title. Reopen Royal Enfield; connect Android to the Dash and
   start navigation while stationary. The bridge's **Dash hook: connected** means
   the IPC poll is alive, not that the physical Dash received packets.
6. Enable **Look up cover art online** if wanted. This sends track title/artist
   to Apple's iTunes catalog, and downloads a 96×96 image only on an exact
   normalized artist/title match. It may not match YouTube video titles or tracks
   absent from the catalog. A validated internet network is required; cellular
   is preferred over the Dash's local Wi-Fi. This is retrieved artwork, not an
   image received from the iPhone.

## Implemented paths

- ANCS live incoming-call alerts and caller titles; stale notification backlog
  is ignored. Removed alerts clear the ringing indication. A 90-second timeout
  clears an incoming alert if removal is lost. A removed ringing notification
  does not prove the call ended: it may have been answered.
- AMS player name, artist, album, track title, playback state, play/pause,
  previous/next and volume commands. Commands are checked against the iPhone's
  reported supported command list when available. A successful GATT write
  confirms forwarding, not that the media app executed the command.
- An Android MediaSession publishes the iPhone's metadata. Version-193 RE media
  callbacks are also intercepted for command forwarding while the bridge is
  connected. RE's existing metadata/75×75 artwork path is used when its foreground
  service and Dash transport exist.
- Optional map overlay with track/artwork, caller title or a short notification
  title. This overlays the navigation projection; it does not modify native Dash
  firmware backgrounds. The overlay can be disabled independently.
- Only the module UID and the original signed Royal Enfield package can access
  bridge IPC or artwork. Other Android applications are denied.

## Limits and diagnostics

No iPhone/YouTube video capture, audio routing, message replies or full iPhone
screen mirroring are implemented. Version 0.4 adds supported incoming actions and
optional root HFP call controls; see the call guide. App-specific AMS behavior,
iPhone reconnection, Dash button mappings and combined navigation behavior need
physical testing. BLE peripheral support is required. The hooks stay pinned to
RE version code 193; screen-off lifecycle behavior from 0.2 is preserved and bridge
hook failures are isolated from those existing hooks.

The setup screen has **Export bridge logs**. Technical logs rotate at 1 MiB in
the module's private files: `iphone-bridge.log` and `iphone-bridge.previous.log`.
They record BLE status codes, operation rejection/timeouts, missing ANCS/AMS,
unsupported commands, catalog match failures, and Dash submission stages. Our
normal status entries omit caller/track/message text; exception stack traces are
saved for diagnosis. Every 10 seconds the log records Bluetooth service readiness,
RE hook age, last media/notification event age, screen/Doze state and battery
exemption. A long media-event age can simply mean the same track is playing;
it does not prove a connection failure. RE's own SDK logging
may include labels when forwarding native packets. Abrupt OS termination cannot
always record an exact final cause. Existing navigation logs remain available
through `patches/export_logs.py`.

For a combined USB export after reconnecting, run
`python3 patches/export_iphone_bridge_logs.py`. It reads private bridge files with
the already-authorized ADB root access and includes RE navigation logs, filtered
logcat and Android process-exit records for both packages. Without root, use the
app's export button for bridge logs. A missing previous log can be normal before
the first rotation; export-result.json records each read's result.

Current validation and outstanding hardware checks are maintained in
[TESTING.md](../docs/TESTING.md). Follow [setup rollback](../docs/SETUP.md#stop-and-rollback)
when stopping/removing the HFP channel; private rollback APKs are not distributed.

## References

- [Apple ANCS](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Specification/Specification.html)
- [Apple AMS](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleMediaService_Reference/Specification/Specification.html)
- [AMS attribute list](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleMediaService_Reference/Appendix/Appendix.html)
- [ANCS peripheral pairing reference](https://github.com/MarcoPeretti/ultra)

Build: `python3 runtime-module/build.py`. Protocol tests:
`python3 runtime-module/tests/run.py`. Xposed API remains compile-only.
