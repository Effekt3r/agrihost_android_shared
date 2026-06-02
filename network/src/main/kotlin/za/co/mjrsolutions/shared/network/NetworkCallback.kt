package za.co.mjrsolutions.shared.network

/** Java-friendly single-method callback for asynchronous reads. */
fun interface NetworkCallback<T> {
    fun onResult(result: NetworkResult<T>)
}
