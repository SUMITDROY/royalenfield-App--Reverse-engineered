# Privacy and diagnostics

The bridge processes iPhone caller/notification titles and media metadata in
memory to display them on Android/Dash. ANCS permission can expose notifications;
only pair phones you control. Normal bridge status entries omit caller names,
numbers, track titles and message text. Status/exception logs are local private
app files and rotate around 1 MiB per log. Navigation logs rotate around 2 MiB
and omit route coordinates/map images. No analytics or hosted bridge server is
part of this project.

Optional cover lookup sends track title/artist to Apple’s iTunes catalog and
fetches artwork. It is controlled by a visible opt-in setting. Bluetooth services
and the RE/Dash connection operate independently of that lookup.

The exported provider requires the bridge UID or the original RE package with
the expected signing certificate. This is an access boundary, not a substitute
for trusting installed root modules/frameworks. The HFP helper communicates only
through its parent’s private pipes and controls the selected peer/call UUID.

Android/vendor/RE SDK logs, exceptions, ADB exports, device preflight reports and
process-exit records can still contain names, numbers, serials, addresses, app
state or network information. Inspect and redact all exports before publication.
The export tools do not upload them. Exported files stay where you choose until
you delete them. Uninstalling/clearing the companion deletes its private logs;
keep any needed local diagnostic copy first. A sudden OS kill may not provide an
exact final failure reason.

Private logs, account data, APK backups, signing secrets, decompiled vendor code
and original firmware are excluded from the public repository. The optional REA
tool has its own configuration and policies; no account configuration is shipped.
