# Validation and known issues

Status recorded 15 October 2026. This is a small, version-pinned hardware test,
not certification across phones or firmware.

## Observed on the owner’s setup

OnePlus 13, Android 15/API 35, KernelSU/KernelSU Next, Vector; original Royal
Enfield version code 193; iPhone 15 Plus; Tripper Dash. The iOS version was
owner-reported and is not independently certified. No personal device identifiers
or raw phone logs are distributed.

- The owner reported screen-off navigation working after the corrected lifecycle
  hooks and later reported the iPhone media bridge working.
- ANCS/AMS pairing, subscriptions, metadata, reconnection and official RE private
  provider access were observed. Unrelated shell provider access was denied.
- The dormant HFP client initially crashed on a missing receiver export flag.
  A narrowly scoped API-35 runtime fix allowed the profile and root proxy to start.
- The companion’s own KernelSU grant was required; ADB root alone did not expose
  `su` to its process. App restart applied the grant.
- A saved call peer incorrectly pointed to earbuds. Selecting the iPhone produced
  a real HFP connection; a missing cached HFP UUID alone did not prove incompatibility.
- At 19:42 IST, a real incoming call was observed, Answer was forwarded, iPhone
  state changed to active, Disconnect was forwarded and the call list cleared.
  Command submission alone was not treated as success.

## Automated checks

- JVM: ANCS little-endian/unsigned UID encoding, both actions, fragmented replies
  across boundaries, UTF-8, oversized/malformed/duplicate attributes, label bounds,
  stale/expired/unsupported action rejection and action-selection normalization.
- On-device isolated instrumentation: bitmap preservation, bounds, Unicode,
  artwork rendering, call-card priority, immutable input and dimension guards.
- On-device isolated state tests: incoming/active/held/ended state, caller
  isolation, contextual actions and stale UUID/invalid answer rejection.
- Local APKTool contract checks against the owner-supplied original APK: exact
  declared bridge/media/call hook signatures. Decoded sources are not distributed.

CI builds are compilation checks, not physical Dash or iPhone tests.

## Outstanding

- **Earphone controls:** the owner reported that both call and music buttons fail
  when the headset is connected to iPhone. The bridge HFP channel was stopped for
  an A/B test while BLE remained enabled. Result/root cause is pending.
- Physical Dash call-action selection and joystick routing on the latest update.
- Incoming-call decline, headset audio route, multi-call scenarios and combined
  navigation/calls with the Android screen off.
- Broad ROM/device/version support, long sessions, background reconnection,
  battery/thermal measurements and navigation rerouting under power restrictions.
- Actual cover image download/delivery for a matched catalog track.
- Optional Swift helper installation on an iPhone (unsigned build only was checked).

No video streaming, contact-photo transport, native Dash wallpaper upload or
arbitrary dialing is implemented. A projected navigation overlay is not native
firmware customization. No device or feature is marked passed just because an
API request was accepted.
