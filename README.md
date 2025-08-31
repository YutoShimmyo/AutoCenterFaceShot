# AutoCenterFaceShot

Android app that shows a live front-camera preview, detects faces with ML Kit, guides the user to center the largest face horizontally, and automatically captures and saves a photo when centered.

## Build & Run (Android 10+)

- Open the project in Android Studio (Giraffe+).
- Ensure `local.properties` points to a valid Android SDK.
- Sync Gradle; the app uses Kotlin and XML layouts (no Compose).
- Run on a physical device (recommended) or emulator with a front camera.
- Requires Android 10+ (API 29+) runtime.
- Grant the Camera permission when prompted.

## Features

- CameraX Preview (front camera) via `PreviewView`.
- ML Kit Face Detection using `ImageAnalysis` (KEEP_ONLY_LATEST).
- Horizontal centering guidance for the largest detected face.
- Auto-capture when centered with a cooldown to prevent rapid shots.
- Saves JPEGs to MediaStore in `Pictures/AutoCenterFaceShot`.
- Minimal overlay UI: centered thin vertical guide + status text.

## Tunable Constants

- `CENTER_TOLERANCE = 0.06` — a face is considered centered if `|x - 0.5| ≤ 0.06` where `x ∈ [0..1]`.
- `CAPTURE_COOLDOWN_MS = 2000` — minimum time between captures in milliseconds.

These are defined in `MainActivity.kt` for simplicity.

## Permissions

- Runtime `CAMERA` permission is requested on API 23+.
- No legacy external storage permissions are requested.

## Storage Path

- Images are saved to MediaStore at `Pictures/AutoCenterFaceShot/IMG_yyyyMMdd_HHmmss.jpg`.
- Uses scoped storage via `RELATIVE_PATH` (Android 10+ only).

## Troubleshooting

- If preview is black: ensure Camera permission is granted and a front camera exists.
- If face count stays zero: test in good lighting; ensure the face is within the frame.
- If Gradle sync issues arise: update Android Studio and use bundled JBR.

## Notes

- Camera bound use cases: `Preview`, `ImageAnalysis`, `ImageCapture`.
- Analyzer is single-threaded with backpressure strategy to avoid buildup.
- UI updates and logs are debounced/throttled to prevent spam.
