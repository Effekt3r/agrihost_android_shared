package za.co.mjrsolutions.shared.fcm_inbox

object BrandUtil {
    private val SUFFIX = Regex("_(TEST|TRAINING|LANDSCAPE|MANUAL|OFFLINE)$", RegexOption.IGNORE_CASE)
    private val VRUGTE = Regex("_vrugte$")

    fun brandForFlavor(flavor: String): String =
        flavor
            .replace(VRUGTE, "")
            .replace(SUFFIX, "")
            .uppercase()
}
