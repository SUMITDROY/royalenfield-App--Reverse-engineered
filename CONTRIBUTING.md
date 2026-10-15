# Contributing

Start with the README’s supported version and open issues. Keep changes focused
and describe the problem, final behavior, tests and hardware limitations in a PR.
Do not relax the RE version gate without checking the new APK’s method contracts
and actual lifecycle behavior. Do not remove IPC certificate checks or stale-call
UUID validation to make a test pass.

Run `python3 runtime-module/tests/run.py` and `python3 runtime-module/build.py`.
For call state/overlay changes, also run the isolated device tests documented in
`docs/BUILDING.md`. Changes to hardware behavior need a stationary real-device
check; state observations separately from inferred success. Do not send real calls
or accept/terminate calls automatically in tests.

Report Android model/API/ROM, official RE version code, module commit/version,
framework/root versions, iPhone/media app/headset models, steps and expected vs
observed behavior. Redact logs first. Say whether navigation was connected to the
Dash and whether the screen was locked. No passwords, private keys, account data,
proprietary APKs, decoded sources or unredacted logs in issues/PRs.

REA is a pinned submodule; upstream changes belong in its own project. This repo
uses MIT for project-authored contributions. By submitting a contribution you
agree it may be distributed under that license.
