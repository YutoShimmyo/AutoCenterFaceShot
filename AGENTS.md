# AGENTS.md

## Role of Coding Agent
You are the **exclusive coding agent** for this repository.  
The human user will only manage tasks, review PRs, and merge.  
All implementation, configuration, and documentation must be done by you.

## General Rules
- Always read and follow this AGENTS.md before executing any task.
- Do not output pseudocode or diffs. Always output **entire, compile-ready files** with correct paths.
- Use **Kotlin** for app code.
- This project is an **Android Application** built with **Gradle Kotlin DSL** and **Android Studio**.
- UI is based on **XML layouts** (not Compose).
- Each task corresponds to one **feature branch** + **PR**.
- Branch naming: `feature/<short-slug>` (e.g. `feature/mlkit-face-detection`).
- Commit style: **Conventional Commits** (e.g. `feat: add ML Kit face detection`).
- Every PR must include:
    - Title
    - Description with checklist of acceptance criteria
    - Notes on testing steps

## Output Format (Required for Every Task)
When you finish a task, output:
1. Branch name
2. List of changed/added files with paths
3. Full contents of each changed/added file (no diffs)
4. Run/sync/test notes
5. One-line commit message
6. Pull Request title + body (follow the rules above)

## Runtime Environment
- Local dev provides Android SDK (`local.properties` ensures `sdk.dir`).
- Gradle JDK: Android Studio bundled JBR.
- Scoped storage only; do not request legacy WRITE permissions.
- Always request CAMERA permission at runtime (API 23+).

---

## Task Backlog

### Card 1 — Add CameraX Preview
**Acceptance Criteria**
- Add CameraX BOM and dependencies to `app/build.gradle.kts`:
    - `implementation(platform("androidx.camera:camera-bom:<stable>"))`
    - `implementation("androidx.camera:camera-core")`
    - `implementation("androidx.camera:camera-camera2")`
    - `implementation("androidx.camera:camera-lifecycle")`
    - `implementation("androidx.camera:camera-view")`
- Update `app/src/main/AndroidManifest.xml`:
    - Add `android.permission.CAMERA`
    - Add `<uses-feature android:name="android.hardware.camera" android:required="false" />`
- Add layout `app/src/main/res/layout/activity_main.xml` with a full-screen `androidx.camera.view.PreviewView`
- Update `MainActivity.kt`:
    - Request CAMERA runtime permission (API 23+)
    - Initialize `ProcessCameraProvider`, create `Preview`, set `PreviewView.surfaceProvider`
    - Bind to `CameraSelector.DEFAULT_FRONT_CAMERA`
- The app builds and shows a live front-camera preview on device

---

### Card 2 — Integrate ML Kit Face Detection
**Acceptance Criteria**
- Add dependency: `com.google.mlkit:face-detection:16.1.6` (or latest stable).
- Add CameraX `ImageAnalysis` use case with `STRATEGY_KEEP_ONLY_LATEST`.
- Run ML Kit face detection on frames, log number of faces (throttled).
- Preview must keep working.

---

### Card 3 — Horizontal Centering Check
**Acceptance Criteria**
- Select largest detected face.
- Normalize horizontal center X ∈ [0..1].
- Centered if |x - 0.5| ≤ 0.06.
- Add `TextView` to `activity_main.xml`.
- Show guidance: "Centered ✓" / "Move left" / "Move right".
- Debounce updates to avoid UI spam.

---

### Card 4 — Auto Capture When Centered
**Acceptance Criteria**
- Add CameraX `ImageCapture` use case.
- When centered, capture photo with 2s cooldown.
- Update status text: "Capturing..." → "Saved ✓"/"Save failed".
- Must not block analyzer thread.

---

### Card 5 — Save with MediaStore
**Acceptance Criteria**
- Save JPEGs via `MediaStore` scoped storage.
- Path: `Pictures/AutoCenterFaceShot`.
- Filename: timestamp-based (e.g., `IMG_yyyyMMdd_HHmmss.jpg`).
- On success: "Saved ✓"; on failure: "Save failed".

---

### Card 6 — Minimal UI (Guideline + Status)
**Acceptance Criteria**
- Overlay thin vertical line (1–2dp, semi-transparent white) centered.
- Style status `TextView` for readability (padding, shadow, size).
- Keep PreviewView working.

---

### Card 7 — README & Finalize Config
**Acceptance Criteria**
- Add README.md with:
    - Build/run steps
    - Features
    - Tunable constants (CENTER_TOLERANCE=0.06, CAPTURE_COOLDOWN_MS=2000)
    - Notes on permissions, storage path, troubleshooting
- Verify Manifest & Gradle: remove unused libs, ensure sensible minSdk and BOM.
