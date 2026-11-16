package com.nebula.assistant.ui

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.assistant.core.AiState
import com.nebula.assistant.core.Assistant
import com.nebula.assistant.core.ChatMsg
import com.nebula.assistant.core.ConfigStore
import com.nebula.assistant.core.Extra
import com.nebula.assistant.core.Who
import com.nebula.assistant.core.paletteOf

/** Funzioni che solo l'Activity può fare: permessi, selettore di file, apertura di file e link. */
interface ActivityActions {
    fun askPermissions(perms: List<String>, onResult: (Boolean) -> Unit)
    fun hasPermission(perm: String): Boolean
    fun pickFiles()
    fun openFile(path: String)
    fun shareFile(path: String)
    fun openUri(url: String)
    fun notificationsPermissionNeeded(): Boolean
}

@Composable
fun MainScreen(a: Assistant, store: ConfigStore, act: ActivityActions) {
    val cfg by store.flow.collectAsState()
    val pal = remember(cfg.theme) { paletteOf(cfg.theme) }
    NebulaTheme(pal) {
        Surface(color = pal.bg, modifier = Modifier.fillMaxSize()) {
            MainContent(a, store, act)
        }
    }
}

@Composable
private fun ToolButton(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        NIcon(icon, if (active) p.accent else p.text, 20.dp)
        if (active) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(7.dp).clip(CircleShape).background(p.accent))
    }
}

