# agrihost_android_shared

Shared Android Kotlin libraries used across Agrihost mobile apps (`agrihost_hael_android`, `agrihost_mr`, `agrihost_vrugte`).

## Modules

- `fcm-inbox` — FCM token registration, admin inbox UI, message detail viewer.

## Integration (per consumer app)

```gradle
// settings.gradle
includeBuild('../agrihost_android_shared')

// app/build.gradle
implementation 'za.co.mjrsolutions.shared:fcm-inbox'
```
