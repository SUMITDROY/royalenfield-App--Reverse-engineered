# Device setup

## Preserve the official app

Use the original signed Royal Enfield app, version code 193. Updating the
companion does not replace it or change authentication. Other RE versions need
new hook analysis; the version gate intentionally disables unsupported hooks.
No official APK download is provided here.

Before changing the module, finish navigation and record your current module
settings. These instructions assume root and a working compatible Xposed runtime
are already installed; they do not flash or root the phone.

## Screen-off navigation

1. Build the module following [BUILDING.md](BUILDING.md), then install its APK.
2. Enable **Tripper iPhone Bridge** in LSPosed/Vector and scope it to Royal Enfield
   (`com.royalenfield.reprime`). Reopen Royal Enfield so hooks load.
3. Grant the official app its normal location/navigation permissions. Set suitable
   background/battery settings for it and the companion on your ROM.
4. Connect Android to Tripper Dash using the normal official setup. Start a route
   while stationary. Lock the Android display for at least 60 seconds and check
   real progress/map updates on the Dash, then unlock and stop navigation.
5. Inspect navigation logs for `RUNTIME_HOOKS_READY_VERSION_193`. Installation,
   a connected IPC provider or repeated frames alone do not prove map updates.

## iPhone media and notification bridge

1. Open the Android companion, allow Nearby Devices and notifications, then tap
   **Start bridge**. Allow its foreground service and suitable battery exemption.
2. In LightBlue on iPhone, find/connect to the Android phone. On the tested setup
   it appears as **OnePlus 13**. Solicited ANCS UUID:
   `7905F431-B5CE-4E99-A40F-4B1E122D00D0`.
3. Accept pairing and notification sharing. If needed, read encrypted characteristic
   `EAE10101-238E-4A5C-9D1A-623139AEEE10` to trigger the pairing prompt.
4. Close LightBlue after bonding and allow **Share System Notifications** in iOS
   Bluetooth settings if offered. Check ANCS and AMS readiness in the bridge.
5. Start iPhone music. Enable Royal Enfield notification access from the companion
   if required. Restart the official app to load updated hooks.
6. While stationary, check track metadata and play/pause/next on the Dash. Optional
   cover lookup sends title/artist to Apple; leave it off unless wanted.

LightBlue is used for initial BLE pairing, not as the ongoing relay. An optional
pairing-helper source project is in `iphone-companion/`, but its signed device
installation is unverified. You can continue using LightBlue.

## Optional full call controls

Read [CALL-CONTROLS.md](../runtime-module/CALL-CONTROLS.md) before enabling HFP.
This path is experimental and can conflict with headset controls.

1. Enable the module's **Bluetooth** (`com.android.bluetooth`) scope in addition
   to Royal Enfield. The API-35 hook addresses a receiver-registration failure in
   the tested ROM's dormant HFP client; other ROMs may lack a usable client.
   Restart the relevant process/reboot to load newly enabled hooks before HFP startup.
2. Grant **Tripper iPhone Bridge** Superuser in KernelSU/KernelSU Next separately
   from ADB. Restart the bridge after changing its root grant if `su` remains hidden.
3. Tap **Pair iPhone for calls** and approve discoverability, then select the
   Android phone from iPhone Settings → Bluetooth and accept pairing on both.
4. Tap **Connect iPhone calls** on Android and select the **iPhone**, not earbuds,
   a speaker or helmet. An unnamed **Bluetooth device** can be the iPhone before
   its name resolves. Confirm the device rather than guessing. On the tested
   phone, mistakenly selecting CMF earbuds caused failed call-channel connections.
5. Wait for **iPhone call channel connected** / `hfp_ready=true`. ANCS “calls ready”
   means notification service readiness; it is not proof of an HFP connection.
6. Test incoming → Answer → active → Disconnect, then Decline on another call.
   Test headset audio/buttons and screen-off navigation together. Physical Dash
   Next/Previous select an available call action and Play/Pause invokes it only
   if that firmware's buttons reach the hooked callbacks; verify this on hardware.

Android's HFP audio route is disabled to keep audio on iPhone/headset. This does
not guarantee the iPhone will treat all headset connections/control buttons
identically. An unresolved report affects both earphone call/music buttons.

## Stop and rollback

**Stop iPhone call channel** disconnects HFP while preserving BLE media/notifications.
This is the first isolation step for headset-button problems. Ongoing call state
and HFP Disconnect are unavailable while that channel is stopped; ANCS incoming
notification actions may remain available when the iPhone advertises them.

To remove the Bluetooth compatibility hook safely: stop the call channel, keep
its saved enabled switch off, then reboot to reset the nonpersistent HFP property
before disabling Bluetooth scope. Removing the fix while the dormant HFP profile
is enabled can reproduce the ROM's Bluetooth crash. If Bluetooth crash-loops,
disable bridge HFP startup, reset `bluetooth.profile.hfp.hf.enabled` to false using
authorized recovery/root access or reboot, and retain the scope until HFP is off.

Disable the module and reopen Royal Enfield to remove navigation hooks. The
official signed app remains installed. Keep your own previously working companion
APK/key for rollback; no private device backups are published here.
