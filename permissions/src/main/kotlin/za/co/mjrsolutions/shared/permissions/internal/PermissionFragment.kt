package za.co.mjrsolutions.shared.permissions.internal

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import za.co.mjrsolutions.shared.permissions.PermissionCallback
import za.co.mjrsolutions.shared.permissions.PermissionType

/**
 * Headless fragment that hosts the system permission request and forwards results
 * to a callback registry. Lives across config changes (setRetainInstance), so
 * rotation during a system prompt does not drop the callback.
 */
internal class PermissionFragment : Fragment() {

    private val pending = mutableMapOf<PermissionType, MutableList<PermissionCallback>>()
    private val inFlight = mutableMapOf<Int, PermissionType>()
    private var nextRequestCode = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        retainInstance = true
    }

    /** Enqueue a callback. If a request for [type] is already in flight, the callback joins the queue. */
    fun requestNow(type: PermissionType, callback: PermissionCallback) {
        val list = pending.getOrPut(type) { mutableListOf() }
        list.add(callback)
        val alreadyInFlight = inFlight.values.contains(type)
        if (alreadyInFlight) return
        val code = nextRequestCode++
        inFlight[code] = type
        requestPermissions(type.manifestPermissions.toTypedArray(), code)
    }

    @Deprecated("System callback override required by Fragment API")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val type = inFlight.remove(requestCode) ?: return
        onRequestPermissionsResultInternal(type, permissions.toList().toTypedArray(), grantResults)
    }

    /** Visible for tests — accepts the same shape onRequestPermissionsResult produces. */
    fun onRequestPermissionsResultInternal(
        type: PermissionType,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        val granted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        val callbacks = pending.remove(type) ?: return
        if (granted) {
            callbacks.forEach { it.onGranted() }
        } else {
            val permanentlyDenied = permissions
                .mapIndexedNotNull { i, p ->
                    if (i < grantResults.size && grantResults[i] != PackageManager.PERMISSION_GRANTED) p else null
                }
                .filter { perm ->
                    activity?.let { !it.shouldShowRequestPermissionRationale(perm) } ?: false
                }
            callbacks.forEach { it.onDenied(permanentlyDenied) }
        }
    }

    override fun onDestroy() {
        // Activity going away — drain pending callbacks so callers don't leak.
        pending.values.flatten().forEach { it.onDenied(emptyList()) }
        pending.clear()
        inFlight.clear()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "agri_permissions_fragment"

        fun attach(activity: FragmentActivity): PermissionFragment {
            val existing = activity.supportFragmentManager.findFragmentByTag(TAG) as? PermissionFragment
            if (existing != null) return existing
            val fragment = PermissionFragment()
            activity.supportFragmentManager
                .beginTransaction()
                .add(fragment, TAG)
                .commitNowAllowingStateLoss()
            return fragment
        }
    }
}
