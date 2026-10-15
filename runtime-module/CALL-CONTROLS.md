# iPhone call controls — 0.4 experimental

Keep the official Royal Enfield app and the existing navigation setup. This is an
update to the separate module. Android remains connected to Tripper Dash; the
iPhone provides calls and media. LightBlue can still do the one-time BLE pairing.

## Setup on the tested rooted Android 15 phone

1. Install the module update and enable its Vector scopes for **Royal Enfield**
   and **Bluetooth** (`com.android.bluetooth`). Restart Royal Enfield after
   finishing any current navigation session.
2. Open **Tripper iPhone Bridge**, start the bridge, and connect the iPhone via
   LightBlue if needed. Allow system-notification sharing and close LightBlue
   after pairing. Start iPhone media. **Refresh iPhone call/music services**
   retries discovery if services were unavailable during reconnection.
3. The LightBlue BLE bond may not include Classic Bluetooth. If the iPhone is
   missing from the call picker, tap **Pair iPhone for calls**, allow Android
   discoverability, then open iPhone **Settings → Bluetooth**, select this
   Android phone and approve pairing on both devices. HFP requires a Classic or
   dual-mode peer; BLE-only peers are excluded from the call picker.
4. Tap **Connect iPhone calls (KernelSU root)**, choose the iPhone, and grant
   KernelSU access to this bridge when prompted. Do not choose the headset.
   The optional call channel uses HFP in addition to the existing BLE bridge.
   If no prompt appears and logs show `HFP_SU_NOT_VISIBLE` or `Cannot run program
   "su"`, manually enable Superuser for **Tripper iPhone Bridge**
   (`local.re.screenoff.module`) in KernelSU/KernelSU Next and retry. ADB's root
   grant does not grant root to the bridge app; some KernelSU setups hide `su`
   from apps until they are allowlisted.
5. First activation can restart Bluetooth. The bridge recreates its BLE server
   when Bluetooth returns. If the iPhone requires separate Classic Bluetooth
   pairing, accept that on both phones. Wait for **iPhone call channel connected**.
6. Test while stationary: incoming call, answer, active call, disconnect, then
   another incoming call that is declined. Check iPhone/headset audio and
   navigation after each transition, including Android screen-off behavior.

## Controls

The Android setup screen shows the currently supported call actions. The projected
call card shows caller information, incoming/connected/held/outgoing state and the
selected action. With a call card visible, the existing media **Next/Previous**
callbacks change selection; **Play/Pause** performs the selected action. Actual
Dash firmware button routing needs a physical test. No call action is sent merely
because the card appears.

ANCS actions are offered only when the iPhone supplies their flag and actual
label. The command is checked again against the current notification immediately
before transmission. Expired/stale/unlabelled/unsupported actions are rejected.
Notification removal never implies that a call was answered.

HFP supplies actual call state and a per-call UUID. Incoming calls offer Answer
and Decline; active/held/outgoing calls offer Disconnect. The root worker verifies
that the UUID/device still matches a current call before acting. It does not
invent a connected state or timer from a missing ringing notification. Outgoing
calls initiated on the iPhone can be observed; dialing arbitrary numbers or
browsing iPhone contacts from the Dash is not implemented.

## Bluetooth compatibility on this phone

The dormant HFP client initially crashed because its `HeadsetClientService.start`
registered a legacy receiver without Android 15's required export flag. The
module supplies `RECEIVER_NOT_EXPORTED` only for that specific registration while
the HFP start method runs. It also skips starting the HFP Android Telecom UI
service during that method, so the bridge owns the call interface. Other
Bluetooth receivers/services are not intercepted. The compatibility hook is
limited to Android API 35 and the Bluetooth process. No Bluetooth APK is replaced
or resigned, and the global compatibility override remains unchanged.

The root helper enables `bluetooth.profile.hfp.hf.enabled` for the current boot
and requests the HFP client proxy. A startup failure restores the previous
property and restarts Bluetooth. This is not a universal Android implementation;
other ROMs may omit or break the HFP client. The tested phone's HFP profile and
root proxy were confirmed working after the targeted registration fix.

Audio routing to Android is disabled after connecting the selected HFP peer.
That must still be checked with the actual iPhone/headset: establishing another
HFP connection can affect the iPhone's selected audio route. A bounded CPU wake
lock is held only while an HFP call is observed, and released when calls clear.
Bluetooth call broadcasts request immediate snapshots while the screen is off.

## Saved diagnostics

Use **Export bridge logs** without USB. The private rotating log includes call
action support, button receipt, rejected/forwarded commands, HFP initialization,
connection failures, state transitions, worker exit codes, and readiness/power
heartbeats. Caller numbers, names and HFP payloads are not written by the normal
status logger. Exception traces and Bluetooth system logs can contain technical
device information.

After reconnecting USB, run `python3 patches/export_iphone_bridge_logs.py`. It also
collects RE navigation logs, `REHfpCompatibility` messages and Bluetooth process
exit reasons. A successful write/SDK request is logged as forwarded, not proof
that the iPhone performed it; the subsequent HFP state is the confirmation.

## Rollback

**Stop iPhone call channel** disconnects the selected HFP peer and leaves the BLE
media/notification bridge running. Disabling only the Bluetooth scope removes the
receiver fix: first stop HFP and disable its profile (or reboot, since the property
is not persistent), otherwise the dormant ROM bug can recur. Do not remove the
Bluetooth compatibility hook while the HFP profile remains enabled. Disabling
the entire module also removes the screen-off navigation fix.

Keep your own previously working module APK/signing key for rollback. Private
device backups are not distributed. Downgrading may require the rooted package
manager; it does not require replacing the official Royal Enfield APK.

Build: `python3 runtime-module/build.py`. Protocol/action guard tests:
`python3 runtime-module/tests/run.py`. Isolated device tests cover overlay frame
preservation and incoming/active/held/ended call-state transitions without calling
any real phone number. A real iPhone Answer → active → Disconnect sequence was observed. Decline and
physical Dash button behavior remain pending. Headset call/music-button failure
is an unresolved report; see [TESTING.md](../docs/TESTING.md).
