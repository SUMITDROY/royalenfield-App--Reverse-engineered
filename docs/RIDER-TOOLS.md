# Rider tools — experimental 0.5

Open **Tripper iPhone Bridge → Ride tools / trips / contacts / fleet / settings**.
Use the section selector to jump between tools. Everything stays local unless you
configure a fleet endpoint and explicitly enable location sharing. Recording,
contact lookup and automatic startup are off by default. Updating with the same
signer preserves the SQLite ride database and preferences; there is no destructive
schema migration in this release.

## Connections, profiles and contacts

The dashboard distinguishes fresh official-RE transport observations, stale/unknown
Dash state, iPhone ANCS/AMS readiness and the optional HFP channel. Transport/IPC
presence does not certify map delivery. Reconnect iPhone services, open the official
app, or return to the existing bridge/call controls from this screen. The iPhone’s
headset connection and battery are **not exposed** by the current protocols and
are labelled unavailable. Android battery/charging can appear in the map overlay.

Profiles: Navigation only stops the BLE bridge; Navigation + music suppresses call
cards/actions and disables HFP; Full allows notification actions and explicit HFP
connection. Changing profile never silently starts root calls. Existing installations
retain their prior full-capability profile until changed. The previously reported
iPhone earphone-button conflict remains unresolved; these profiles provide an
explicit way to disable the additional HFP link for comparison.

Caller photos require the **caller photos** toggle, Android Contacts permission,
a contact with a stored photo and an actual caller number from HFP. The lookup
uses Android PhoneLookup, not a guessed ANCS display-name match. It caches a small
photo in app-private storage and displays it on the projected call card; it cannot
change a native Dash call screen. Withheld/unknown numbers or missing photos show
no image. Privacy mode hides caller/notification labels at the provider boundary,
disables photo delivery and clears the cached image. It does not hide information
shown independently by the official app or the iPhone.

## Trip recording, history and parking

Start recording from the visible app and grant location permission. Recording uses
a separate, visible **location foreground service**; it is not started by pairing,
boot, the fleet server or the background Dash automation. A notification remains
while recording. Stop recording explicitly in Ride tools.

The GPS/network recorder accepts recent fixes with accuracy at most 75 metres,
rejects out-of-order/unrealistic jumps and filters very small movement. Approximate
location may not produce acceptable fixes; precise location is preferred. Distances
are GPS estimates, not the bike’s odometer. Duration, distance and **observed Dash
link** interruptions appear in ride history. This does not detect every renderer,
GPS or navigation-provider interruption. A process killed mid-ride leaves an active/
interrupted entry rather than falsely marking a complete route.

Select a history entry to export GPX using Android’s document picker. GPX includes
exact accepted coordinates and UTC times: share only intentionally. Find parked
bike opens the most recent recorded location saved on Dash-link loss or recording
stop, provided the accepted fix is no older than two minutes. It is a **last known
phone location**, not a live bike tracker, and needs an active recorded ride first.

## Maintenance and fuel

Enter maintenance tasks with due date and odometer. Enter current odometer to see
due items; selecting an item marks it complete. Due reminders are checked when
Ride tools opens and can post a notification once daily. There is no always-running
maintenance alarm or automatic odometer access.

Log refuels in increasing odometer order and indicate full fills. Mileage uses the
last two full fills and intervening added litres; the fuel added at the earlier
full fill is not counted. Range multiplies that measured mileage by a manually
entered remaining quantity. It is an estimate, not tank sensor data or a guarantee.

## Overlay and preparation

Choose charcoal/navy/forest, left/right position, visibility and battery information.
The existing media/call fields depend on the selected riding profile. Native
speedometer/clock wallpaper and firmware are unchanged. Offline preparation is a
manual checklist plus compatibility/location/permission checks: the official
provider does not expose downloaded-map inventory to this companion. Check maps
and destination inside that provider before leaving.

## Fleet, groups and privacy

A real optional server is included in [`fleet-server/`](../fleet-server/README.md).
Deploy it behind trusted HTTPS before configuring the Android client. No hosted
service is provided or silently provisioned. Configure the HTTPS base URL and the
**rider** token supplied by your manager; never install a manager token on a rider’s
phone. Explicitly enable sharing after reading the consent dialog. Subsequent
accepted recording fixes are sent at most once per 15 seconds. No old points are
backfilled and there is no offline upload queue. Stop sharing immediately using
its button; any already in-flight request can complete. Previously received server
data remains until retention expiry or explicit remote deletion.

The manager can view recent reports/trip-point history, assign destination/route
notes and group meeting plans, and delete server records. Assignments are fetched
manually and cached locally; selecting one opens a map destination, not an imported
multi-stop route in RE. Group peers see recent shared positions only when assigned
to the same group; cached positions show timestamps and are not guaranteed live.
Local group plans can be shared through Android’s share sheet without a server.
Deleting an assignment does not itself remove group membership; remote rider-data
deletion clears membership. The server defaults to seven days of location retention.

The offline emergency card stores contact/medical text privately, but anyone with
access to this unlocked app can view it. Guest mode disables fleet sharing; it is
**not an isolated multi-user container**. Remove personal app data before handing
this phone to another rider. The removal action uses Android’s app-data reset to
clear local rides, settings, logs, tokens, emergency text and cached images. It
does not remove official RE data, system Bluetooth bonds or remote fleet data.
Use the separate remote-deletion action first if wanted and remove bonds in system
Bluetooth Settings. Stop HFP and respect the reboot/scope ordering before reset.

## Automation, support and recovery

Select the actual Dash bond and enable auto-start/stop. Its system Bluetooth ACL
connect event attempts to start the bridge; disconnect stops an automatically
started session after a 15-second grace period. This is Bluetooth-link automation,
not guaranteed navigation-session detection. ROM restrictions may block background
starts; open the app manually and inspect `AUTO_START_BLOCKED_OPEN_BRIDGE`. It
never auto-starts location recording.

The redacted support report deliberately omits raw logs, phone identities, routes,
caller/track text, emergency information and tokens. It includes versions, permission
states, connection booleans and strictly allowlisted failure-code tokens from recent
bridge logs, without their messages or stack traces. Existing raw-log export remains separate and may
contain personal information: review before sharing.

Portable settings backup includes only profile, privacy and overlay/artwork choices.
It excludes rides, contacts, emergency data, credentials, device addresses, automatic
startup and sharing/root consent. Export rides separately as GPX. Restore validates
schema and turns sharing/root startup off; it cannot silently enable them. Keep your
previous working same-signer APK locally for rollback. ADB/local module backup is
not published to GitHub. Before removing Bluetooth scope: stop HFP, keep its saved
startup switch off and reboot so the nonpersistent profile property resets.

## Verification and remaining hardware tests

On 23 November 2026: version-195 hook contracts checked, module installed as an
update, navigation and iPhone hooks loaded, original RE provider IPC and BLE media
reconnected. Isolated device tests passed storage checkpoints, summaries, GPX,
maintenance persistence, settings privacy, safe restore, call state and bitmap
rendering. Fleet role/group isolation, input checks, retention and deletion are
covered by tests. No live fleet location was sent from the phone during development.

A real moving ride, actual matching contact photo/call, native Dash button behavior,
automatic bike link events, server deployment, real multi-rider use and headset
coexistence still require physical testing. No universal device support is claimed.
