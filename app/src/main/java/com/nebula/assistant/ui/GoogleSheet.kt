package com.nebula.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.nebula.assistant.core.Assistant
import com.nebula.assistant.core.ConfigStore
import com.nebula.assistant.core.GOOGLE_FEATURES
import kotlinx.coroutines.launch

private const val SETUP_STEPS =
    "1. Vai su console.cloud.google.com e crea un progetto (gratis).\n" +
        "2. API e servizi, Libreria: abilita Gmail API, Google Drive API, Google Calendar API e Google Classroom API (solo quelle che ti servono).\n" +
        "3. Schermata di consenso OAuth: tipo «Esterno», inserisci un nome, e aggiungi il tuo account Google tra gli «Utenti di test».\n" +
        "4. Credenziali, Crea credenziali, ID client OAuth: tipo applicazione «Android». Nome pacchetto: com.nebula.assistant. " +
        "Impronta SHA-1: quella indicata nel README (e stampata dal workflow GitHub a ogni build).\n" +
        "5. Torna qui, scegli i permessi e premi «Accedi con Google».\n\n" +
        "Note: finché l'app Google resta in modalità «Test», il consenso scade dopo 7 giorni e va rinnovato. " +
        "Gli account scolastici (Workspace for Education) possono bloccare le app esterne o l'API di Classroom: in quel caso usa un account personale."

@Composable
fun GoogleSheet(a: Assistant, store: ConfigStore, onClose: () -> Unit) {
    val cfg by store.flow.collectAsState()
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }

    NSheet("Account Google", onClose) {
        NNote("Collega il tuo account Google per far leggere a Nebula posta, calendario, Drive e Classroom. Nebula non vede la tua password: il consenso lo dai tu a Google e puoi revocarlo quando vuoi. Inviare email o creare file ed eventi chiede sempre la tua conferma.")
        NCard {
            FieldRow("Stato") {
                NNote(
                    (if (cfg.googleConnected) "Collegato" + (if (cfg.googleEmail.isNotEmpty()) " come ${cfg.googleEmail}" else "") + ".\nPermessi concessi: " +
                        (cfg.googleGranted.mapNotNull { GOOGLE_FEATURES[it]?.first }.joinToString(", ").ifEmpty { "nessuno" })
                    else "Non collegato.") + (if (msg.isNotEmpty()) "\n$msg" else "")
                )
            }
        }
        GroupTitle("Permessi richiesti all'accesso")
        NCard {
            var first = true
            for ((k, v) in GOOGLE_FEATURES) {
                if (!first) Divider1()
                first = false
                RowSwitch(v.first, "", k in cfg.googleFeatures) { on ->
                    store.update { c -> c.copy(googleFeatures = if (on) c.googleFeatures + k else c.googleFeatures - k) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NButton("Accedi con Google", {
                scope.launch {
                    msg = "Si apre la finestra di Google: scegli l'account e accetta i permessi…"
                    msg = try {
                        val e = a.google.connect()
                        "✓ Accesso riuscito" + if (e.isNotEmpty()) " ($e)." else "."
                    } catch (e: Exception) {
                        "✗ " + (e.message ?: "accesso non riuscito").take(220)
                    }
                }
            }, kind = "primary")
            NButton("Esci", { a.google.disconnect() }, kind = "danger")
        }
        NCard { Accordion("Come configurare l'accesso (una tantum)", SETUP_STEPS) }
    }
}
