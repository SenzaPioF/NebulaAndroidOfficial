package com.nebula.assistant

import android.app.Application
import com.nebula.assistant.core.Assistant
import com.nebula.assistant.core.ConfigStore
import org.osmdroid.config.Configuration
import java.io.File

class NebulaApp : Application() {
    lateinit var store: ConfigStore
        private set
    lateinit var assistant: Assistant
        private set

    override fun onCreate() {
        super.onCreate()
        val osm = Configuration.getInstance()
        osm.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        osm.userAgentValue = packageName
        osm.osmdroidBasePath = File(cacheDir, "osmdroid").also { it.mkdirs() }
        osm.osmdroidTileCache = File(cacheDir, "osmdroid/tiles").also { it.mkdirs() }
        store = ConfigStore(this)
        assistant = Assistant(this, store)
    }
}