@Composable
private fun ToolGroup(content: @Composable () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(p.panel).border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun MainContent(a: Assistant, store: ConfigStore, act: ActivityActions) {
    val p = LocalPal.current
    val cfg by store.flow.collectAsState()
    val msgs by a.messages.collectAsState()
    val st by a.state.collectAsState()
    val label by a.stateLabel.collectAsState()
    val vision by a.visionActive.collectAsState()
    val hands by a.handsFree.collectAsState()
    val alarm by a.alarmActive.collectAsState()
    val confirm by a.pendingConfirm.collectAsState()
    val survOn by a.surveillance.active.collectAsState()
    var sheet by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (act.notificationsPermissionNeeded()) act.askPermissions(listOf(Manifest.permission.POST_NOTIFICATIONS)) { }
    }
    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1)
    }

    fun send() {
        val t = input.trim()
        if (t.isEmpty()) return
        input = ""
        a.submit(t)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ---- barra comandi: sistema a sinistra, funzioni a destra ----
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ToolGroup {
                    ToolButton("gear", "Impostazioni", false) { sheet = "settings" }
                    ToolButton("info", "Info e aggiornamenti", false) { sheet = "info" }
                }
                Row(Modifier.weight(1f)) {}
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    ToolGroup {
                        ToolButton("shield", "Sorveglianza", survOn) { sheet = "surv" }
                        ToolButton("grid", "Servizi", false) { sheet = "services" }
                        ToolButton("phone", "Telefono", false) { sheet = "phone" }
                        ToolButton("headset", "Vivavoce", hands) {
                            act.askPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { ok -> if (ok) a.setHandsFree(true) }
                        }
                        ToolButton("camera", "Fotocamera", vision) {
                            act.askPermissions(listOf(Manifest.permission.CAMERA)) { ok -> if (ok) a.visionActive.value = !a.visionActive.value }
                        }
                        ToolButton("clip", "Allega", false) { act.pickFiles() }
                    }
                }
            }
            // ---- titolo e stato ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cfg.aiName, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (cfg.alwaysOn) {
                            if (cfg.wakeRequired) "Ascolto continuo: attende «${cfg.wakeWord}»" else "Ascolto continuo: risponde a tutto"
                        } else "Tocca il microfono per parlare",
                        color = p.textDim, fontSize = 13.sp,
                    )
                }
                val col = stateColor(st, p.textDim, p.accent)
                Row(
                    Modifier.clip(RoundedCornerShape(15.dp)).background(p.panel).border(1.dp, if (st == AiState.IDLE) p.line else col, RoundedCornerShape(15.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                    Spacer(Modifier.size(8.dp))
                    Text(STATE_LABELS[st] ?: "", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            // ---- sfera ----
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Orb(st, 128.dp, cfg.lightUi)
                Text(label, color = p.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            }
            // ---- fotocamera (visione o sorveglianza) ----
            if (vision || survOn) CameraBox(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)), a)
            // ---- conversazione ----
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.panel).border(1.dp, p.line, RoundedCornerShape(12.dp)),
            ) {
                if (msgs.isEmpty()) {
                    Text(
                        "La conversazione comparirà qui.\nTocca il microfono, scrivi un messaggio oppure attiva l'ascolto continuo dalle impostazioni.",
                        color = p.textDim, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(msgs, key = { it.id }) { m -> Bubble(m, cfg.aiName, act) }
                    }
                }
            }
            // ---- scrittura ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    NTextField(input, { input = it }, "Scrivi un messaggio…")
                }
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(p.panel2).border(1.dp, p.line, RoundedCornerShape(8.dp)).clickable { send() },
                    contentAlignment = Alignment.Center,
                ) { NIcon("send", p.text, 20.dp) }
                val listening = st == AiState.LISTENING
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(if (listening) p.a2 else p.accent).border(if (listening) 3.dp else 0.dp, p.accent, CircleShape).clickable {
                        act.askPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { ok -> if (ok) a.micTap() }
                    },
                    contentAlignment = Alignment.Center,
                ) { NIcon("mic", p.bg, 22.dp) }
            }
        }

        // ---- pannelli ----
        when (sheet) {
            "settings" -> SettingsSheet(a, store, act) { sheet = "" }
            "info" -> InfoSheet { sheet = "" }
            "phone" -> PhoneSheet(a, store) { sheet = "" }
            "services" -> ServicesSheet({ sheet = it }) { sheet = "" }
            "nav" -> NavigatorSheet(a, store, act) { sheet = "" }
            "google" -> GoogleSheet(a, store) { sheet = "" }
            "surv" -> SurveillanceSheet(a, act) { sheet = "" }
        }

        // ---- sovrapposizioni ----
        if (hands) HandsFree(a, st, cfg.lightUi)
        if (alarm) AlarmOverlay { a.stopAlarm() }
        val cr = confirm
        if (cr != null) {
            AlertDialog(
                onDismissRequest = { cr.result.complete(false) },
                containerColor = p.panel, titleContentColor = p.text, textContentColor = p.text,
                title = { Text("${cfg.aiName} chiede conferma") },
                text = { Text(cr.text) },
                confirmButton = { TextButton(onClick = { cr.result.complete(true) }) { Text("Esegui", color = p.accent) } },
                dismissButton = { TextButton(onClick = { cr.result.complete(false) }) { Text("Annulla", color = p.textDim) } },
            )
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg, aiName: String, act: ActivityActions) {
    val p = LocalPal.current
    val user = m.who == Who.USER
    val sys = m.who == Who.SYS
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Text(if (user) "Tu" else if (sys) "Sistema" else aiName, color = p.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        if (m.text.isNotEmpty()) {
            if (sys) {
                Text(m.text, color = p.textDim, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.widthIn(max = 340.dp))
            } else {
                Text(
                    m.text, color = if (user) p.bg else p.text, fontSize = 15.sp, lineHeight = 21.sp,
                    modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(12.dp)).background(if (user) p.accent else p.panel2).padding(horizontal = 13.dp, vertical = 9.dp),
                )
            }
        }
        for (e in m.extras) {
            when (e) {
                is Extra.Link -> Chip("🔗 " + e.label) { act.openUri(e.url) }
                is Extra.VideoLink -> Chip("▶ Guarda il video") { act.openUri(e.url) }
                is Extra.FileChip -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("📄 Apri ${e.name}") { act.openFile(e.path) }
                    Chip("Condividi") { act.shareFile(e.path) }
                }
                is Extra.Image -> Image(
                    e.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(top = 4.dp).widthIn(max = 320.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, p.line, RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Text(
        text, color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(p.panel2).border(1.dp, p.line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun AlarmOverlay(onStop: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF8A0000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("⚠ Volto non riconosciuto", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Rilevata una persona sconosciuta", color = Color(0xFFFFD0D0), fontSize = 15.sp)
            NButton("Ferma allarme", onStop, kind = "normal")
        }
    }
}
