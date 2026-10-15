# Troubleshooting

## Account details fail

Use the original signed Royal Enfield app. Earlier re-signed experiments were
rejected by certificate-bound backend configuration. The separate module is the
current approach. Do not distribute or install the old experimental RE APKs.

## Hooks do not load / screen-off navigation stops

Check the official app version code is 193 and the module is enabled/scoped in
LSPosed/Vector. Restart the official app after changing scopes or updating the
module. `RUNTIME_HOOKS_READY_VERSION_193` confirms setup, while
`UNSUPPORTED_VERSION`, `HOOK_SETUP_FAILED` or `RUNTIME_HOOKS_DISABLED` require
investigation. Collect persistent logs plus process exit reasons. Check actual
Dash map progress, not only connectivity or packet/callback counts.

## Root unavailable despite ADB root

Grant Superuser to **Tripper iPhone Bridge** (`local.re.screenoff.module`) in
KernelSU/KernelSU Next. Restart the bridge to refresh its namespace/grant.
`HFP_SU_NOT_VISIBLE_ENABLE_KERNELSU_FOR_BRIDGE_APP` identifies hidden/unavailable
`su`; `HFP_ROOT_START_FAILED` and worker exit events record other failures.
Do not expose a root shell to arbitrary apps or change global protections to fix
one companion’s missing grant.

## “Calls ready” but no active-call state

ANCS notification readiness and HFP readiness are separate. Full call state
requires `hfp_ready=true`. Check **Connect iPhone calls** selected the iPhone,
not a headset or speaker. An initially unnamed device may be the iPhone; confirm
its identity rather than guessing. Cached audio-gateway UUID absence is not
conclusive; the tested iPhone connected despite an initially missing cache entry.
Check iPhone Settings → Bluetooth and accept Classic pairing if required.

## Bluetooth crashes after enabling HFP

The tested ROM’s dormant HFP client registered a receiver without API-35 export
flags. Bluetooth scope and a process restart are needed to load the targeted fix
before activating the profile. See [setup rollback](SETUP.md#stop-and-rollback).
Other ROM bugs are not automatically fixed by that hook. Avoid repeated activation
loops; preserve system exit/crash evidence and return HFP to its disabled state.

## Earphone buttons stop working

This report is **open**, with no confirmed cause. Earphones connect directly to
iPhone; Android does not receive their raw button events. First use **Stop iPhone
call channel** and retry the same music and call buttons while BLE remains running.
Record whether both, only one or neither recover. Then compare a fully stopped
bridge if needed. Keep volume/audio route, media app and pairing unchanged during
each comparison. Stopping HFP loses its ongoing-call/Disconnect functionality;
notification-advertised incoming actions may remain. Do not claim this tradeoff
is solved before repeating the combined test.

## Metadata or cover art missing

`AMS_READ_ATTRIBUTE_*_FAILED_STATUS_162` was observed for some long-attribute reads.
It is a GATT operation failure, not proof the entire HFP channel is down. Record
which player, fields and controls work. Use **Refresh iPhone call/music services**
after reconnection. Cover lookup is opt-in and requires an exact normalized
artist/title catalog match and a validated internet path; YouTube titles may not
match. It sends a catalog query, not iPhone artwork.

## Installation signature mismatch

A local source build uses your own development key. It cannot update another
signer’s installed companion. Follow [BUILDING.md](BUILDING.md#signing), preserve
needed logs/settings and perform the documented HFP shutdown before replacing
only the companion. Do not clear or replace the official RE app to fix this.
