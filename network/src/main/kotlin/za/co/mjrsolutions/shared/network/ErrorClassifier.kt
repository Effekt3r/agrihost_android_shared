package za.co.mjrsolutions.shared.network

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

/** Single place that turns HTTP outcomes into a [NetworkResult]. */
internal object ErrorClassifier {

    fun fromException(e: IOException): NetworkResult<Nothing> = when (e) {
        is UnknownHostException, is ConnectException -> NetworkResult.Offline
        else -> NetworkResult.NetworkError(e)
    }

    fun fromResponse(code: Int, body: String?): NetworkResult<Nothing> =
        NetworkResult.ServerError(code, body)
}
