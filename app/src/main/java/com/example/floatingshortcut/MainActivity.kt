package com.example.floatingshortcut

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    // Launcher that asks the system for screen-capture consent.
    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Hand the consent token to the service so screenshots work.
                startFloatingService(result.resultCode, result.data)
            } else {
                // The user declined screen capture. Start without screenshot support.
                startFloatingService(Activity.RESULT_CANCELED, null)
            }
        }

    // Launcher that asks the user to allow drawing over other apps.
    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // We do not act on the result directly; the button re-checks on press.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // A simple programmatic layout so no XML layout file is needed.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Floating Shortcut"
            textSize = 22f
        }

        val info = TextView(this).apply {
            text = "Step 1: Allow the app to draw over other apps.\n" +
                    "Step 2: Start the floating button.\n\n" +
                    "Tap the button anywhere on screen to open the circular menu. " +
                    "Drag the button to move it."
            textSize = 15f
            setPadding(0, 32, 0, 48)
        }

        val startButton = Button(this).apply {
            text = "Start Floating Button"
            setOnClickListener { handleStart() }
        }

        root.addView(title)
        root.addView(info)
        root.addView(startButton)
        setContentView(root)

        requestNotificationPermissionIfNeeded()
    }

    private fun handleStart() {
        if (!Settings.canDrawOverlays(this)) {
            // Send the user to the system screen to grant the overlay permission.
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayLauncher.launch(intent)
            return
        }
        // Permission is granted, so ask for screen-capture consent next.
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startFloatingService(resultCode: Int, data: Intent?) {
        val intent = Intent(this, FloatingService::class.java).apply {
            putExtra(FloatingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(FloatingService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(this, intent)
        // Send the app to the background so the floating button is visible.
        moveTaskToBack(true)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }
    }
}
