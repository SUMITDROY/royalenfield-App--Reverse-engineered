# Tripper Screen-Off Hooks and iPhone Bridge (experimental)

Version 0.4 adds supported incoming notification actions and an optional rooted
HFP call channel with actual call state and per-call disconnect. See
[CALL-CONTROLS.md](CALL-CONTROLS.md) for pairing, KernelSU authorization, Vector
Bluetooth scope, tests and rollback. The Android-15 Bluetooth receiver compatibility
hook runs only in the Bluetooth process; the navigation hooks remain RE-only.

Version 0.3 adds a BLE iPhone call/media bridge, playback controls, opt-in catalog
artwork lookup and an optional navigation overlay. See [IPHONE-BRIDGE.md](IPHONE-BRIDGE.md)
for pairing, limitations and diagnostics. The screen-off behavior below is
preserved. Keep your own previously working module APK/key for rollback.

This separate LSPosed module targets the original Royal Enfield Android app
`com.royalenfield.reprime`, version code 193, Android 12 or later. It does not
repackage the app or change account authentication. Navigation hooks run only in that
app's main process; the optional HFP compatibility hook runs in Bluetooth. Other RE versions leave navigation hooks disabled.

Enable the module in LSPosed and scope navigation to RoyalEnfield. The optional
Android-15 call compatibility fix additionally needs Bluetooth scope. Force-stop and
reopen RoyalEnfield. Look for `RUNTIME_HOOKS_READY_VERSION_193` in the persistent
log. `RUNTIME_HOOKS_DISABLED` or `HOOK_SETUP_FAILED` means it is not active.
If setup fails, already-installed behavioral hooks are removed.

The module retains navigation and SDK lifecycle callbacks only when the map
encoder is running, the connected device is the Tripper Dash, and the display
is non-interactive. Version 0.2 sets the Fragment base callback flag directly (the full base callback
behavior in version 193) and
pairs retained SDK callbacks to avoid duplicate starts/resumes. The navigation
screen-waking lock becomes a CPU-only lock and the known navigation window flags
are suppressed. Home/back behavior with the display on remains normal.

Logs live under the official app's external files directory:
`/sdcard/Android/data/com.royalenfield.reprime/files/screenoff/navigation.log`.
The shared logger rotates at approximately 2 MiB. It records hook setup errors,
encoder exceptions, bitmap callback counts, sample changes, power state and
stalled-update observations. It does not save map images or route coordinates.
`python3 patches/export_logs.py --output output/runtime-phone-logs` exports logs
and available Android process exit reasons. Abrupt OS termination cannot always
write an exact failure reason.

Installation and hook loading do not establish working map delivery. Android's
rendering surfaces may still stop updating when the display turns off. Test a
stationary route with the real Dash, confirm map updates continue after locking,
then confirm screen-on recovery and navigation stop. No universal Android
compatibility is claimed. Version 193 and the connected OnePlus are the current
targets.

To roll back, disable this module in LSPosed, then force-stop/reopen RoyalEnfield.
The official APK stays installed. If HFP was enabled, follow the Bluetooth
shutdown/reboot ordering in [SETUP.md](../docs/SETUP.md#stop-and-rollback) first.

Build/setup: [BUILDING.md](../docs/BUILDING.md) and [SETUP.md](../docs/SETUP.md).
The compile-only Xposed API 82 is fetched with a pinned checksum. Local builds use
a private local development key, not the original workspace signer. No raw crash
logs or previously installed APK backups are published. See
[TESTING.md](../docs/TESTING.md) for current evidence and unresolved headset controls.
