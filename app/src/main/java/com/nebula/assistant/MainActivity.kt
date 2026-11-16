package com.nebula.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.nebula.assistant.ui.ActivityActions
import com.nebula.assistant.ui.MainScreen
import java.io.File

class MainActivity : ComponentActivity(), ActivityActions {
    private var permCallback: ((Boolean) -> Unit)? = null

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        permCallback?.invoke(res.values.all { it })
        permCallback = null
    }

    private val googleLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        (application as NebulaApp).assistant.googleAuth.onActivityResult(res.data)
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val a = (application as NebulaApp).assistant
        for (u in uris) {
            try {
                contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            a.attach(u)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NebulaApp
        app.assistant.googleAuth.launcher = { sender ->
            googleLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
        setContent { MainScreen(app.assistant, app.store, this) }
    }

    override fun onStart() {
        super.onStart()
        val a = (application as NebulaApp).assistant
        a.gps.start()
        a.syncListening()
    }

    override fun askPermissions(perms: List<String>, onResult: (Boolean) -> Unit) {
        val need = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) {
            onResult(true)
            return
        }
        permCallback = onResult
        permLauncher.launch(need.toTypedArray())
    }

    override fun hasPermission(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    override fun pickFiles() {
        filePicker.launch(arrayOf("*/*"))
    }

    override fun openFile(path: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", File(path))
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, contentResolver.getType(uri) ?: "text/plain")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, "Apri con"))
        } catch (e: Exception) {
        }
    }

    override fun shareFile(path: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", File(path))
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, "Condividi"))
        } catch (e: Exception) {
        }
    }

    override fun openUri(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
        }
    }

    override fun notificationsPermissionNeeded(): Boolean = Build.VERSION.SDK_INT >= 33
}
