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
