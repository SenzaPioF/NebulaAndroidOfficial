package com.nebula.assistant.ui

import android.Manifest
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nebula.assistant.core.Assistant
import com.nebula.assistant.core.ConfigStore
import com.nebula.assistant.core.LatLon
import com.nebula.assistant.core.Place
import com.nebula.assistant.core.Resolved
import com.nebula.assistant.core.Route
import com.nebula.assistant.core.SimulatedPosition
import com.nebula.assistant.core.fmtDist
import com.nebula.assistant.core.fmtEta
import com.nebula.assistant.core.maneuverText
import com.nebula.assistant.core.shortLabel
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Mappa OpenStreetMap (osmdroid) con percorso, posizione e destinazione. */
@Composable
fun OsmMap(
    route: Route?, pos: LatLon?, here: Resolved?, pick: Boolean, onPick: (LatLon) -> Unit, modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val view = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(42.0, 12.5))
            // dentro un pannello scorrevole la mappa deve ricevere i trascinamenti
            setOnTouchListener { v, ev ->
                if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    val lastRoute = remember { arrayOfNulls<Any>(1) }
    val lastHere = remember { arrayOfNulls<Any>(1) }
    DisposableEffect(Unit) {
        view.onResume()
        onDispose {
            view.onPause()
            view.onDetach()
        }
    }
    AndroidView(factory = { view }, modifier = modifier, update = { v ->
        v.overlays.clear()
        v.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (pick && p != null) {
                    onPick(LatLon(p.latitude, p.longitude))
                    return true
                }
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }))
        if (route != null) {
            val pts = route.coords.map { GeoPoint(it.lat, it.lon) }
            val line = Polyline()
            line.setPoints(pts)
            line.outlinePaint.color = 0xFF0078D4.toInt()
            line.outlinePaint.strokeWidth = 12f
            v.overlays.add(line)
            val d = Marker(v)
            d.position = GeoPoint(route.destPt.lat, route.destPt.lon)
            d.title = shortLabel(route.destLabel)
            d.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            v.overlays.add(d)
            if (route !== lastRoute[0]) {
                lastRoute[0] = route
                v.post { v.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), true, 80) }
            }
        }
        val me: GeoPoint? = if (pos != null) GeoPoint(pos.lat, pos.lon) else if (here != null) GeoPoint(here.lat, here.lon) else null
        if (me != null) {
            val m = Marker(v)
            m.position = me
            m.title = if (pos != null) "Tu" else "La tua posizione"
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            v.overlays.add(m)
            if (pos != null) {
                v.controller.animateTo(me)
            } else if (here !== lastHere[0] && route == null) {
                lastHere[0] = here
                v.controller.setZoom(if (here?.source == "rete") 9.0 else 15.0)
                v.controller.setCenter(me)
            }
        }
        v.invalidate()
    })
}

