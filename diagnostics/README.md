# :diagnostics — main-thread stall watchdog

Reports main-thread stalls (the lock-ups Android would log as an ANR) to logcat and to
Crashlytics as a non-fatal whose stack is the **main thread's** stack, so the report names
the blocking call. Android keeps real ANR traces in `/data/anr`, unreadable without root,
which is why the field fleet's lock-ups were invisible before this.

## Init

First thing in `Application.onCreate`, before any other initialisation:

```kotlin
MainThreadWatchdog.start()                       // 15s threshold, 1s heartbeat, 20 reports/process
MainThreadWatchdog.start(WatchdogConfig(stallThresholdMs = 8_000L))
```

`start()` is idempotent. The watchdog is a daemon thread at minimum priority; it never keeps
the process alive.

## Reading a report

- logcat: tag `MainThreadWatchdog`, `E` line `main thread blocked for >=Nms` followed by the
  main thread frames, then a `W` line `main thread recovered after ~Nms` once it unblocks.
- Crashlytics: non-fatal `Throwable: Main thread blocked >=Nms`, grouped by the top frame.

Reports are suppressed while a debugger is attached (a breakpoint pause is indistinguishable
from a stall), capped per process, and issued once per stall.

## Origin

Extracted from `agrihost_hael_android` `utils/MainThreadWatchdog.java` (9.0.61, Aug 2026).
Consumers: hael (2027Season). vrugte / mr not yet wired.
