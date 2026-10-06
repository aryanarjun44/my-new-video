# Video Inject: LSPosed module (test aid for your own app)

Plays a video of your choice in place of the camera **preview** inside one app you scope the module to,
similar to what OBS Virtual Camera does on desktop. Your app does **not** need any change.

## Build
Easiest: upload `VideoInjectModule.zip` and `.github/workflows/build.yml` to a GitHub repository; the
"Actions" tab builds `app-debug.apk` for you. Or open the folder in Android Studio and run Build > Build APK.

## Use (one-time setup)
1. Install the APK. In LSPosed: Modules > Video Inject > enable, and tick **only your own app** in the scope.
2. Open Video Inject. Type your app's package name, tap **Choose video**, then **Apply to app**
   and tap **Allow** on the root (Magisk/KernelSU) popup.
3. Swipe your app away from recent apps (first time only), open it, go to its camera screen.
4. Tap **Check setup** in Video Inject. It shows whether the video is in place and what the hook did last.

## Later
- Same video plays again every time the app opens the camera.
- New video: Choose video > Apply to app > open the camera screen again.
- Switches (Inject / Loop) re-apply automatically. "Remove video from app" cleans up.
- Closing or force-stopping your app deletes nothing. Never use "Clear data".

## How it works
- **Apply to app** uses root to copy the video and a tiny settings file into the target app's own
  `files/` folder (`vinject.mp4`, `vinject.cfg`). Nothing inside the app's code or manifest is touched.
- `HookEntry` hooks Camera1 (`setPreviewTexture` / `setPreviewDisplay`) and Camera2/CameraX
  (`createCaptureSession*`, plus `CaptureRequest.Builder.addTarget`) inside the scoped app.
- The real preview surface is given to a `MediaPlayer`; the camera is pointed at a throw-away dummy surface.
- The hook writes a one-line `vinject.status` file in the app folder; **Check setup** reads it back.
- If the video or settings file is missing, the hook does nothing and the real camera is used.

## Limitations
- Needs a rooted phone (Magisk or KernelSU) with LSPosed. First user (profile) only.
- Preview only: photo capture, `ImageReader`/frame analysis and `Camera.PreviewCallback` still get real camera data.
- The first valid surface passed to the capture session is treated as the preview.
- Front-camera mirroring and sensor rotation are not emulated. Audio is muted.
- If the app is uninstalled or its data is cleared, tap Apply again.
- Not compiled or run on a device by the author: if the GitHub build or a feature fails, send the error text.
