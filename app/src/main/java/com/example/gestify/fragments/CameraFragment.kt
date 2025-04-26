package com.example.gestify.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.gestify.ObjectDetectionHelper
import com.example.gestify.SpotifyConnection
import com.example.gestify.databinding.FragmentCameraBinding
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var detectionHelper: ObjectDetectionHelper
    private val inputSize = 640
    private var camera: Camera? = null
    private lateinit var spotifyConnection: SpotifyConnection

    // initial mapping - can def change
    private val gestureToActionMap: Map<String, String> = mapOf(
        "like" to "volumeUp",
        "dislike" to "volumeDown",
        "three" to "rewind",
        "four" to "skip",
        "one" to "play",
        "two_up" to "pause",
        "fist" to "mute",
        "palm" to "unmute"
    )

    private var lastLabel: String? = null
    private var lastLabelTimestamp: Long = 0
    private val labelChangeThreshold = 1000L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        spotifyConnection = SpotifyConnection(requireContext(), binding.tvMusicStatus)
        spotifyConnection.initiateSpotifyLogin(requireActivity())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        detectionHelper = ObjectDetectionHelper(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()

        // request camera permissions if needed
        if (allPermissionsGranted()) {
            binding.viewFinder.post { setupCamera() }
        } else {
            requestPermissions(
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            } catch (e: Exception) {
                Log.e(TAG, "Camera setup failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        // preview
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

        // image analysis
        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 640)) // This helps with performance
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { image ->
                    Log.d(TAG, "Received image for analysis: ${image.width}x${image.height}")
                    processImage(image)
                }
            }

        try {
            // unbind all use cases first
            cameraProvider.unbindAll()

            // select front camera
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            // bind use cases
            camera = cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun processImage(image: ImageProxy) {
        try {
            Log.d(TAG, "Processing image ${image.width}x${image.height}")

            // convert
            val bitmap = toBitmap(image)

            // prepare input buffer
            val inputBuffer = prepareInputBuffer(bitmap)

            // run detection
            val detectionsAndInference = detectionHelper.detectWithDetails(inputBuffer)
            val detections = detectionsAndInference.first
            val inferenceTime = detectionsAndInference.second
            Log.d(TAG, "Processed 640x640 image, detections: ${detections.size}")
            // log detailed results
            val topDetection = detections.maxByOrNull { it.second }

            if (topDetection != null){
                val topDetectionID = topDetection.first
                val topDetectionConfidence = topDetection.second
                val topDetectionLabel = topDetectionID.let { detectionHelper.getGestureLabel(it) }
                updateUiWithDetection(topDetectionLabel, topDetectionConfidence)
                handleDetectionWithSpotify(topDetectionLabel)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Image processing error", e)
        } finally {
            image.close()
            Log.d(TAG, "Image processed and closed")
        }
    }

    fun toBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val width = image.width
        val height = image.height

        // create bitmap and copy pixel data
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        // calculate total required rotation
        val sensorRotation = image.imageInfo.rotationDegrees
        val additionalRotation = 180 // The extra rotation you need
        val totalRotation = (sensorRotation + additionalRotation) % 360

        // create transformation matrix
        val matrix = Matrix().apply {
            // apply mirroring for front camera first
            if (camera?.cameraInfo?.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                postScale(-1f, 1f, width / 2f, height / 2f)
            }

            //  apply the combined rotation
            postRotate(totalRotation.toFloat(), width / 2f, height / 2f)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    private fun prepareInputBuffer(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        return ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
            val pixels = IntArray(inputSize * inputSize)
            resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

            for (pixel in pixels) {
                putFloat(Color.red(pixel) / 255f)
                putFloat(Color.green(pixel) / 255f)
                putFloat(Color.blue(pixel) / 255f)
            }
            rewind()
        }
    }

    private fun logDetections(detections: List<Pair<Int, Float>>) {
        if (detections.isEmpty()) {
            Log.d(TAG, "No detections above confidence threshold")
            return
        }

        // group detections by class
        val grouped = detections.groupBy { it.first }

        Log.d(TAG, "===== Detection Results =====")
        Log.d(TAG, "Total detections: ${detections.size}")

        grouped.forEach { (classId, classDetections) ->
            val label = detectionHelper.getGestureLabel(classId)
            val count = classDetections.size
            val maxConfidence = classDetections.maxOf { it.second }
            val avgConfidence = classDetections.map { it.second }.average()

            Log.d(TAG, "Class: $label (ID: $classId)")
            Log.d(TAG, "  Detections: $count")
            Log.d(TAG, "  Max confidence: $maxConfidence")
            Log.d(TAG, "  Avg confidence: $avgConfidence")

            // log top 3 detections for this class
            classDetections.sortedByDescending { it.second }
                .take(3)
                .forEachIndexed { i, (_, conf) ->
                    Log.d(TAG, "    ${i+1}. Confidence: $conf")
                }
        }

        // log top detection overall
        val topDetection = detections.maxByOrNull { it.second }
        topDetection?.let { (classId, confidence) ->
            val label = detectionHelper.getGestureLabel(classId)
            Log.d(TAG, "Top detection: $label with confidence $confidence")
        }
    }

    private fun updateUiWithDetection(detectionLabel: String, detectionConfidence: Float) {
        activity?.runOnUiThread {
                binding.tvClassificationResult.text =
                    "$detectionLabel (${(detectionConfidence * 100).toInt()}%)"
            } ?: run {
                binding.tvClassificationResult.text = "No gesture detected"
            }
    }

    private fun handleDetectionWithSpotify(detectionLabel: String){
        if (detectionLabel == lastLabel){
            if (System.currentTimeMillis() - lastLabelTimestamp >= labelChangeThreshold) {
                callSpotifyFunction(detectionLabel)
            }
        }else{
            lastLabel = detectionLabel
            lastLabelTimestamp = System.currentTimeMillis()
            callSpotifyFunction(detectionLabel)
        }
    }

    private fun callSpotifyFunction(label: String) {
        val action = gestureToActionMap[label]
        binding.gestureClassified.text = action

        if (spotifyConnection.isSpotifyConnected) {
            when (action) {
                "play" -> spotifyConnection.resumeTrack()
                "pause" -> spotifyConnection.pauseTrack()
                "skip" -> spotifyConnection.skipTrack()
                "rewind" -> spotifyConnection.rewindTrack()
                "volumeUp" -> spotifyConnection.volumeUp()
                "volumeDown" -> spotifyConnection.volumeDown()
                "mute" -> spotifyConnection.mute()
                "unmute" -> spotifyConnection.unmute()
                else -> println("Unknown gesture")
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        cameraExecutor.shutdown()

        super.onDestroyView()
    }

    companion object {
        private const val TAG = "CameraFragment"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA
        )
    }
}