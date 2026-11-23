# Guerrilla 450 hardware investigation — 23 November 2026

Native instrument customization and live ECU telemetry are not implemented.
The app wallpaper/manual odometer features must not be described as native Dash
features or bike-sourced readings.

A stationary owner-authorized session on the connected Guerrilla 450 established
actual Dash-to-phone UDP traffic. A bounded passive control capture contained 229
UDP packets, 194 incoming, with 26 recurring `0x0C` subtype/length combinations.
Most payloads were zero; a few were nonzero. These are **unmapped protocol fields**,
not validated fuel, RPM, ABS, engine temperature, speed or odometer measurements.
The official version-195 parser explicitly handles only some of these fields.
Names on unrelated cloud/vehicle DTOs are not evidence of live Guerrilla telemetry.
No field is exposed as a physical bike measurement until its unit, scale, validity,
stale handling and actual instrument correspondence are verified.

An external reference at cotton1234/tripper, commit
`a33d83085d3585d20473823ed5d5c88f74cd0d83`, describes idle H.264 image projection.
This is distinct from persistent native wallpaper/firmware customization. Its
comments about unmapped telemetry are leads, not authoritative field mappings.
No reference-project source was incorporated into the released module.

A local 30-second prototype attempted idle projection through the existing official
session/encoder. An encoder-start event was observed, but the owner reported **no
visible change**. The polling worker subsequently stalled during encoder shutdown.
The attempt was stopped by restarting RE; all prototype hooks, provider controls,
test buttons and encoder code were removed from the installed build. Local version
code 11 (`0.7.1-cockpit`) restores the known app-only implementation with a higher
version code so it updates without deleting settings. No firmware was flashed and
no ECU/odometer write command was used. Private packet captures, images and logs
remain excluded from Git.

Next evidence needed: actual numeric instrument values (or an owner-supplied clear
Dash photo), firmware version and labelled stationary captures with known controlled
state changes. A list of available icons does not establish numeric mappings.
Any additional read-only investigation should preserve the official app session;
projection changes need a bounded test and physical confirmation, not just an
encoder/API success result. Engine/ABS/fuel warnings must never be synthesized
from unverified bytes or replaced with phone/manual estimates.
