# Architecture

## Navigation hooks

`RuntimeHooks` attaches to the official RE process and checks version code 193.
While its map encoder is active, the device is Tripper Dash and the display is
noninteractive, selected lifecycle stops are retained. Fragment base callback
requirements and paired SDK lifecycle transitions prevent the recursion/duplicate
start problems encountered in earlier experiments. The known navigation wake
request becomes a CPU-only lock; screen-on Home/Back behavior stays normal.

`Diagnostics` records setup/encoder errors, callback counters and power state.
It does not record map screenshots or route coordinates. A repeated frame is not
proof that map rendering continues to progress.

## BLE bridge

`BridgeService` is a connected-device foreground service. Android advertises a
pairing characteristic and ANCS solicitation; once iPhone initiates the BLE link,
Android discovers/subscribes to ANCS and AMS. Serialized GATT operations and
fragmented attribute parsing handle protocol transport. The public setup activity
controls pairing and service state.

`AppleProtocol` encodes/parses ANCS. `CallActionGate` rejects stale, unsupported,
unlabelled or expired actions. Notification disappearance does not imply an
answered call. `ArtworkLookup` optionally matches title/artist against Apple’s
catalog and bounds image download/decoding.

## RE integration

`BridgeProvider` exposes state/actions/artwork only to the module UID and the
original signed official RE package. Caller package identity and certificate are
checked; provider export is not open access. `IPhoneDashHooks` polls it off the
main thread, forwards native media/call messages and renders an optional overlay
on a copied navigation bitmap. Overlay failures do not deliberately disable the
independent navigation fix. Call cards take priority over media cards.

With a call card, supported hooked Next/Previous callbacks select an action and
Play/Pause invokes it. Actual hardware firmware mappings require a physical test.
An active call switches the action to Disconnect; an old call UUID cannot control
a newer call.

## Root HFP channel

`HfpController` starts `HfpRootWorker` via the bridge APK’s own code using
`app_process` and authorized `su`. The transport is private process stdin/stdout
JSON, not a network server. The worker checks UID 0, initializes Android service
access and acquires the HFP client proxy. It controls only the explicitly selected
peer. Current calls carry SDK-provided UUIDs and states; action commands recheck
the actual device/UUID/state before execution. Bounded CPU wake locks are used
while calls exist. Stopping the worker disconnects its selected HFP link, not an
arbitrary iPhone call or bond.

`HfpCompatibilityHooks` operates only in the API-35 Bluetooth main process. It
changes the known legacy receiver registration only while HFP service startup
runs, and suppresses its Telecom UI service start so the bridge owns its call UI.
It does not replace the Bluetooth APK or enable a global compatibility override.
The root worker enables a nonpersistent HFP client property for the current boot;
Bluetooth scope must remain active while that profile needs the compatibility fix.

Audio routing to Android is disabled. This is not evidence that iPhone/headset
control ownership is unaffected; the headset-button report remains unresolved.

## Investigation artifacts

The historical `patches/` re-packaging scripts document an abandoned approach.
The official application is not compiled, modified or redistributed by the current
build. Private APKTool/JADX output and system Bluetooth sources are excluded.
REA is an optional pinned external investigation tool, not a runtime dependency.
