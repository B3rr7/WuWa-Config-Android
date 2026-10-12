# Security Policy

WuWaConfig is privacy-first and collects **no telemetry** — no Firebase,
Crashlytics or Sentry. The only network egress is to Kuro's official APIs
(user-initiated fetches) and the GitHub release check.

## Supported versions

Only the latest release on [GitHub Releases](https://github.com/B3rr7/WuWa-Config-Android/releases)
is actively maintained. Please update before reporting — the issue may already
be fixed. Older APKs are not patched.

## Reporting a vulnerability

To report a vulnerability, open a **private
[GitHub Security Advisory](https://github.com/B3rr7/WuWa-Config-Android/security/advisories/new)**.
Do not open a public issue, and do not include sensitive data in the report —
no logs, ADB keys, or player identifiers.

We aim to acknowledge within 48 hours and to ship a fix in the next release.

## Security posture

- **Local-only network.** `network_security_config.xml` permits cleartext only to
  `127.0.0.1` / `localhost`; everything else is HTTPS. Gacha history is
  broadcast in-process via `LocalBroadcastManager`, never to the network.
- **Keys encrypted at rest.** ADB key material is written through the platform
  `AndroidKeyStore` (AES-256-GCM), not plaintext.
- **No Android backup.** `allowBackup="false"`, so nothing is extractable
  through `adb backup`.
- **Permission-gated providers.** `ShizukuProvider` is locked behind
  `INTERACT_ACROSS_USERS_FULL` (a Shizuku requirement) and the AppFunctions
  service behind `BIND_APP_FUNCTION_SERVICE`, so no arbitrary installed app can
  reach the live binders.
- **Hardened release build.** R8 with resource shrinking; `Log.d` / `Log.v` are
  stripped and the keep rules are narrowed to what reflection actually needs.
- **Least-privilege agent surface.** The 13 AppFunctions are read-only or
  in-memory; device-mutating operations need a live backend session and are
  deliberately not exposed. The account identifier in battle stats and the gacha
  bearer token are withheld from the agent DTOs.
- **Sensitive clipboard.** Copies made by the app are flagged
  `EXTRA_IS_SENSITIVE`.
- **No network image fetching.** Coil ships without a network fetcher, so only
  `content://` URIs the user picked can be loaded.
