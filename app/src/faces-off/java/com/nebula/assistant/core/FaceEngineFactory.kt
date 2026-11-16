package com.nebula.assistant.core

import android.content.Context

/** Build senza riconoscimento dei volti (predefinita): la sorveglianza resta disattivata. */
object FaceEngineFactory {
    val available: Boolean = false

    fun create(ctx: Context): FaceEngine? = null
}
