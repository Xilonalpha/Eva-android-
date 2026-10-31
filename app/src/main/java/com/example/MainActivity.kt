package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.eva1.companion.EvaViewModel
import com.eva1.companion.ui.MainScreen
import com.eva1.companion.ui.theme.EVA1Theme

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.compose.runtime.DisposableEffect
import com.eva1.companion.engines.MediaProjectionRequester
import com.eva1.companion.engines.MediaProjectionVisionEngine

class MainActivity : ComponentActivity() {
    private val viewModel: EvaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mediaProjectionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    com.eva1.companion.engines.EvaMediaProjectionService.startServiceWithProjection(
                        this@MainActivity,
                        result.resultCode,
                        result.data!!
                    )
                    MediaProjectionRequester.onProjectionResult?.invoke(result.resultCode, result.data!!)
                }
                val pendingPkg = MediaProjectionRequester.consumePendingPackageToLaunch()
                if (!pendingPkg.isNullOrBlank() && pendingPkg != packageName) {
                    window.decorView.postDelayed({
                        try {
                            val launchIntent = packageManager.getLaunchIntentForPackage(pendingPkg)?.apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            }
                            if (launchIntent != null) {
                                startActivity(launchIntent)
                            }
                        } catch (_: Exception) {}
                    }, 220L)
                }
            }

            fun triggerMediaProjectionDialog() {
                if (MediaProjectionVisionEngine.isMediaProjectionActive.value) {
                    val pendingPkg = MediaProjectionRequester.consumePendingPackageToLaunch()
                    if (!pendingPkg.isNullOrBlank() && pendingPkg != packageName) {
                        try {
                            packageManager.getLaunchIntentForPackage(pendingPkg)?.apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            }?.let { startActivity(it) }
                        } catch (_: Exception) {}
                    }
                    return
                }
                try {
                    if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                    } else {
                        // Dacă cererea vine din Panoul Plutitor (Floating HUD) în timp ce aplicația e în background,
                        // aducem MainActivity în prim-plan pentru ca Android 10-15 să permită afișarea dialogului de captură.
                        val fgIntent = android.content.Intent(this@MainActivity, MainActivity::class.java).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("request_media_projection", true)
                        }
                        startActivity(fgIntent)
                    }
                } catch (_: Exception) {}
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { _ ->
                // Lansăm dialogul MediaProjection DUPĂ ce utilizatorul a închis dialogul de permisiuni runtime,
                // evitând suprapunerea a două dialoguri de sistem!
                triggerMediaProjectionDialog()
            }

            DisposableEffect(Unit) {
                MediaProjectionRequester.onRequestMediaProjection = {
                    triggerMediaProjectionDialog()
                }
                onDispose {
                    MediaProjectionRequester.onRequestMediaProjection = null
                }
            }

            LaunchedEffect(Unit) {
                val candidates = mutableListOf(
                    Manifest.permission.RECORD_AUDIO
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    candidates.add(Manifest.permission.BLUETOOTH_SCAN)
                    candidates.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    candidates.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    candidates.add(Manifest.permission.POST_NOTIFICATIONS)
                    candidates.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                }
                val missing = candidates.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) {
                    permissionLauncher.launch(missing.toTypedArray())
                } else {
                    // Permisiunile sunt deja acordate -> solicităm direct MediaProjection dacă nu e activ
                    triggerMediaProjectionDialog()
                }
            }

            EVA1Theme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("request_media_projection", false)) {
            intent.removeExtra("request_media_projection")
            window.decorView.postDelayed({
                MediaProjectionRequester.onRequestMediaProjection?.invoke()
            }, 200L)
        }
    }
}
