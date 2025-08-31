package com.example.autocenterfaceshot

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView

    private val cameraPermission = Manifest.permission.CAMERA
    private val requestCodeCamera = 1001

    private var detector: FaceDetector? = null
    private var imageCapture: ImageCapture? = null

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private lateinit var mainExecutor: Executor

    // Tunable constants
    private val CENTER_TOLERANCE = 0.06f
    private val CAPTURE_COOLDOWN_MS = 2000L
    private val UI_DEBOUNCE_MS = 150L
    private val LOG_THROTTLE_MS = 500L

    private var lastUiUpdate = 0L
    private var lastLog = 0L
    private var lastCapture = 0L
    private var isCapturing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        mainExecutor = ContextCompat.getMainExecutor(this)

        setupFaceDetector()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(cameraPermission), requestCodeCamera)
        }
    }

    private fun setupFaceDetector() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .build()
        detector = FaceDetection.getClient(options)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, cameraPermission) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { setupAnalyzer(it) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    analysis,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, mainExecutor)
    }

    private fun setupAnalyzer(analysis: ImageAnalysis) {
        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return@setAnalyzer
            }

            val rotation = imageProxy.imageInfo.rotationDegrees
            val input = InputImage.fromMediaImage(mediaImage, rotation)
            val currentDetector = detector
            if (currentDetector == null) {
                imageProxy.close()
                return@setAnalyzer
            }

            currentDetector.process(input)
                .addOnSuccessListener { faces ->
                    handleFaces(imageProxy, faces)
                }
                .addOnFailureListener { err ->
                    Log.e(TAG, "Face detection error", err)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun handleFaces(imageProxy: ImageProxy, faces: List<Face>) {
        val now = System.currentTimeMillis()

        if (now - lastLog > LOG_THROTTLE_MS) {
            Log.d(TAG, "Faces detected: ${faces.size}")
            lastLog = now
        }

        if (faces.isEmpty()) {
            maybeUpdateStatus("Align face in center")
            return
        }

        // Select largest face by area
        val largest = faces.maxBy { it.boundingBox.width() * it.boundingBox.height() }

        // Compute normalized center X in [0..1], corrected for rotation and mirroring
        val rotation = imageProxy.imageInfo.rotationDegrees
        val imageWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val centerX = largest.boundingBox.centerX().toFloat()
        var xNorm = (centerX / imageWidth.toFloat()).coerceIn(0f, 1f)

        // Front camera preview is mirrored; flip x to match what user sees
        xNorm = 1f - xNorm

        val delta = kotlin.math.abs(xNorm - 0.5f)

        if (delta <= CENTER_TOLERANCE) {
            maybeUpdateStatus("Centered ✓")
            maybeCapture()
        } else {
            val dir = if (xNorm < 0.5f) "Move right" else "Move left"
            maybeUpdateStatus(dir)
        }
    }

    private fun maybeUpdateStatus(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastUiUpdate < UI_DEBOUNCE_MS) return
        lastUiUpdate = now
        runOnUiThread {
            statusText.text = text
        }
    }

    private fun maybeCapture() {
        val captureUseCase = imageCapture ?: return
        val now = System.currentTimeMillis()
        if (isCapturing || now - lastCapture < CAPTURE_COOLDOWN_MS) return
        isCapturing = true
        lastCapture = now
        runOnUiThread { statusText.text = "Capturing..." }

        val resolver = contentResolver
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${name}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AutoCenterFaceShot")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            resolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        captureUseCase.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    runOnUiThread { statusText.text = "Saved ✓" }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    Log.e(TAG, "Save failed", exception)
                    runOnUiThread { statusText.text = "Save failed" }
                }
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodeCamera) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
        analysisExecutor.shutdown()
    }

    companion object {
        private const val TAG = "AutoCenterFaceShot"
    }
}
