# :debug-tools — developer conveniences for DEBUG builds only

Currently: a **"Clear app data"** entry in the launcher long-press menu that, after a
confirmation, wipes the app exactly like Settings → Storage → Clear storage (database,
every preferences file, tokens, cached dictionaries, caches) and kills the process, so the
next launch starts from a clean install without reinstalling.

## Wiring

One line, and it must be `debugImplementation`:

```groovy
debugImplementation 'za.co.mjrsolutions.shared:debug-tools'
```

Nothing else. The module's manifest registers a startup ContentProvider (authority
`${applicationId}.debugshortcuts`, so side-by-side flavors do not collide) that installs
the dynamic shortcut, and the target activity. There is no code to call.

**Never use `implementation`.** The wipe destroys unsynced assessments, which the server
cannot re-issue (it allocates reports to a device on download). Scoping the dependency to
the debug variant is what keeps the shortcut out of every release APK; a runtime
`BuildConfig.DEBUG` check would still ship it.

## Origin

Extracted from `agrihost_hael_android` `app/src/debug/` (9.0.118–9.0.119, Sep 2026).
Consumers: hael (2027Season). vrugte / mr not yet wired.
