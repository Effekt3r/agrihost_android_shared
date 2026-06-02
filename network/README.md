# :network — shared HTTP + offline outbox

OkHttp-based network engine for the Agrihost Android apps. Provides:

- One `OkHttpClient` with a `Auth → Transport → Logging` (innermost) interceptor chain.
- `AgriNetwork.execute(AgriRequest)` for synchronous reads (call off the main thread).
- Retrofit service access via `AgriNetwork.service(MyApi.class)`.
- An offline **outbox**: `AgriNetwork.enqueue(OutboxOperation)` queues a write to a Room DB
  (`agri_outbox.db`) and a `SyncWorker` drains it (idempotency-keyed, dependency-ordered)
  whenever connectivity returns.

## Init (wired in app phases, not here)

```java
AgriNetwork.init(application, new AgriNetworkConfig(
    baseUrl,
    Transport.DIRECT,
    /* functionsBaseUrl */ null,
    () -> AgriAuth.getAuthHeaders(),
    CrashlyticsLogSink.INSTANCE,
    () -> AgriAppConfig.snapshot().getDebugLoggingEnabled(),
    () -> AgriAppConfig.snapshot().getMinSyncIntervalMinutes(),
    30000L, 60000L
));
```

## Not yet wired
- `Transport.FUNCTIONS_GATEWAY` is implemented and tested but unused (Phase H).
- No app consumes this module yet — see the design spec's Migration Order (Phases C–G).
