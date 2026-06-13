# Floating Shortcut (Android)

A floating button that stays on top of every app. Tapping it opens a set of shortcuts
arranged in a circle around the button. Included shortcuts are Screenshot, Settings,
Home, and Close. You can drag the button anywhere on the screen.

## How it works

The app relies on three Android mechanisms:

1. **Draw over other apps** (`SYSTEM_ALERT_WINDOW`). This lets the button appear on top
   of other applications. The user must grant this manually in system settings the first
   time, which the app opens automatically.
2. **Foreground service**. The button is hosted by a service so it survives when you leave
   the app. A small persistent notification is required by Android for this.
3. **Screen capture** (`MediaProjection`). The screenshot shortcut needs the user to allow
   screen recording once per session. The app asks for this on startup.

The circular menu is built by placing each item view around the button using trigonometry,
then animating the items outward from the center.

## Files

- `AndroidManifest.xml` declares permissions and the service.
- `MainActivity.kt` requests the permissions and starts the service.
- `FloatingService.kt` hosts the draggable button and builds the radial menu.
- `ScreenshotHelper.kt` captures a frame and saves it to the gallery.

## Building it

1. Open Android Studio and create a new **Empty Activity** project. Set the package name
   to `com.example.floatingshortcut` and choose **Kotlin**.
2. Replace the generated files with the ones in this project:
   - Copy the four `.kt` files into `app/src/main/java/com/example/floatingshortcut/`.
   - Replace `app/src/main/AndroidManifest.xml`.
   - Add `app/src/main/res/values/themes.xml` (or merge the theme into your existing one).
   - Replace the contents of `app/build.gradle` with the provided version, or merge the
     `dependencies` and SDK settings into yours.
3. Let Gradle sync, then run the app on a device or emulator running Android 8.0 or newer.

## Using it

1. Launch the app and tap **Start Floating Button**.
2. Approve "Display over other apps" when the system screen opens, then return and tap
   start again if needed.
3. Approve the screen capture prompt if you want the screenshot shortcut to work.
4. The app moves to the background and the floating button appears. Tap it to open the
   circular menu. Drag it to reposition.

## Adding your own shortcuts

Open `FloatingService.kt` and edit the `menuItems()` function. Each entry is a label, an
icon, a color, and an action. To launch a specific app, use its package name:

```kotlin
MenuItem("Camera", android.R.drawable.ic_menu_camera, Color.BLUE) {
    val launch = packageManager.getLaunchIntentForPackage("com.android.camera")
    launch?.let { startActivity(it.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }
}
```

## Notes and limits

- Screenshots include only the screen content captured at that moment. The button is
  hidden briefly during capture so it does not appear in the image.
- Screen capture consent lasts for the session. If the service is killed by the system,
  reopen the app to grant it again.
- Some manufacturers (for example certain Xiaomi or Huawei devices) require an extra
  step to allow background pop-ups or overlays. Check the app permissions if the button
  does not appear.
