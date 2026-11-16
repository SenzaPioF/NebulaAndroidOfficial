# Nebula per Android

Assistente vocale personale in **Kotlin + Jetpack Compose**: è la versione Android dell'app Nebula per Windows
(stessa grafica in stile Windows 11, stesse funzioni e impostazioni), **esclusa** la parte "mente" ed "evoluzione".

## Come ottenere l'APK (senza installare nulla)

1. Crea un repository su GitHub e carica **tutti** i file di questa cartella (compresa la cartella nascosta `.github`).
2. Vai su **Actions → "Crea l'APK"**: parte da solo a ogni caricamento (oppure premi *Run workflow*).
3. Quando diventa verde, apri l'esecuzione e scarica l'artefatto **nebula-apk** (uno zip con `app-debug.apk`).
4. Copialo sul telefono e installalo (Android ti chiederà di consentire le "origini sconosciute").

Per avere anche il **riconoscimento dei volti** (sorveglianza) premi *Run workflow* e spunta **con_volti**:
l'APK diventa molto più grande e usa OpenCV (sperimentale).

Se la build fallisce, apri il passaggio rosso nel log di Actions e inviami l'errore: lo correggo.

## Impostazioni iniziali

- **Chiavi API**: Impostazioni → Chiavi API. Incolla una chiave gratuita (Groq, Gemini, OpenRouter, Mistral, Cerebras,
  SambaNova): Nebula riconosce il servizio e sceglie il modello da sola. Oppure usa il servizio pubblico senza chiave
  (Pollinations, sperimentale).
- **Permessi**: microfono (voce), posizione (navigatore), fotocamera (visione e sorveglianza), notifiche.
- **Account Google** (facoltativo): serve creare un ID client OAuth di tipo *Android* nel tuo progetto Google Cloud con
  - nome pacchetto: `com.nebula.assistant`
  - impronta SHA-1 della firma: `5D:3F:17:C8:D9:C3:1E:07:0A:E2:FC:83:0D:7F:DF:FD:2D:A8:EA:D8`
    (la firma di debug è inclusa in `app/debug.keystore`; il workflow la stampa a ogni build).
  I passaggi completi sono nell'app: Servizi → Account Google.

## Cosa c'è

Interfaccia (barra a due gruppi, sfera animata, conversazione, pannelli), ascolto continuo con parola d'attivazione o
vivavoce, voce di Android (voce/velocità/tono), router a 3 chiavi con failover e scelta automatica del modello,
abilità locali senza IA (calcoli, conversioni, date, meteo Open-Meteo, ricerche, appunti, promemoria, volume e musica),
navigatore con mappa OpenStreetMap (Nominatim + OSRM, GPS reale o simulazione, indicazioni ogni 2 s senza IA, luoghi
salvati, posizione impostabile), Google (Gmail, Drive, Calendario, Classroom), allegati (testo, Word, Excel, PDF e
immagini con OCR), fotocamera con visione, rubrica e chiamate, WhatsApp (chat con testo pronto), timer, generazione di
immagini e video, creazione di file, consiglio di più IA, nome dell'IA personalizzabile, tema colore.

## Cosa non c'è (rispetto alla versione per Windows)

- Mente, sogni, creazioni autonome, cervello, profili e **auto-evoluzione**: esclusi come richiesto.
- Controllo del computer (mouse, tastiera, schermo), modalità sandbox e agente: non hanno equivalente su Android.
  Restano apertura delle app, volume/musica, chiamate e link WhatsApp.
- Voci neurali Edge e Whisper: sull'app usa la voce e il riconoscimento vocale di Android.
- Invio automatico dei messaggi WhatsApp: Android non lo permette; l'app apre la chat e premi tu Invio.
- Timer e promemoria suonano solo finché l'app resta aperta.

## Struttura

```
app/src/main/java/com/nebula/assistant/
  core/   Config, Llm (router), Assistant (strumenti), Skills (abilità locali), Nav (navigatore),
          GoogleApi, Voice, Attachments, Faces/Surveillance
  ui/     MainScreen, Sheets (impostazioni, info, telefono, servizi), NavigatorSheet, GoogleSheet,
          CameraSheet, HandsFree, Orb, Components, IconData
  service/NavService  (tiene viva la guida con lo schermo spento)
app/src/faces-off | faces-on   (riconoscimento volti spento/acceso)
```
