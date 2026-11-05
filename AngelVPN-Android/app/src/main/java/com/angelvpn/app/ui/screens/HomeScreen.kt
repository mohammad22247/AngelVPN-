package com.angelvpn.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.angelvpn.app.domain.ConnState
import com.angelvpn.app.domain.Engine
import com.angelvpn.app.domain.Profile
import com.angelvpn.app.domain.VpnSnapshot
import com.angelvpn.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profiles: List<Profile>,
    snapshot: VpnSnapshot,
    importText: String,
    flash: String?,
    onImportTextChange: (String) -> Unit,
    onImport: (String) -> Unit,
    onConnect: (Profile) -> Unit,
    onDisconnect: () -> Unit,
    onDelete: (String) -> Unit,
    onCopyLink: (Profile) -> Unit,
    onClearFlash: () -> Unit
) {
    var showImport by remember { mutableStateOf(false) }

    LaunchedEffect(flash) {
        if (flash != null) {
            delay(2800)
            onClearFlash()
        }
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = Gold, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "AngelVPN",
                            fontWeight = FontWeight.Bold,
                            color = Fg,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showImport = true }) {
                        Icon(Icons.Default.Add, null, tint = Gold)
                        Spacer(Modifier.width(4.dp))
                        Text("ایمپورت", color = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = Fg
                )
            )
        },
        floatingActionButton = {
            if (snapshot.state == ConnState.CONNECTED || snapshot.state == ConnState.CONNECTING) {
                ExtendedFloatingActionButton(
                    onClick = onDisconnect,
                    containerColor = Danger,
                    contentColor = Fg
                ) {
                    Icon(Icons.Default.PowerSettingsNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("قطع اتصال")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Status card
            StatusCard(snapshot)
            Spacer(Modifier.height(12.dp))

            // Flash message
            AnimatedVisibility(visible = flash != null) {
                Text(
                    flash ?: "",
                    color = Signal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface2)
                        .padding(12.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
            }

            // Profile list
            if (profiles.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Link, null, tint = Muted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("هنوز پروفایلی نیست", color = Muted, fontSize = 16.sp)
                        Text(
                            "لینک slipnet / dnst / soar / cotten / masterdns را ایمپورت کنید",
                            color = Muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                        Button(
                            onClick = { showImport = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
                        ) {
                            Text("افزودن کانفیگ")
                        }
                    }
                }
            } else {
                Text(
                    "${profiles.size} پروفایل",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileCard(
                            profile = profile,
                            isActive = snapshot.profileId == profile.id &&
                                    snapshot.state in listOf(ConnState.CONNECTED, ConnState.CONNECTING, ConnState.RECONNECTING),
                            onConnect = { onConnect(profile) },
                            onDelete = { onDelete(profile.id) },
                            onCopy = { onCopyLink(profile) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showImport) {
        ImportSheet(
            text = importText,
            onTextChange = onImportTextChange,
            onImport = {
                onImport(importText)
                showImport = false
            },
            onDismiss = { showImport = false }
        )
    }
}

@Composable
private fun StatusCard(snapshot: VpnSnapshot) {
    val (label, color) = when (snapshot.state) {
        ConnState.CONNECTED -> "متصل" to Signal
        ConnState.CONNECTING, ConnState.RECONNECTING -> "در حال اتصال…" to Gold
        ConnState.ERROR -> "خطا" to Danger
        ConnState.DISCONNECTED -> "قطع" to Muted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Fg, fontWeight = FontWeight.SemiBold)
            if (snapshot.profileName != null) {
                Text(
                    "${snapshot.engine?.label ?: ""} · ${snapshot.profileName}",
                    color = Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (snapshot.errorMessage != null) {
                Text(snapshot.errorMessage, color = Danger, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    isActive: Boolean,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    val borderColor = if (isActive) Signal else Line
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EngineChip(profile.engine, profile.subtype)
            Spacer(Modifier.width(8.dp))
            Text(
                profile.name,
                color = Fg,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            profile.domain.ifBlank { "—" },
            color = Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onConnect,
                enabled = !isActive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Surface2 else Gold,
                    contentColor = if (isActive) Muted else Ink
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(if (isActive) "فعال" else "اتصال", fontSize = 13.sp)
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ContentCopy, "کپی لینک", tint = Muted, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, "حذف", tint = Danger.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EngineChip(engine: Engine, subtype: String?) {
    val label = buildString {
        append(engine.label)
        if (!subtype.isNullOrBlank() && !subtype.equals(engine.name, true)) {
            append(" · ")
            append(subtype)
        }
    }
    Text(
        label,
        color = Gold,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Gold.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(
    text: String,
    onTextChange: (String) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        contentColor = Fg
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "ایمپورت کانفیگ",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Fg
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "لینک slipnet / dnst / soar / cotten / masterdns یا JSON را بچسبانید",
                color = Muted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text("slipnet://… یا dnst://…", color = Muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = Line,
                    focusedTextColor = Fg,
                    unfocusedTextColor = Fg,
                    cursorColor = Gold
                ),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onImport,
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
            ) {
                Text("افزودن", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