@Composable
fun NavigatorSheet(a: Assistant, store: ConfigStore, act: ActivityActions, onClose: () -> Unit) {
    val p = LocalPal.current
    val cfg by store.flow.collectAsState()
    val route by a.nav.route.collectAsState()
    val ns by a.nav.state.collectAsState()
    val scope = rememberCoroutineScope()
    var dest by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("auto") }
    var banner by remember { mutableStateOf("Scrivi una destinazione e premi «Traccia il percorso».") }
    var locText by remember { mutableStateOf("") }
    var locStatus by remember { mutableStateOf("") }
    var here by remember { mutableStateOf<Resolved?>(null) }
    var pick by remember { mutableStateOf(false) }
    var placeName by remember { mutableStateOf("") }
    var selPlace by remember { mutableStateOf("") }

    fun refreshLoc() {
        locStatus = "Cerco la tua posizione…"
        scope.launch {
            val r = a.position()
            here = r
            locStatus = if (r == null) "Non conosco la tua posizione: impostala qui sotto."
            else when (r.source) {
                "gps" -> "Posizione del GPS (precisa)."
                "manuale" -> "Posizione impostata da te: ${r.label}."
                else -> "Solo una stima dalla rete: ${r.label}. Imposta la tua posizione per correggerla."
            }
        }
    }

    LaunchedEffect(Unit) {
        act.askPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            a.gps.start()
            refreshLoc()
        }
    }

    NSheet("Navigatore", onClose) {
        OsmMap(
            route, if (ns.active) ns.pos else null, here, pick,
            onPick = { ll ->
                pick = false
                scope.launch {
                    val name = try {
                        val r = a.nav.reverse(ll.lat, ll.lon)
                        listOf(r.road, r.city).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "punto scelto sulla mappa" }
                    } catch (e: Exception) {
                        "punto scelto sulla mappa"
                    }
                    store.update { it.copy(myPlace = Place(ll.lat, ll.lon, name)) }
                    refreshLoc()
                }
            },
            modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp)),
        )
        // indicazione in corso
        NCard {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (ns.active && !ns.noPosition && ns.error.isEmpty()) {
                    Text(
                        if (ns.arrived) "Sei arrivato a destinazione." else ns.nextText + (ns.distToNext?.let { ", tra ${fmtDist(it)}" } ?: ""),
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.accent,
                    )
                    NNote("Mancano ${fmtDist(ns.remainingM)}, arrivo tra circa ${fmtEta(ns.etaS)}." + if (ns.offRoute) " Sei fuori percorso." else "")
                } else if (ns.active && ns.noPosition) {
                    Text("Non ricevo la posizione: esci all'aperto, attiva la posizione del telefono oppure usa «Simula il percorso».", color = p.textDim, fontSize = 14.sp)
                } else {
                    Text(banner, color = p.textDim, fontSize = 14.sp)
                }
                if (ns.error.isNotEmpty()) NNote(ns.error)
            }
        }
        GroupTitle("Destinazione")
        NCard {
            FieldRow("") {
                NTextField(dest, { dest = it }, "Dove vuoi andare? (es. Piazza Duomo, Milano)")
                NDropdown(listOf("auto" to "In auto", "bici" to "In bici", "piedi" to "A piedi"), mode, { mode = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NButton("Traccia il percorso", {
                        if (dest.isNotBlank()) scope.launch {
                            banner = "Cerco il percorso…"
                            banner = a.startNavigation(dest, mode, false)
                        }
                    }, kind = "primary")
                    NButton("Avvia la guida", {
                        if (route == null) banner = "Prima traccia un percorso."
                        else try {
                            a.nav.start(if (cfg.navSim) SimulatedPosition(cfg.navSimSpeed.toDouble()) else a.gps, true)
                        } catch (e: Exception) {
                            banner = "Non riesco ad avviare la guida: " + (e.message ?: "").take(100)
                        }
                    })
                    NButton("Ferma", { a.nav.stop() }, kind = "danger")
                }
            }
        }
        route?.let { r ->
            NCard {
                Accordion("Passaggi del percorso", r.steps.withIndex().joinToString("\n") { (i, s) -> "${i + 1}. ${maneuverText(s)} (${fmtDist(s.distance)})" })
            }
        }
        GroupTitle("Luoghi salvati")
        NCard {
            FieldRow("") {
                val places = cfg.places
                if (places.isEmpty()) NNote("Nessun luogo salvato. Traccia un percorso, poi salva la destinazione con un nome (casa, lavoro...).")
                else {
                    NDropdown(places.keys.map { it to it.replaceFirstChar { c -> c.uppercase() } }, selPlace.ifEmpty { places.keys.first() }, { selPlace = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NButton("Vai", { dest = selPlace.ifEmpty { places.keys.first() } }, kind = "primary")
                        NButton("Elimina", { a.nav.deletePlace(selPlace.ifEmpty { places.keys.first() }) }, kind = "danger")
                    }
                }
                NTextField(placeName, { placeName = it }, "Nome con cui salvare la destinazione (es. casa)")
                NButton("Salva la destinazione", {
                    val r = route
                    if (r != null && placeName.isNotBlank()) {
                        a.nav.savePlace(placeName, Place(r.destPt.lat, r.destPt.lon, r.destLabel))
                        placeName = ""
                    } else banner = "Prima traccia un percorso e scrivi un nome."
                })
            }
        }
        GroupTitle("La mia posizione")
        NCard {
            FieldRow("", "L'indirizzo IP dà solo una stima della sede del tuo provider e può sbagliare di centinaia di chilometri. Imposta qui la tua posizione (o dì «mi trovo a» e la tua città): la uso quando il GPS non c'è.") {
                if (locStatus.isNotEmpty()) NNote(locStatus)
                NTextField(locText, { locText = it }, "La tua città o il tuo indirizzo (es. Bisceglie)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NButton("Imposta", {
                        if (locText.isNotBlank()) scope.launch {
                            locStatus = "Cerco il luogo…"
                            banner = a.setMyPlace(locText)
                            refreshLoc()
                        }
                    }, kind = "primary")
                    NButton(if (pick) "Tocca la mappa…" else "Scegli sulla mappa", { pick = !pick })
                    NButton("Aggiorna", { refreshLoc() })
                }
                NDropdown(listOf("auto" to "Automatica: GPS, poi la mia, poi la rete", "manuale" to "Solo quella che ho impostato io"), cfg.locSource, { v ->
                    store.update { it.copy(locSource = v) }
                    refreshLoc()
                })
            }
        }
        GroupTitle("Prova senza GPS")
        NCard {
            RowSwitch("Simula il percorso", "Cammina lungo il tracciato a velocità realistica (utile per provare a casa).", cfg.navSim) { v -> store.update { it.copy(navSim = v) } }
            Divider1()
            FieldRow("Velocità della simulazione") {
                NDropdown(listOf("1" to "Reale (1x)", "3" to "3x", "6" to "6x", "12" to "12x", "25" to "25x"), cfg.navSimSpeed.toString(), { v -> store.update { it.copy(navSimSpeed = v.toInt()) } })
            }
            Divider1()
            FieldRow("Punto di partenza della simulazione", "Vuoto = la tua posizione.") {
                NTextField(cfg.navStart, { v -> store.update { it.copy(navStart = v) } }, "es. Piazza del Duomo, Milano")
            }
        }
        NNote("Mappe, ricerca dei luoghi e percorsi: © OpenStreetMap contributors (Nominatim e OSRM, servizi pubblici gratuiti: usali con moderazione).")
    }
}
