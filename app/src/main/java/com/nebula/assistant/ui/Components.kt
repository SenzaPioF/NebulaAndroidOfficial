package com.nebula.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.assistant.core.DANGER_COLOR
import com.nebula.assistant.core.Palette
import com.nebula.assistant.core.paletteOf

val LocalPal = staticCompositionLocalOf { paletteOf("default") }

@Composable
fun NebulaTheme(pal: Palette, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = pal.accent, onPrimary = pal.bg, secondary = pal.a2, background = pal.bg, onBackground = pal.text,
        surface = pal.panel, onSurface = pal.text, surfaceVariant = pal.panel2, onSurfaceVariant = pal.textDim,
        outline = pal.line, error = DANGER_COLOR,
    )
    CompositionLocalProvider(LocalPal provides pal) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Icona disegnata dai tracciati SVG (le stesse dell'app per Windows). */
@Composable
fun NIcon(name: String, tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val paths = remember(name) {
        (ICON_PATHS[name] ?: emptyList()).mapNotNull {
            try {
                PathParser().parsePathString(it).toPath()
            } catch (e: Exception) {
                null
            }
        }
    }
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            for (p in paths) drawPath(p, tint, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun NCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(p.panel).border(1.dp, p.line, shape), content = content)
}

@Composable
fun Divider1() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPal.current.line))
}

@Composable
fun GroupTitle(text: String) {
    Text(text, color = LocalPal.current.textDim, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 2.dp, top = 6.dp))
}

@Composable
fun NNote(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalPal.current.textDim, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = modifier)
}

@Composable
fun RowSwitch(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (desc.isNotEmpty()) NNote(desc, Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.bg, checkedTrackColor = p.accent, checkedBorderColor = p.accent,
                uncheckedThumbColor = p.textDim, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = p.textDim,
            ),
        )
    }
}

@Composable
fun FieldRow(title: String, desc: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotEmpty()) Text(title, fontWeight = FontWeight.Medium)
        content()
        if (desc.isNotEmpty()) NNote(desc)
    }
}

@Composable
fun NButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: String = "normal", enabled: Boolean = true) {
    val p = LocalPal.current
    val container = if (kind == "primary") p.accent else p.panel2
    val content = if (kind == "primary") p.bg else if (kind == "danger") DANGER_COLOR else p.text
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = p.panel2, disabledContentColor = p.textDim,
        ),
        border = BorderStroke(1.dp, if (kind == "primary") p.accent else p.line),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(text) }
}

@Composable
fun NTextField(
    value: String, onValueChange: (String) -> Unit, placeholder: String = "", modifier: Modifier = Modifier,
    password: Boolean = false, singleLine: Boolean = true, minLines: Int = 1,
) {
    val p = LocalPal.current
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(), singleLine = singleLine, minLines = minLines,
        placeholder = { Text(placeholder, color = p.textDim, fontSize = 14.sp) },
        shape = RoundedCornerShape(8.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.accent, unfocusedBorderColor = p.line, focusedTextColor = p.text, unfocusedTextColor = p.text,
            cursorColor = p.accent, focusedContainerColor = p.panel2, unfocusedContainerColor = p.panel2,
        ),
    )
}

@Composable
fun NDropdown(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: options.firstOrNull()?.second ?: ""
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.panel2).border(1.dp, p.line, RoundedCornerShape(8.dp))
                .clickable { open = true }.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), maxLines = 1)
            Text("▾", color = p.textDim)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(p.panel2)) {
            for ((k, v) in options) {
                DropdownMenuItem(text = { Text(v, color = p.text) }, onClick = {
                    onSelect(k)
                    open = false
                })
            }
        }
    }
}

@Composable
fun NSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>) {
    val p = LocalPal.current
    Slider(
        value = value, onValueChange = onChange, valueRange = range,
        colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.panel2),
    )
}

@Composable
fun Accordion(title: String, text: String) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(if (open) "▾" else "▸", color = p.textDim)
        }
        if (open) NNote(text, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp))
    }
}

/** Pannello che sale dal basso (come i flyout di Windows 11). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = p.bg, contentColor = p.text) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onClose) { NIcon("close", p.textDim, 18.dp) }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    indication = null,
    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
    onClick = onClick,
)
