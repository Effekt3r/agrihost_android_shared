package za.co.mjrsolutions.shared.network.outbox

import za.co.mjrsolutions.shared.network.HttpMethod

/**
 * What a call site hands to AgriNetwork.enqueue(...). Translated into an OutboxEntry on insert.
 *
 * @param dependsOnLocalId outbox id of a row that must reach DONE before this one is sent
 *        (e.g. a photo upload depends on its parent report's create).
 */
data class OutboxOperation(
    val opType: String,
    val method: HttpMethod,
    val path: String,
    val tenant: String,
    val payloadJson: String? = null,
    val payloadFileRef: String? = null,
    val dependsOnLocalId: String? = null
)
