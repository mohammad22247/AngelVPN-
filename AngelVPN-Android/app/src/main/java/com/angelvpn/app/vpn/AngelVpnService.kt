package com.angelvpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.angelvpn.app.R
import com.angelvpn.app.domain.ConnState
import com.angelvpn.app.domain.Engine
import com.angelvpn.app.domain.Profile
import com.angelvpn.app.domain.VpnSnapshot
import com.angelvpn.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phase 1 VpnService skeleton.
 *
 * In later phases this will:
 *  - open TUN interface
 *  - start the correct engine bridge (DNSTT / Soar / Cotten / MasterDNS)
 *  - feed packets through tun2socks → local SOCKS → tunnel
 *
 * Right now it establishes a TUN interface and reports CONNECTED so the
 * UI and lifecycle can be tested end-to-end.
 */
class AngelVpnService : VpnService() {

    companion object {
        private const val TAG = "AngelVpnService"
        private const val CH_ID = "angel_vpn"
        private const val NOTIF_ID = 42

        const val ACTION_CONNECT = "com.angelvpn.app.CONNECT"
        const val ACTION_DISCONNECT = "com.angelvpn.app.DISCONNECT"
        const val EXTRA_PROFILE_JSON = "profile_json"

        private val _snapshot = MutableStateFlow(VpnSnapshot())
        val snapshot: StateFlow<VpnSnapshot> = _snapshot.asStateFlow()

        @Volatile
        var runningInstance: AngelVpnService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    private var engineJob: Job? = null
    private var currentProfile: Profile? = null

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
        createChannel()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val json = intent.getStringExtra(EXTRA_PROFILE_JSON)
                if (json != null) {
                    val profile = try {
                        com.google.gson.Gson().fromJson(json, Profile::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "Bad profile json", e)
                        null
                    }
                    if (profile != null) startTunnel(profile)
                }
            }
            ACTION_DISCONNECT -> stopTunnel()
        }
        return START_STICKY
    }

    private fun startTunnel(profile: Profile) {
        if (_snapshot.value.state == ConnState.CONNECTED || _snapshot.value.state == ConnState.CONNECTING) {
            stopTunnelInternal()
        }
        currentProfile = profile
        updateSnapshot(
            VpnSnapshot(
                state = ConnState.CONNECTING,
                profileId = profile.id,
                profileName = profile.name,
                engine = profile.engine
            )
        )
        startForeground(NOTIF_ID, buildNotification(ConnState.CONNECTING, profile.name))

        engineJob = scope.launch {
            try {
                // ── Phase 1: open a basic TUN so the VPN permission path works ──
                val builder = Builder()
                    .setSession("AngelVPN – ${profile.name}")
                    .setMtu(1500)
                    .addAddress("10.8.0.2", 32)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .addRoute("0.0.0.0", 0)

                // Exclude ourselves so tunnel control packets can leave the device
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (_: Exception) {}

                tun = builder.establish()
                if (tun == null) {
                    updateSnapshot(
                        VpnSnapshot(
                            state = ConnState.ERROR,
                            profileId = profile.id,
                            profileName = profile.name,
                            engine = profile.engine,
                            errorMessage = "ساخت رابط TUN ناموفق بود"
                        )
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                // ── Phase 2 placeholder: engine bridge ──
                // When real engines are integrated:
                //   when (profile.engine) {
                //     Engine.DNSTT -> DnsttBridge.start(profile, tun!!)
                //     Engine.SOAR  -> SoarBridge.start(...)
                //     ...
                //   }
                Log.i(TAG, "TUN established for ${profile.engine} / ${profile.domain}")
                Log.w(TAG, "Engine bridge not yet implemented – UI-only connected state")

                updateSnapshot(
                    VpnSnapshot(
                        state = ConnState.CONNECTED,
                        profileId = profile.id,
                        profileName = profile.name,
                        engine = profile.engine,
                        connectedSince = System.currentTimeMillis()
                    )
                )
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIF_ID, buildNotification(ConnState.CONNECTED, profile.name))

            } catch (e: Exception) {
                Log.e(TAG, "startTunnel failed", e)
                updateSnapshot(
                    VpnSnapshot(
                        state = ConnState.ERROR,
                        profileId = profile.id,
                        profileName = profile.name,
                        engine = profile.engine,
                        errorMessage = e.message ?: "خطای ناشناخته"
                    )
                )
                stopTunnelInternal()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopTunnel() {
        stopTunnelInternal()
        updateSnapshot(VpnSnapshot(state = ConnState.DISCONNECTED))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopTunnelInternal() {
        engineJob?.cancel()
        engineJob = null
        try {
            tun?.close()
        } catch (_: Exception) {}
        tun = null
        currentProfile = null
        // TODO Phase 2: call EngineBridge.stop() for each engine
    }

    private fun updateSnapshot(s: VpnSnapshot) {
        _snapshot.value = s
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CH_ID,
                getString(R.string.channel_vpn),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_vpn_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(state: ConnState, name: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AngelVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (state) {
            ConnState.CONNECTING, ConnState.RECONNECTING -> getString(R.string.vpn_notification_connecting)
            ConnState.CONNECTED -> getString(R.string.vpn_notification_connected)
            else -> getString(R.string.vpn_notification_disconnected)
        }
        return NotificationCompat.Builder(this, CH_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText("$title – $name")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .addAction(0, "قطع", stop)
            .setOngoing(state == ConnState.CONNECTED || state == ConnState.CONNECTING)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        stopTunnelInternal()
        updateSnapshot(VpnSnapshot(state = ConnState.DISCONNECTED))
        runningInstance = null
        scope.cancel()
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN revoked by system")
        stopTunnel()
        super.onRevoke()
    }
}
