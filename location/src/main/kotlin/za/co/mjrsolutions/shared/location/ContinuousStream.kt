package za.co.mjrsolutions.shared.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import java.util.concurrent.CopyOnWriteArraySet

internal object ContinuousStream : DefaultLifecycleObserver {

    @Volatile private var config: LocationConfig = LocationConfig()
    @Volatile private var latest: AgriFix? = null
    @Volatile private var subscribed: Boolean = false

    private val listeners = CopyOnWriteArraySet<LocationListener>()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val now = System.currentTimeMillis()
            for (loc in result.locations) {
                val fix = FixFilter.toAgriFix(loc, config, now)
                latest = fix
                listeners.forEach { it.onLocationUpdate(fix) }
            }
        }
    }

    fun init(config: LocationConfig) {
        this.config = config
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun addListener(context: Context, listener: LocationListener) {
        listeners.add(listener)
        ensureSubscribed(context)
    }

    fun removeListener(listener: LocationListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            unsubscribe()
        }
    }

    fun getLatestFresh(): AgriFix? = latest?.takeIf { it.isFresh && it.isAccurate }

    fun getLatestAny(): AgriFix? = latest

    override fun onResume(owner: LifecycleOwner) {
        latest = null
    }

    @SuppressLint("MissingPermission")
    private fun ensureSubscribed(context: Context) {
        if (subscribed) return
        FusedClient.init(context)
        FusedClient.get().requestLocationUpdates(
            FusedClient.continuousRequest(config),
            callback,
            Looper.getMainLooper()
        )
        subscribed = true
    }

    private fun unsubscribe() {
        if (!subscribed) return
        try {
            FusedClient.get().removeLocationUpdates(callback)
        } catch (_: Exception) {
        }
        subscribed = false
    }
}
