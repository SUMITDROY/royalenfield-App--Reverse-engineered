# Optional self-hosted fleet/group relay

This prototype accompanies the Android 0.5 rider tools. It is not a hosted fleet
service and is disabled/unconfigured on the phone by default. It provides:

- Explicit rider-token location uploads during a user-started recorded ride.
- Manager-token dashboard, reported locations and shared trip-point history.
- Per-rider route/meeting notes, optional group membership and recent group points.
- Rider self-deletion, manager deletion and configurable location retention.

Python 3.10+ stdlib only. SQLite is stored outside the source tree. All tokens are
private configuration, not sample hardcoded credentials. Generate and run:

```sh
python3 fleet-server/server.py --init
# Read .local/fleet-users.json privately. It maps random tokens to identities.
python3 fleet-server/server.py --port 8787 --retention-days 7
```

The server binds **127.0.0.1**. For a phone, put a reverse proxy with a valid trusted
TLS certificate in front of it and configure its HTTPS URL in Ride tools. For
example, with a domain already pointed to your host and Caddy separately installed:

```caddyfile
fleet.example.org {
    reverse_proxy 127.0.0.1:8787
}
```

Do not expose the Python listener directly to the internet. Set request-size and
rate limits at your reverse proxy, restrict host access and configure appropriate
service supervision/backups. Disable/redact proxy access logs for sensitive paths.
Protect `.local/` and backups: they contain bearer credentials and exact locations.
No deployment/domain/account has been provisioned by the project.

Open your HTTPS URL in a browser for the manager UI and paste the **admin** token.
It stays in page memory, not local storage or a URL. Do not paste that token into
Android: give each phone its own rider token. Add users by editing the private
config with another random token (e.g. `python3 -c 'import secrets; print(secrets.token_urlsafe(32))'`)
and a unique rider identity, then restart. Rotate tokens by replacing them. Do not
publish configuration or tokens in issues. All endpoints fail closed without the
right role; user identity comes from the token, never a client-supplied rider ID
for location writes.

Android records only after runtime location consent and an explicit Start recording.
Sharing is a separate opt-in. Operators should agree on retention/group access with
riders first. Group members receive recent points for their own group only, and
stale entries are excluded by the API after two minutes. Android can retain an
older fetched snapshot with its timestamp until refreshed. Manager history is
retention-limited shared points, not the full private GPX or a sensor-derived trip
summary. Retention cleanup occurs on authenticated API activity. Deleting remote
rider data also removes assignments and group membership, but does not revoke that
rider's token; rotate/remove the token separately if needed. Removing an assignment
alone does not remove group membership. Backups need a separate retention policy.

API:

- Rider: `POST /api/location`, `GET /api/assignments`, `POST /api/delete-my-data`.
- Manager: `GET /api/dashboard`, `POST /api/assign`, `POST /api/delete-assignment`,
  `POST /api/delete-rider-data`.
- Use `Authorization: Bearer TOKEN`. Bodies are bounded JSON objects. No wildcard
  CORS, token-in-query authentication or untrusted redirect following is used.

Run tests: `python3 -m unittest discover -s fleet-server -v`.
The server and Android transport have not been field-tested with multiple riders.
