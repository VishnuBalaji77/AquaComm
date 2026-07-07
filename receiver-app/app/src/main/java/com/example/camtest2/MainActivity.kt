package com.example.camtest2

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.camtest2.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService

    // Optimization: Store bits in real-time to avoid slow post-processing
    private val capturedBits = StringBuilder()
    private var isRecordingData = false

    private val VALID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 "

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        viewBinding.shutterButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    capturedBits.setLength(0) // Reset data for new recording
                    isRecordingData = true
                    startRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isRecordingData = false
                    stopRecording()

                    val fullResult = capturedBits.toString()
                    val decoded = decodeBits(fullResult)

                    if (decoded.isNotEmpty()) {
                        // Split by "111+ followed by 4 or more 0s"
                        val transmissions = splitTransmissions(decoded)
                        
                        val payload1 = if (transmissions.isNotEmpty()) extractPayload(transmissions[0]) else ""
                        val payload2 = if (transmissions.size > 1) extractPayload(transmissions[1]) else ""

                        val textPart = if (payload2.isNotEmpty()) {
                            bitsToTextRedundant(payload1, payload2)
                        } else if (payload1.isNotEmpty()) {
                            bitsToText(payload1)
                        } else ""

                        // Display decoded bits, character string, and total bit count
                        viewBinding.resultsTextView.text = "$decoded\n\nDecoded Text: $textPart\n\nTotal Bits: ${decoded.length}"
                    } else {
                        viewBinding.resultsTextView.text = if (fullResult.isEmpty()) "" else "No '1' detected"
                    }
                    true
                }
                else -> false
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun splitTransmissions(bits: String): List<String> {
        val result = mutableListOf<String>()
        val pattern = Regex("111+0{4,}")
        val matches = pattern.findAll(bits)
        
        var lastStart = 0
        for (match in matches) {
            // Add the transmission before the gap
            // The match itself includes the 111+ and the 0{4,}.
            // We want everything up to the 111+ part of this match for the first transmission.
            val matchEndSequenceStart = bits.indexOf("111", match.range.first)
            result.add(bits.substring(lastStart, matchEndSequenceStart))
            
            // The next transmission starts after all the zeros in the gap.
            // Find the next '1' after the current match's zeros.
            val nextOne = bits.indexOf('1', match.range.last + 1)
            if (nextOne != -1) {
                lastStart = nextOne
            } else {
                lastStart = bits.length
                break
            }
        }
        
        // Add the last remaining part if any
        if (lastStart < bits.length) {
            val remaining = bits.substring(lastStart)
            // Trim any trailing 111/1111 from the final transmission
            val trimmed = when {
                remaining.endsWith("1111") -> remaining.substring(0, remaining.length - 4)
                remaining.endsWith("111") -> remaining.substring(0, remaining.length - 3)
                else -> remaining
            }
            result.add(trimmed)
        }
        
        return result
    }

    private fun extractPayload(bits: String): String {
        // Find start sequence: after 8 bits or after 1111, whichever is first
        val index1111 = bits.indexOf("1111")
        val startAfter1111 = if (index1111 != -1) index1111 + 4 else Int.MAX_VALUE
        val startIndexCandidate = minOf(8, startAfter1111)
        val startIndex = if (startIndexCandidate > bits.length) bits.length else startIndexCandidate
        
        return if (startIndex < bits.length) bits.substring(startIndex) else ""
    }

    private fun bitsToTextRedundant(bits1: String, bits2: String): String {
        val result = StringBuilder()
        var i = 0
        // Read 8 bits at a time from both
        while (i + 8 <= bits1.length || i + 8 <= bits2.length) {
            val byteStr1 = if (i + 8 <= bits1.length) bits1.substring(i, i + 8) else null
            val byteStr2 = if (i + 8 <= bits2.length) bits2.substring(i, i + 8) else null

            // Stop if either byte is the termination sequence
            if (byteStr1 == "00001111" || byteStr2 == "00001111") break

            val predicted = predictBestChar(byteStr1, byteStr2)
            if (predicted != null) {
                result.append(predicted)
            }
            i += 8
        }
        return result.toString()
    }

    private fun predictBestChar(byteStr1: String?, byteStr2: String?): Char? {
        val b1 = try { byteStr1?.toInt(2) } catch (e: Exception) { null }
        val b2 = try { byteStr2?.toInt(2) } catch (e: Exception) { null }

        var bestChar: Char? = null
        var minDistance = Int.MAX_VALUE

        for (candidate in VALID_CHARS) {
            val targetByte = candidate.code
            val d1 = b1?.let { java.lang.Integer.bitCount(it xor targetByte) } ?: Int.MAX_VALUE
            val d2 = b2?.let { java.lang.Integer.bitCount(it xor targetByte) } ?: Int.MAX_VALUE
            
            val distance = minOf(d1, d2)

            if (distance < minDistance) {
                minDistance = distance
                bestChar = candidate
            }
            if (minDistance == 0) break
        }

        return if (minDistance <= 1) bestChar else null
    }

    private fun bitsToText(bits: String): String {
        val result = StringBuilder()
        var i = 0
        while (i + 8 <= bits.length) {
            val byteStr = bits.substring(i, i + 8)
            if (byteStr == "00001111") break

            try {
                val rawByte = byteStr.toInt(2)
                val predicted = predictChar(rawByte)
                if (predicted != null) {
                    result.append(predicted)
                }
            } catch (e: Exception) {}
            i += 8
        }
        return result.toString()
    }

    private fun predictChar(rawByte: Int): Char? {
        var bestChar: Char? = null
        var minDistance = Int.MAX_VALUE

        for (candidate in VALID_CHARS) {
            val targetByte = candidate.code
            val distance = java.lang.Integer.bitCount(rawByte xor targetByte)

            if (distance < minDistance) {
                minDistance = distance
                bestChar = candidate
            }
            if (minDistance == 0) break
        }

        return if (minDistance <= 1) bestChar else null
    }

    private fun decodeBits(input: String): String {
        val firstOne = input.indexOf('1')
        if (firstOne == -1) return ""
        val s = input.substring(firstOne)

        var i = 0
        while (i < s.length && s[i] == '1') i++
        val len1 = i
        if (len1 == 0) return ""

        val start0 = i
        while (i < s.length && s[i] == '0') i++
        val len0 = i - start0

        if (len0 == 0) {
            val numBits = (s.length.toDouble() / len1).roundToInt()
            return "1".repeat(numBits)
        }

        val result = StringBuilder()
        var currentPos = 0
        while (currentPos < s.length) {
            val char = s[currentPos]
            var runLen = 0
            while (currentPos + runLen < s.length && s[currentPos + runLen] == char) {
                runLen++
            }

            val baseLen = if (char == '1') len1 else len0
            val numBits = (runLen.toDouble() / baseLen).roundToInt()

            repeat(numBits) {
                result.append(char)
            }

            currentPos += runLen
        }

        val decoded = result.toString()
        val lastOne = decoded.lastIndexOf('1')
        return if (lastOne != -1) decoded.substring(0, lastOne + 1) else ""
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 1. Preview
            val previewBuilder = Preview.Builder()
            applyManualSettings(Camera2Interop.Extender(previewBuilder))
            val preview = previewBuilder.build().also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }

            // 2. Video Capture
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            val videoCaptureBuilder = VideoCapture.Builder(recorder)
            applyManualSettings(Camera2Interop.Extender(videoCaptureBuilder))
            videoCapture = videoCaptureBuilder.build()

            // 3. Image Analysis (Real-time processing with averaging)
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { image ->
                if (isRecordingData) {
                    processYUVStrip(image)
                }
                image.close()
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videoCapture, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processYUVStrip(image: ImageProxy) {
        val plane = image.planes[0] // Y (Luminance) plane is brightness
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val midY = height / 2
        val rowStart = midY * rowStride

        val step = 10

        for (i in 0 until width step step) {
            var sumY = 0L
            val end = minOf(i + step, width)
            val count = end - i

            for (x in i until end) {
                val index = rowStart + (x * pixelStride)
                if (index < buffer.capacity()) {
                    sumY += buffer.get(index).toInt() and 0xFF
                }
            }

            if (count > 0) {
                val avgY = sumY / count
                capturedBits.append(if (avgY > 128) "1" else "0")
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun applyManualSettings(extender: Camera2Interop.Extender<*>) {
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            Range(60, 60)
        )
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_OFF
        )
        extender.setCaptureRequestOption(
            CaptureRequest.SENSOR_EXPOSURE_TIME,
            1_000_000L
        )
        extender.setCaptureRequestOption(
            CaptureRequest.SENSOR_SENSITIVITY,
            1600
        )
    }

    private fun startRecording() {
        val videoCapture = this.videoCapture ?: return

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.shutterButton.text = "Recording..."
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            recording?.close()
                            recording = null
                        }
                        viewBinding.shutterButton.text = "Hold to Record"
                    }
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}