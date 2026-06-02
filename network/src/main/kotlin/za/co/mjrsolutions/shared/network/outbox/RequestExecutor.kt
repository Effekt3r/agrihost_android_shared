package za.co.mjrsolutions.shared.network.outbox

import za.co.mjrsolutions.shared.network.AgriRequest
import za.co.mjrsolutions.shared.network.NetworkResult

/** Indirection over AgriNetwork.execute so the flusher is unit-testable with a fake. */
fun interface RequestExecutor {
    fun execute(request: AgriRequest): NetworkResult<String>
}
