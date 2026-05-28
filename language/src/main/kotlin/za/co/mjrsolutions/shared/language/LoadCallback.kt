package za.co.mjrsolutions.shared.language

interface LoadCallback {
    fun onSuccess()
    fun onFailure(message: String)
}
