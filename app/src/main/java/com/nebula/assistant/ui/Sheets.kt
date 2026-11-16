package com.nebula.assistant.ui

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.assistant.BuildConfig
import com.nebula.assistant.core.Assistant
import com.nebula.assistant.core.Config
import com.nebula.assistant.core.ConfigStore
import com.nebula.assistant.core.Contact
import com.nebula.assistant.core.DANGER_COLOR
import com.nebula.assistant.core.PRESETS
import com.nebula.assistant.core.Slot
import com.nebula.assistant.core.THEME_LABELS
import com.nebula.assistant.core.normalizeSlots
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun updateSlot(store: ConfigStore, i: Int, f: (Slot) -> Slot) {
    store.update { c -> c.copy(slots = normalizeSlots(c.slots).mapIndexed { k, s -> if (k == i) f(s) else s }) }
}

@Composable
private fun SlotCard(i: Int, a: Assistant, store: ConfigStore, act: ActivityActions) {
    val p = LocalPal.current
    val cfg by store.flow.collectAsState()
    val slot = normalizeSlots(cfg.slots)[i]
    val routerStatus by a.router.status.collectAsState()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var adv by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(listOf<String>()) }
    val preset = a.router.preset(slot)

    // collegamento automatico: riconosce il servizio dalla chiave, sceglie il modello e prova
    LaunchedEffect(slot.key) {
        if (slot.key.isBlank()) {
            status = if (preset.needsKey) "Incolla una chiave per iniziare." else ""
            return@LaunchedEffect
        }
        delay(900)
        status = "Riconosco il servizio e provo la chiave…"
        status = try {
            "✓ " + a.router.setup(i)
        } catch (e: Exception) {
            "✗ " + (e.message ?: "errore").take(200) + (if ((e.message ?: "").contains("chiave")) " Controlla di aver incollato la chiave giusta." else "")
        }
    }

    NCard {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Chiave ${i + 1}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(if (adv) "Avanzate ▾" else "Avanzate ▸", color = p.accent, fontSize = 13.sp, modifier = Modifier.padding(4.dp).then(Modifier.clickableNoRipple { adv = !adv }))
            }
            NTextField(slot.key, { v -> updateSlot(store, i) { it.copy(key = v.trim()) } }, "Incolla qui una chiave API", password = true)
            val extra = routerStatus[i] ?: ""
            NNote(status.ifEmpty { extra.ifEmpty { if (a.router.usable(slot)) "Pronto." else "" } })
            if (adv) {
                NDropdown(PRESETS.map { it.id to it.label }, slot.provider, { v ->
                    updateSlot(store, i) { it.copy(provider = v, model = "") }
                    a.router.clearAuto(i)
                })
                if (slot.provider == "custom" || slot.provider == "ollama") {
                    NTextField(slot.base, { v -> updateSlot(store, i) { it.copy(base = v.trim()) } }, "Indirizzo API, es. http://192.168.1.10:11434/v1")
                }
                NTextField(slot.model, { v -> updateSlot(store, i) { it.copy(model = v.trim()) } }, "Modello (vuoto = ${preset.model.ifEmpty { "da indicare" }})")
                NTextField(slot.visionModel, { v -> updateSlot(store, i) { it.copy(visionModel = v.trim()) } }, "Modello per le immagini (facoltativo)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NButton("Carica modelli", {
                        scope.launch {
                            status = "Carico i modelli…"
                            try {
                                models = a.router.listModels(i)
                                status = "${models.size} modelli disponibili: scegline uno."
                            } catch (e: Exception) {
                                status = "Impossibile caricare i modelli: " + (e.message ?: "").take(100)
                            }
                        }
                    })
                    NButton("Prova", {
                        scope.launch {
                            status = "Provo…"
                            status = try {
                                "✓ Funziona. Risposta: " + a.router.test(i)
                            } catch (e: Exception) {
                                "✗ Non funziona: " + (e.message ?: "").take(160)
                            }
                        }
                    }, kind = "primary")
                }
                if (models.isNotEmpty()) {
                    NDropdown(models.map { it to it }, slot.model, { v -> updateSlot(store, i) { it.copy(model = v) } })
                }
                if (preset.keyUrl.isNotEmpty()) NButton("Dove la trovo?", { act.openUri(preset.keyUrl) })
            }
        }
    }
}

