package com.nebula.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.assistant.core.AiState
import com.nebula.assistant.core.Assistant

/** Schermata a tutto schermo solo per parlare: ascolto continuo e risposte vocali. */
@Composable
fun HandsFree(a: Assistant, st: AiState, light: Boolean) {
    val p = LocalPal.current
    val label by a.stateLabel.collectAsState()
    val transcript by a.transcript.collectAsState()
    BackHandler { a.setHandsFree(false) }
    Box(Modifier.fillMaxSize().background(p.bg), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(a.name(), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Orb(st, 230.dp, light)
            Text(label, color = p.textDim, fontSize = 16.sp)
            Text(transcript, fontSize = 17.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, maxLines = 6)
            NButton("Chiudi il vivavoce", { a.setHandsFree(false) }, kind = "primary")
        }
    }
}
