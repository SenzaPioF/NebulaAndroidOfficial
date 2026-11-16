package com.nebula.assistant.core

import androidx.compose.ui.graphics.Color

data class Palette(
    val accent: Color,
    val dim: Color,
    val a2: Color,
    val a3: Color,
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val line: Color,
    val text: Color,
    val textDim: Color,
)

private fun hx(s: String): Color = Color(android.graphics.Color.parseColor(s))

private fun pal(
    accent: String, dim: String, a2: String, a3: String, bg: String,
    panel: String, panel2: String, line: String, text: String, textDim: String,
): Palette = Palette(hx(accent), hx(dim), hx(a2), hx(a3), hx(bg), hx(panel), hx(panel2), hx(line), hx(text), hx(textDim))

val OK_COLOR: Color = hx("#6ccb5f")
val WARN_COLOR: Color = hx("#fcc84a")
val DANGER_COLOR: Color = hx("#ff99a4")

val THEMES: Map<String, Palette> = linkedMapOf(
    "default" to pal("#60cdff", "#0f5a8f", "#99ebff", "#c7f0ff", "#202020", "#2b2b2b", "#343434", "#3f3f3f", "#f3f3f3", "#b4b4b4"),
    "jarvis" to pal("#ff3227", "#7a1810", "#ffc72c", "#ffdb70", "#120806", "#1c0f0a", "#241309", "#4a2410", "#ffe9c7", "#a67856"),
    "fire" to pal("#ff6a00", "#8a2e00", "#ffb100", "#ffd580", "#170a02", "#241005", "#331705", "#5c2c08", "#ffe6c2", "#b8834c"),
    "water" to pal("#29b6f6", "#0f5470", "#7fe3ff", "#c8f4ff", "#030f16", "#071e29", "#0b2c3c", "#124a63", "#dff6ff", "#6fa8c2"),
    "air" to pal("#b9f3ff", "#4f8a99", "#eafffe", "#ffffff", "#061217", "#0c2029", "#123039", "#2c5b66", "#eafcff", "#86b9c4"),
    "earth" to pal("#b98a3d", "#5c421a", "#d9b46a", "#efd9a0", "#120d05", "#201709", "#2c1f0c", "#513a15", "#f2e3c2", "#b39865"),
    "life" to pal("#5fd45f", "#245c24", "#a6ec8a", "#d6ffc2", "#061206", "#0c200c", "#123012", "#275c27", "#e5ffdf", "#86c485"),
    "magic" to pal("#c25bd6", "#5c2166", "#ff8ae0", "#ffc2f0", "#120616", "#200c26", "#2c1233", "#5c2166", "#f6e0ff", "#b483c4"),
    "tech" to pal("#ffcf33", "#7a5c10", "#fff08a", "#fff9cc", "#171404", "#262008", "#332b0b", "#5c4c14", "#fff3d0", "#c2ab6f"),
    "undead" to pal("#8e6ac0", "#362654", "#b89aef", "#d9c8ff", "#0a0812", "#140f22", "#1c1530", "#3a2a55", "#e6dbff", "#9080b3"),
)

val THEME_LABELS: List<Pair<String, String>> = listOf(
    "default" to "Predefinito (Windows 11 scuro)",
    "jarvis" to "Classico (rosso Jarvis)",
    "fire" to "Fuoco",
    "water" to "Acqua",
    "air" to "Aria",
    "earth" to "Terra",
    "life" to "Vita",
    "magic" to "Magia",
    "tech" to "Tech",
    "undead" to "Non-morti",
)

fun paletteOf(key: String): Palette = THEMES[key] ?: THEMES.getValue("default")
