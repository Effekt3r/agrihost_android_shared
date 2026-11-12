package za.co.mjrsolutions.shared.app_config

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.Calendar

/**
 * The red update banner on an app's main screen (the included `agri_app_update_banner` layout).
 *
 * What it shows:
 * - REQUIRED: the app is below the console's minVersion and Force is ticked. No X.
 * - SUGGESTED: below a minVersion without Force, or Google Play has a newer version (asked on every
 *   resume, so no minVersion is needed to suggest an update). The X hides it for 24 hours.
 * - The version gap "(vX → vY)" follows the text whenever the console's minVersion is ahead.
 *
 * When: only from 18:00 up to 06:00 (tablet time) or on unmetered Wi-Fi (a phone hotspot is
 * metered, so it does not count). It comes and goes by itself at 06:00 and 18:00 and when the
 * network changes while the screen is open. The console's forced pop-up is the app's own and shows
 * at any time.
 *
 * The tap: Google Play's update screen inside the app (AppUpdateFlow). The app's update pop-ups
 * call [startUpdate] for their Update button.
 *
 * Create it in the screen's onCreate, after setContentView. It follows the screen's lifecycle by
 * itself. Call [refresh] after the app's language changes.
 */
class AppUpdateBanner internal constructor(
    private val activity: ComponentActivity,
    private val root: View,
    private val texts: Texts,
    private val config: () -> AppConfigSnapshot?,
    private val play: PlayUpdateCheck,
    private val flow: AppUpdateFlow,
    private val now: () -> Calendar,
    private val onUnmeteredWifi: () -> Boolean,
    private val onShown: OnShownListener?,
) : DefaultLifecycleObserver {

    /** The banner's words in the app's own dictionary. */
    fun interface Texts {
        /** The text for [kind]: [UpdateKind.REQUIRED] or [UpdateKind.SUGGESTED]. */
        fun text(kind: UpdateKind): String
    }

    /**
     * Told each time the banner comes into view (and when what it is about changes while in view),
     * so the app's optional pop-up follows the same hours and Wi-Fi rule as the banner.
     */
    fun interface OnShownListener {
        /**
         * @param kind [UpdateKind.REQUIRED] or [UpdateKind.SUGGESTED].
         * @param fromMinVersion the console's minVersion is ahead of this version; false when only
         *   Google Play has a newer version.
         */
        fun onShown(kind: UpdateKind, fromMinVersion: Boolean)
    }

    /**
     * @param root the included `agri_app_update_banner` layout (`R.id.agri_update_banner`).
     * @param onShown told when the banner comes into view; it may already be called from here.
     */
    @JvmOverloads
    constructor(activity: ComponentActivity, root: View, texts: Texts, onShown: OnShownListener? = null) : this(
        activity = activity,
        root = root,
        texts = texts,
        config = { AgriAppConfig.getSnapshot() },
        play = AgriAppUpdate.play,
        flow = AppUpdateFlow(
            activity = activity,
            play = AgriAppUpdate.play,
            managerFor = AgriAppUpdate::managerFor,
            online = { NetworkState.isOnline(activity) },
            elapsed = SystemClock::elapsedRealtime,
        ),
        now = { Calendar.getInstance() },
        onUnmeteredWifi = { NetworkState.isOnUnmeteredWifi(activity) },
        onShown = onShown,
    )

    private val text: TextView = root.findViewById(R.id.agri_update_banner_text)
    private val close: View = root.findViewById(R.id.agri_update_banner_close)
    private val prefs = activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val redraw = Runnable { refresh() }

    /** At 06:00 and 18:00 the hours rule changes by itself: redraw, then wait for the next edge. */
    private val edgeTick = Runnable {
        refresh()
        scheduleEdge()
    }

    private var networkRegistered = false

    /** What the banner shows now (kind, from minVersion), or null while it is hidden. */
    private var showing: Pair<UpdateKind, Boolean>? = null

    /** The system calls it on its own thread; the redraw runs on the main thread. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post(redraw)
        }

        override fun onLost(network: Network) {
            handler.post(redraw)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            handler.post(redraw)
        }
    }

    init {
        root.setOnClickListener { startUpdate() }
        close.setOnClickListener { dismiss() }
        activity.lifecycle.addObserver(this)
        refresh()
    }

    /**
     * Reads the console's config, Play's answer, the time and the network again and redraws. It runs
     * by itself on resume and on every change it can see; the app calls it after a language change.
     */
    fun refresh() {
        try {
            render()
        } catch (e: Exception) {
            AgriAppUpdate.reportFailure("Banner", e)
        }
    }

    /**
     * Google Play's update screen inside the app: the banner's tap, and the Update button on the
     * app's own update pop-ups (which show whether the banner does or not).
     */
    fun startUpdate() {
        flow.start(belowMinVersion = config()?.updateRequired == true)
    }

    override fun onResume(owner: LifecycleOwner) {
        AgriAppUpdate.addListener(redraw)
        registerNetwork()
        scheduleEdge()
        flow.onResume()
        refresh()
    }

    override fun onPause(owner: LifecycleOwner) {
        AgriAppUpdate.removeListener(redraw)
        unregisterNetwork()
        handler.removeCallbacks(edgeTick)
        handler.removeCallbacks(redraw)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
    }

    private fun render() {
        val snapshot = config()
        val belowMinVersion = snapshot?.updateRequired == true
        val kind = AppUpdateRules.kind(belowMinVersion, snapshot?.forceUpdate == true, play.waiting)
        val time = now()
        val visible = AppUpdateRules.bannerVisible(
            kind = kind,
            hourOfDay = time.get(Calendar.HOUR_OF_DAY),
            onUnmeteredWifi = onUnmeteredWifi(),
            dismissedUntilMs = prefs.getLong(KEY_DISMISSED_UNTIL, 0L),
            nowWallMs = time.timeInMillis,
        )
        if (!visible) {
            root.visibility = View.GONE
            showing = null
            return
        }
        val words = texts.text(kind)
        // The version gap is known only from the console's minimum; Play gives no version name.
        val gap = snapshot?.takeIf { belowMinVersion }
        text.text = if (gap != null) "$words (v${gap.currentVersion} → v${gap.minVersion})" else words
        close.visibility = if (kind == UpdateKind.SUGGESTED) View.VISIBLE else View.GONE
        root.visibility = View.VISIBLE
        val now = kind to belowMinVersion
        if (now != showing) {
            showing = now
            onShown?.onShown(kind, belowMinVersion)
        }
    }

    /** The X: a suggested update stays hidden for 24 hours, on this screen and the next. */
    private fun dismiss() {
        prefs.edit()
            .putLong(KEY_DISMISSED_UNTIL, AppUpdateRules.dismissedUntil(now().timeInMillis))
            .apply()
        AgriAppUpdate.log("suggested update hidden for 24 hours")
        refresh()
    }

    private fun scheduleEdge() {
        handler.removeCallbacks(edgeTick)
        handler.postDelayed(edgeTick, AppUpdateRules.millisToNextWindowEdge(now()))
    }

    private fun registerNetwork() {
        if (networkRegistered) return
        try {
            val cm = connectivityManager() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(networkCallback)
            } else {
                cm.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
            }
            networkRegistered = true
        } catch (e: Exception) {
            // The banner still follows the network on every resume.
            AgriAppUpdate.reportFailure("Network watch", e)
        }
    }

    private fun unregisterNetwork() {
        if (!networkRegistered) return
        try {
            connectivityManager()?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            AgriAppUpdate.reportFailure("Network watch", e)
        } finally {
            networkRegistered = false
        }
    }

    private fun connectivityManager(): ConnectivityManager? =
        activity.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private companion object {
        const val PREFS = "agri_app_update"
        const val KEY_DISMISSED_UNTIL = "suggested_dismissed_until"
    }
}
