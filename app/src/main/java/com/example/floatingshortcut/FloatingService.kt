package com.example.floatingshortcut

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class FloatingService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "floating_shortcut_channel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: ImageView
    private lateinit var buttonParams: WindowManager.LayoutParams

    private var menuView: FrameLayout? = null
    private var isMenuOpen = false

    // Screenshot support, only available if the user granted screen capture.
    private var screenshotHelper: ScreenshotHelper? = null

    // State used while dragging the button.
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        addFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Set up screenshot capture if the user granted consent in MainActivity.
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Service.STOP_FOREGROUND_REMOVE)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        }
        if (resultCode != null && resultCode == android.app.Activity.RESULT_OK && resultData != null) {
            screenshotHelper = ScreenshotHelper(this, resultCode, resultData)
        }
        return START_STICKY
    }

    // Build the round floating button and add it to the window.
    private fun addFloatingButton() {
        floatingButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_add)
            background = circleDrawable(Color.parseColor("#3F51B5"))
            val pad = dp(14)
            setPadding(pad, pad, pad, pad)
            setColorFilter(Color.WHITE)
        }

        val size = dp(56)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        buttonParams = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        floatingButton.setOnTouchListener { _, event -> handleTouch(event) }
        windowManager.addView(floatingButton, buttonParams)
    }

    // Distinguish a tap from a drag so moving the button does not open the menu.
    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = buttonParams.x
                initialY = buttonParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (hypot(dx, dy) > dp(8)) {
                    isDragging = true
                    if (isMenuOpen) closeMenu()
                    buttonParams.x = initialX + dx.toInt()
                    buttonParams.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(floatingButton, buttonParams)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    if (isMenuOpen) closeMenu() else openMenu()
                }
                return true
            }
        }
        return false
    }

    // The shortcut items shown in the circle. Add or change entries here.
    private fun menuItems(): List<MenuItem> = listOf(
        MenuItem("Screenshot", android.R.drawable.ic_menu_camera, Color.parseColor("#E91E63")) {
            takeScreenshot()
        },
        MenuItem("Settings", android.R.drawable.ic_menu_manage, Color.parseColor("#009688")) {
            openSettings()
        },
        MenuItem("Home", android.R.drawable.ic_menu_revert, Color.parseColor("#FF9800")) {
            goHome()
        },
        MenuItem("Close", android.R.drawable.ic_menu_close_clear_cancel, Color.parseColor("#607D8B")) {
            stopSelf()
        }
    )

    // Add a full-screen transparent overlay and animate the items outward in a circle.
    private fun openMenu() {
        if (isMenuOpen) return
        isMenuOpen = true

        val container = FrameLayout(this)
        // Tapping the empty area closes the menu.
        container.setOnClickListener { closeMenu() }

        val metrics = resources.displayMetrics
        val centerX = buttonParams.x + dp(28)
        val centerY = buttonParams.y + dp(28)
        val radius = dp(96).toFloat()
        val items = menuItems()

        // Spread the items across a quarter to half circle, biased away from screen edges.
        val onLeftHalf = centerX < metrics.widthPixels / 2
        val startAngle = if (onLeftHalf) -45.0 else 135.0
        val sweep = 180.0
        val step = if (items.size > 1) sweep / (items.size - 1) else 0.0

        items.forEachIndexed { index, item ->
            val angle = Math.toRadians(startAngle + step * index)
            val targetX = centerX + radius * cos(angle).toFloat() - dp(24)
            val targetY = centerY + radius * sin(angle).toFloat() - dp(24)

            val itemView = ImageView(this).apply {
                setImageResource(item.icon)
                background = circleDrawable(item.color)
                val pad = dp(10)
                setPadding(pad, pad, pad, pad)
                setColorFilter(Color.WHITE)
                setOnClickListener {
                    closeMenu()
                    item.action()
                }
            }
            val lp = FrameLayout.LayoutParams(dp(48), dp(48))
            itemView.layoutParams = lp
            // Start collapsed at the button, then animate to the target position.
            itemView.x = centerX - dp(24).toFloat()
            itemView.y = centerY - dp(24).toFloat()
            itemView.alpha = 0f
            container.addView(itemView)

            itemView.animate()
                .x(targetX)
                .y(targetY)
                .alpha(1f)
                .setStartDelay(index * 30L)
                .setDuration(180)
                .start()
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(container, params)
        menuView = container
    }

    private fun closeMenu() {
        isMenuOpen = false
        menuView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        menuView = null
    }

    // --- Shortcut actions ---

    private fun takeScreenshot() {
        val helper = screenshotHelper
        if (helper == null) {
            Toast.makeText(this, "Screen capture was not granted. Restart the app to enable it.", Toast.LENGTH_LONG).show()
            return
        }
        // Hide the button briefly so it does not appear in the capture.
        floatingButton.visibility = View.GONE
        handler.postDelayed({
            helper.capture { path ->
                handler.post {
                    floatingButton.visibility = View.VISIBLE
                    val msg = if (path != null) "Screenshot saved" else "Screenshot failed"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }, 150)
    }

    private fun openSettings() {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open Settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    // --- Helpers ---

    private fun circleDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Floating Shortcut",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val notification: Notification = Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("Floating Shortcut is running")
            .setContentText("Tap the floating button to open the menu")
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeMenu()
        screenshotHelper?.release()
        try { windowManager.removeView(floatingButton) } catch (_: Exception) {}
    }

    data class MenuItem(
        val label: String,
        val icon: Int,
        val color: Int,
        val action: () -> Unit
    )
}
