# :connectivity — live server reachability, with the reason

Answers *why* the server cannot be reached, not just whether it can, so a banner or a
blocked-action dialog names the actual problem instead of always blaming the bediener.

| State | Meaning | User can fix on device |
|---|---|---|
| `NO_TRANSPORT` | wifi and mobile data both off | yes → wifi settings |
| `NO_INTERNET` | attached, but no route to the internet / captive portal | yes → wifi settings |
| `SERVER_UNREACHABLE` | internet works, the base URL does not answer | no |
| `ONLINE` | the server answered | — |
| `UNKNOWN` | no probe resolved yet; never counts as offline | — |

The probe is a ladder: two zero-socket OS checks, then a `HEAD` on the base URL, then a
neutral `generate_204` check (cached for 2 minutes) to tell "server down" from "my data is
out of bundle".

## Usage (one watcher per screen)

```java
ConnectivityWatcher watcher = new ConnectivityWatcher(
        this, () -> HttpRequestHandler.getBaseUrl(),
        (previous, current) -> applyConnectivityState(previous, current));

onResume  -> watcher.start();      // probe now, then every 30s, and on every network change
onPause   -> watcher.stop();
onDestroy -> watcher.shutdown();

if (watcher.isOffline()) { /* block the server-bound action, show current.getPhrase() */ }
```

Phrases: `ConnectivityState.getPhrase()` returns the `CommonPhrase` key for the reason
(`GEEN_NETWERK_VERBINDING`, `GEEN_INTERNET_TOEGANG`, `GEEN_VERBINDING_MET_BEDIENER`);
`VERBINDING_HERSTEL` is the restored message and `GEEN_BEDIENER_VERBINDING` the dialog
title. **Every app dictionary (af/en/en-us.json) must carry these keys and bump its
LANGUAGE_VERSION** — the module declares the keys, the app owns the strings.

## Why not `:network`

`:network` drags Room and WorkManager (which auto-initialises at process start) into every
consumer. Reachability needs neither.

## Origin

Extracted from `agrihost_hael_android` (`utils/ConnectivityProbe.java` + the MainActivity
banner controller, 9.0.106 / 9.0.121). Consumers: hael (2027Season). vrugte / mr not yet
wired — vrugte still gates on the deprecated `NetworkInfo.isConnected()`.
