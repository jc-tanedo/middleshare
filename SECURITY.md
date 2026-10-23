# Security

## Reporting a vulnerability

Use [GitHub private vulnerability reporting](https://github.com/jc-tanedo/middleshare/security/advisories/new) when it is enabled. If that option is unavailable, open an issue asking the maintainer for a private reporting route, without publishing sensitive details or credentials.

Include the app version, Android version, affected flavor (main or video add-on), and steps to reproduce. Ordinary bugs can be reported in Issues.

## Security boundaries

- The main app has no internet permission or bundled video runtime.
- Incoming files use `content://` grants; generated files are exposed through a non-exported, read-only provider with per-file URI grants.
- The video add-on is checked for its package, minimum version, enabled state, and matching signing certificate.
- Video download confirmation is enforced by a non-exported activity; intent extras cannot prove consent.
- Signing keys belong in GitHub Actions secrets or private local storage, never in Git. The release workflow verifies APK signing certificates and removes the temporary key.

See the [2026-10-23 repository audit](docs/AUDIT-2026-10-23.md) for findings, fixes, and scope. An audit is a point-in-time review, not a guarantee that every vulnerability has been found.