@Composable
fun SettingsSheet(a: Assistant, store: ConfigStore, act: ActivityActions, onClose: () -> Unit) {
    val p = LocalPal.current
    val cfg by store.flow.collectAsState()
    val scope = rememberCoroutineScope()
    var nameDraft by remember(cfg.aiName) { mutableStateOf(cfg.aiName) }
    var voices by remember { mutableStateOf(a.tts.voices()) }
    var poll by remember { mutableStateOf("") }

    NSheet("Impostazioni", onClose) {
        GroupTitle("Identità")
        NCard {
            FieldRow("Nome dell'IA", "Come si chiama il tuo assistente. Se la parola d'attivazione era uguale al vecchio nome, cambia anche quella.") {
                NTextField(nameDraft, { nameDraft = it }, "Nebula")
                NButton("Applica il nome", { a.rename(nameDraft) })
            }
        }

        GroupTitle("Risparmio delle chiavi API")
        NCard {
            RowSwitch(
                "Usa il meno possibile le chiavi API",
                "Risponde da sola a ciò che non richiede un'IA (ore, calcoli, conversioni, meteo, ricerche, appunti, navigazione, posta e calendario Google...).",
                cfg.saveKeys,
            ) { v -> store.update { it.copy(saveKeys = v) } }
            NNote("Chiamate all'IA risparmiate finora: ${cfg.savedCalls}.", Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
        }

        GroupTitle("Ascolto")
        NCard {
            RowSwitch("Ascolto sempre attivo", "Il microfono resta acceso in attesa di comandi (consuma batteria).", cfg.alwaysOn) { v ->
                if (v) {
                    act.askPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { ok ->
                        if (ok) {
                            store.update { it.copy(alwaysOn = true) }
                            a.syncListening()
                        }
                    }
                } else {
                    store.update { it.copy(alwaysOn = false) }
                    a.syncListening()
                }
            }
            Divider1()
            RowSwitch("Richiedi parola d'attivazione", "Con l'ascolto continuo risponde solo se dici prima la parola qui sotto.", cfg.wakeRequired) { v ->
                store.update { it.copy(wakeRequired = v) }
            }
            Divider1()
            FieldRow("Parola d'attivazione") {
                NTextField(cfg.wakeWord, { v -> store.update { it.copy(wakeWord = v.lowercase()) } }, "nebula")
            }
        }

        GroupTitle("Lingua e voce")
        NCard {
            FieldRow("Lingua") {
                NDropdown(
                    listOf("it-IT" to "Italiano", "en-US" to "English (US)", "en-GB" to "English (UK)", "fr-FR" to "Français", "de-DE" to "Deutsch", "es-ES" to "Español"),
                    cfg.lang, { v -> store.update { it.copy(lang = v, ttsVoice = "") } },
                )
            }
            Divider1()
            FieldRow("Voce di risposta", "Le voci sono quelle installate sul telefono (Impostazioni di Android, Sintesi vocale).") {
                NDropdown(listOf("" to "Automatica") + voices, cfg.ttsVoice, { v -> store.update { it.copy(ttsVoice = v) } })
                NButton("Aggiorna l'elenco", { voices = a.tts.voices() })
            }
            Divider1()
            FieldRow("Velocità: ${"%.1f".format(cfg.ttsRate)}x") {
                NSlider(cfg.ttsRate, { v -> store.update { it.copy(ttsRate = v) } }, 0.5f..2.0f)
            }
            Divider1()
            FieldRow("Tono: ${"%.1f".format(cfg.ttsPitch)}") {
                NSlider(cfg.ttsPitch, { v -> store.update { it.copy(ttsPitch = v) } }, 0.5f..2.0f)
            }
            Divider1()
            FieldRow("") { NButton("Prova la voce", { a.speakQuiet("Ciao, sono ${a.name()}. Questa è la mia voce.") }) }
        }

        GroupTitle("Intelligenza artificiale")
        NCard {
            FieldRow("Personalità e istruzioni") {
                NTextField(cfg.personality, { v -> store.update { it.copy(personality = v) } }, "", singleLine = false, minLines = 4)
            }
        }

        GroupTitle("Chiavi API")
        NNote("Incolla una chiave: Nebula riconosce il servizio (Groq, Gemini, OpenRouter, Mistral, Cerebras, SambaNova), sceglie il modello e prova da sola. Con più chiavi, se una raggiunge il limite gratuito passa alla successiva. Quasi tutti hanno un piano gratuito.")
        for (i in 0 until 3) SlotCard(i, a, store, act)
        NButton("Aggiungo un servizio pubblico senza chiave (Pollinations)", {
            val sl = normalizeSlots(store.value.slots)
            val idx = sl.indexOfFirst { it.key.isBlank() && it.provider != "pollinations" }.let { if (it < 0) 2 else it }
            updateSlot(store, idx) { Slot("pollinations") }
            scope.launch {
                poll = "Provo il servizio…"
                poll = try { "✓ " + a.router.setup(idx) } catch (e: Exception) { "✗ " + (e.message ?: "").take(120) }
            }
        })
        if (poll.isNotEmpty()) NNote(poll)
        NCard {
            FieldRow("Quale chiave usa cosa") {
                NDropdown(listOf("auto" to "Conversazione: automatica (in ordine)", "1" to "Conversazione: chiave 1", "2" to "Conversazione: chiave 2", "3" to "Conversazione: chiave 3"), cfg.routeChat, { v -> store.update { it.copy(routeChat = v) } })
                NDropdown(listOf("auto" to "Visione: automatica", "1" to "Visione: chiave 1", "2" to "Visione: chiave 2", "3" to "Visione: chiave 3"), cfg.routeVision, { v -> store.update { it.copy(routeVision = v) } })
            }
        }

        GroupTitle("Aspetto")
        NCard {
            FieldRow("Tema colore") {
                NDropdown(THEME_LABELS, cfg.theme, { v -> store.update { it.copy(theme = v) } })
            }
            Divider1()
            RowSwitch("Animazioni ridotte", "Sfera più semplice: consuma meno batteria.", cfg.lightUi) { v -> store.update { it.copy(lightUi = v) } }
        }

        GroupTitle("Avanzate")
        NCard {
            FieldRow("Prefisso internazionale predefinito", "Usato per i numeri in rubrica scritti senza prefisso (39 = Italia), per WhatsApp.") {
                NTextField(cfg.countryCode, { v -> store.update { it.copy(countryCode = v.filter { c -> c.isDigit() }) } }, "39")
            }
            Divider1()
            FieldRow("") { NButton("Cancella la conversazione", { a.clearChat() }, kind = "danger") }
        }
    }
}

private val GUIDE: List<Pair<String, String>> = listOf(
    "Come funziona" to "Le risposte sono generate da un servizio di intelligenza artificiale a tua scelta: inserisci almeno una chiave nelle impostazioni, sezione Chiavi API. Il riconoscimento vocale e la voce sono quelli di Android (gratuiti). Le chiavi restano salvate sul telefono e vengono inviate solo al servizio a cui appartengono.",
    "Chiavi API gratuite" to "Hai tre slot. Incolla la chiave e Nebula riconosce il servizio, sceglie il modello e prova. Dove trovare le chiavi:\n- Groq: console.groq.com/keys\n- Google Gemini: aistudio.google.com/apikey\n- OpenRouter: openrouter.ai/keys (modelli con :free)\n- Mistral: console.mistral.ai\n- Cerebras: cloud.cerebras.ai\n- SambaNova: cloud.sambanova.ai\n- Ollama: un PC della tua rete con Ollama acceso (indica l'indirizzo nelle opzioni avanzate)\n\nCon più chiavi, se una raggiunge il limite gratuito, Nebula passa alla successiva. Per la visione servono Groq, Gemini o Mistral, oppure un «modello per le immagini».",
    "Risparmio delle chiavi API" to "Nebula risponde da sola, senza IA, a: ore e date («che giorno sarà tra 10 giorni», «quanti giorni mancano a Natale»), calcoli («quanto fa 12 per 7», «il 20% di 350»), conversioni («5 km in miglia»), meteo («che tempo fa a Roma»), ricerche («cerca ricette su YouTube»), appunti («prendi nota: comprare il latte»), promemoria («ricordami di chiamare Mario tra 10 minuti»), volume e musica, navigazione, posta, calendario e Classroom di Google.",
    "Comandi rapidi" to "«Che ore sono», «che giorno è», «dove mi trovo», «apri YouTube», «apri WhatsApp», «genera un'immagine di…», «chiama [nome]», «metti un timer di 5 minuti». I timer suonano solo finché l'app resta aperta.",
    "Navigatore" to "Servizi, poi Navigatore, oppure di': «portami a Piazza Duomo a Milano», «portami a casa a piedi». L'indirizzo si cerca con OpenStreetMap, il percorso con OSRM; poi ogni 2 secondi Nebula confronta il GPS con il tracciato e dà le indicazioni a voce, senza IA e senza chiavi. Comandi: «quanto manca», «ripeti l'indicazione», «ferma la navigazione», «salva questo posto come casa». Serve il permesso di posizione; per provare senza muoverti attiva «Simula il percorso». Mappa e luoghi da OpenStreetMap.",
    "Posizione" to "Ordine usato: GPS del telefono, poi la posizione che imposti tu («mi trovo a Bisceglie» oppure Navigatore, La mia posizione), poi una stima dall'indirizzo IP (può sbagliare di centinaia di chilometri).",
    "Account Google" to "Servizi, poi Account Google. Serve creare una volta sola, gratis, un ID client OAuth di tipo Android nel tuo progetto Google Cloud (i passaggi sono nella finestra). Permessi a scelta, in sola lettura di partenza; inviare email o creare file ed eventi chiede sempre conferma. Richieste gestite senza IA: «controlla la posta», «cosa ho in calendario domani», «cosa devo consegnare», «cerca nel drive storia».",
    "Allegati e fotocamera" to "Con la graffetta allega testo, Word, Excel, PDF o immagini: Nebula legge il contenuto (PDF e immagini con il riconoscimento del testo). Con l'icona della fotocamera, ogni domanda viene inviata insieme all'inquadratura a un modello con visione.",
    "Telefono e WhatsApp" to "Salva i contatti nella sezione Telefono, poi di' «chiama [nome]»: si apre il telefono con il numero già composto. Per WhatsApp Nebula apre la chat con il messaggio già scritto: premi Invio tu.",
    "Vivavoce" to "L'icona delle cuffie apre una schermata solo per parlare, con ascolto continuo.",
    "Sorveglianza" to "Registra il tuo volto e attiva la sorveglianza: se la fotocamera inquadra un volto diverso scatta l'allarme. Serve l'APK compilato con l'opzione «con_volti»; funziona solo con l'app aperta e la fotocamera attiva. Non è un sistema di sicurezza professionale.",
)

@Composable
fun InfoSheet(onClose: () -> Unit) {
    NSheet("Info e aggiornamenti", onClose) {
        NCard {
            FieldRow("Nebula per Android", "Versione ${BuildConfig.VERSION_NAME}. Per aggiornare, scarica il nuovo APK dalla pagina del progetto su GitHub (Actions, artefatto nebula-apk) e installalo sopra a questo: impostazioni e rubrica restano.") {}
        }
        GroupTitle("Guida")
        NCard {
            for ((i, g) in GUIDE.withIndex()) {
                if (i > 0) Divider1()
                Accordion(g.first, g.second)
            }
        }
    }
}

@Composable
fun PhoneSheet(a: Assistant, store: ConfigStore, onClose: () -> Unit) {
    val p = LocalPal.current
    val cfg by store.flow.collectAsState()
    var dial by remember { mutableStateOf("") }
    var cname by remember { mutableStateOf("") }
    var cnum by remember { mutableStateOf("") }
    NSheet("Telefono", onClose) {
        GroupTitle("Chiama un numero")
        NCard {
            FieldRow("") {
                NTextField(dial, { dial = it }, "Numero di telefono")
                NButton("Chiama", { a.dial(dial) }, kind = "primary")
            }
        }
        GroupTitle("Rubrica")
        NCard {
            if (cfg.contacts.isEmpty()) NNote("Nessun contatto salvato.", Modifier.padding(14.dp))
            for ((i, c) in cfg.contacts.withIndex()) {
                if (i > 0) Divider1()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.Medium)
                        NNote(c.number)
                    }
                    NButton("Chiama", { a.dial(c.number) }, kind = "primary")
                    NButton("Elimina", { store.update { cf -> cf.copy(contacts = cf.contacts.filterIndexed { k, _ -> k != i }) } }, kind = "danger")
                }
            }
        }
        NCard {
            FieldRow("Aggiungi un contatto") {
                NTextField(cname, { cname = it }, "Nome")
                NTextField(cnum, { cnum = it }, "Numero")
                NButton("Aggiungi contatto", {
                    if (cname.isNotBlank() && cnum.isNotBlank()) {
                        store.update { cf -> cf.copy(contacts = cf.contacts + Contact(cname.trim(), cnum.trim())) }
                        cname = ""
                        cnum = ""
                    }
                })
            }
        }
        NNote("«Chiama» apre il telefono con il numero già composto. Puoi anche dire «chiama [nome]» o chiedere di scrivere un messaggio WhatsApp a un contatto salvato.")
    }
}

@Composable
fun ServicesSheet(open: (String) -> Unit, onClose: () -> Unit) {
    NSheet("Servizi", onClose) {
        NNote("Strumenti che funzionano quasi tutti senza usare le chiavi dell'intelligenza artificiale.")
        NCard {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NButton("Navigatore (mappa e indicazioni stradali)", { open("nav") }, Modifier.fillMaxWidth(), kind = "primary")
                NButton("Account Google (Gmail, Drive, Calendario, Classroom)", { open("google") }, Modifier.fillMaxWidth())
            }
        }
    }
}
