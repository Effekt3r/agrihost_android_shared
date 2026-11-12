# audit

Shared Firestore audit log for the assessor apps. One call per server interaction writes:
1. `GetFromServer/{client}/{user}/{success|failure}/entries/{ts}` or
   `SyncToServer/{client}/{user}/{reportNumber}/entries/{ts}` (full payload, frozen tree shape),
2. a `messages` doc (read by the Flutter client — field names frozen),
3. an FCM v1 push to every admin user (`users` where `isAdmin`), using the service-account
   key in `services/AccountKey`. The push is gated on the `messages` write committing, so an
   offline sync defers its notification until connectivity returns. Stale tokens (404/410)
   are cleared.

Init from `Application.onCreate`; see `AgriAudit` kdoc. Client ids are frozen per app:
hael=FLAVOR, vrugte=FLAVOR+"_vrugte", Brand=FLAVOR, mr=FLAVOR+"_mr".
Spec: Agrihost/docs/superpowers/specs/2026-06-10-shared-audit-log-design.md

1.3.0 (2026-11-12) - one console writer for every app (hael is the reference):
- `logPhoto` / `logPhotoUpload` / `logPdf`: hael's photo, photo_upload and pdf entries under
  `SyncToServer/{client}/{full name}/{report}/entries/{photo_<id>|<ms>|pdf_<file>}`; no messages doc, no push.
- `AuditPayloads.forLog`: every logged body has base64 photos (100,000+ chars) replaced by
  `[photo, N KB, not kept in this log]` and is cut at 900,000 chars, so no entry is lost to the 1 MiB limit.
- `AgriUserRecord.recordLogin`: users/{login id} + users/{login id}/bundle/{applicationId} merge (Users tab).
- `CalculationAudit` + `CalcScope`: calculation_audit_logs documents (hael's fields).
- `AuditConfig` gained `loginIdProvider`, `appVersion`, `applicationId`; `userNameProvider` is the full name.
