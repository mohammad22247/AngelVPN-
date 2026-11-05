package com.angelvpn.app.ui

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.angelvpn.app.domain.ConnState
import com.angelvpn.app.domain.Profile
import com.angelvpn.app.parser.ConfigParser
import com.angelvpn.app.ui.screens.HomeScreen
import com.angelvpn.app.ui.theme.AngelTheme
import com.angelvpn.app.ui.theme.Ink
import com.angelvpn.app.vpn.AngelVpnService
import com.google.gson.Gson
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var pendingProfile: Profile? = null

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingProfile?.let { connectVpn(it) }
        } else {
            Toast.makeText(this, "مجوز VPN رد شد", Toast.LENGTH_SHORT).show()
        }
        pendingProfile = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleImportIntent(intent)

        setContent {
            AngelTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    val profiles by vm.profiles.collectAsState()
                    val snapshot by AngelVpnService.snapshot.collectAsState()
                    val importText by vm.importText.collectAsState()
                    val flash by vm.flash.collectAsState()

                    HomeScreen(
                        profiles = profiles,
                        snapshot = snapshot,
                        importText = importText,
                        flash = flash,
                        onImportTextChange = vm::setImportText,
                        onImport = { text ->
                            val result = ConfigParser.parse(text)
                            if (result.profiles.isEmpty()) {
                                vm.flash(result.errors.firstOrNull() ?: "پروفایلی پیدا نشد")
                            } else {
                                lifecycleScope.launch {
                                    result.profiles.forEach { vm.upsert(it) }
                                    vm.setImportText("")
                                    val w = if (result.warnings.isNotEmpty())
                                        " (${result.warnings.size} هشدار)" else ""
                                    vm.flash("${result.profiles.size} پروفایل اضافه شد$w")
                                }
                            }
                        },
                        onConnect = { profile -> requestVpnAndConnect(profile) },
                        onDisconnect = { disconnectVpn() },
                        onDelete = { id -> lifecycleScope.launch { vm.delete(id) } },
                        onCopyLink = { profile ->
                            val link = ConfigParser.toNativeLink(profile)
                            val clip = android.content.ClipData.newPlainText("config", link)
                            getSystemService(android.content.ClipboardManager::class.java)
                                .setPrimaryClip(clip)
                            vm.flash("لینک کپی شد")
                        },
                        onClearFlash = { vm.clearFlash() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleImportIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null) {
            val link = data.toString()
            val result = ConfigParser.parse(link)
            if (result.profiles.isNotEmpty()) {
                lifecycleScope.launch {
                    result.profiles.forEach { vm.upsert(it) }
                    vm.flash("${result.profiles.size} پروفایل از لینک ایمپورت شد")
                }
            }
        }
        // Also support ACTION_SEND text
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val result = ConfigParser.parse(shared)
            if (result.profiles.isNotEmpty()) {
                lifecycleScope.launch {
                    result.profiles.forEach { vm.upsert(it) }
                    vm.flash("${result.profiles.size} پروفایل از اشتراک‌گذاری اضافه شد")
                }
            }
        }
    }

    private fun requestVpnAndConnect(profile: Profile) {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            pendingProfile = profile
            vpnPermission.launch(prepare)
        } else {
            connectVpn(profile)
        }
    }

    private fun connectVpn(profile: Profile) {
        val intent = Intent(this, AngelVpnService::class.java).apply {
            action = AngelVpnService.ACTION_CONNECT
            putExtra(AngelVpnService.EXTRA_PROFILE_JSON, Gson().toJson(profile))
        }
        startForegroundService(intent)
    }

    private fun disconnectVpn() {
        val intent = Intent(this, AngelVpnService::class.java).apply {
            action = AngelVpnService.ACTION_DISCONNECT
        }
        startService(intent)
    }
}
