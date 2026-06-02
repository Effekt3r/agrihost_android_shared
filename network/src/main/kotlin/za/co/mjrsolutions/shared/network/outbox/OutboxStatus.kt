package za.co.mjrsolutions.shared.network.outbox

/** Lifecycle of an outbox row. Stored in Room as its [name] String (no TypeConverter needed). */
enum class OutboxStatus { PENDING, IN_FLIGHT, DONE, FAILED }
