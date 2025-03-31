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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var detectionHelper: ObjectDetectionHelper
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
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 640))
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
            cameraProvider.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

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

            // Convert to bitmap and process
            val bitmap = toBitmap(image)
            val detections = detectionHelper.detectWithDetails(bitmap)
            Log.d(TAG, "Processed 640x640 image, detections: ${detections.size}")

            logDetections(detections)
            updateUiWithDetection(detections)
            handleDetectionWithSpotify(detections)

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

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        val sensorRotation = image.imageInfo.rotationDegrees
        val additionalRotation = 180
        val totalRotation = (sensorRotation + additionalRotation) % 360

        val matrix = Matrix().apply {
            if (camera?.cameraInfo?.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                postScale(-1f, 1f, width / 2f, height / 2f)
            }
            postRotate(totalRotation.toFloat(), width / 2f, height / 2f)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    private fun logDetections(detections: List<Pair<Int, Float>>) {
        if (detections.isEmpty()) {
            Log.d(TAG, "No detections above confidence threshold")
            return
        }

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

            classDetections.sortedByDescending { it.second }
                .take(3)
                .forEachIndexed { i, (_, conf) ->
                    Log.d(TAG, "    ${i+1}. Confidence: $conf")
                }
        }

        val topDetection = detections.maxByOrNull { it.second }
        topDetection?.let { (classId, confidence) ->
            val label = detectionHelper.getGestureLabel(classId)
            Log.d(TAG, "Top detection: $label with confidence $confidence")
        }
    }

    private fun updateUiWithDetection(detections: List<Pair<Int, Float>>) {
        activity?.runOnUiThread {
            val topDetection = detections.maxByOrNull { it.second }
            topDetection?.let { (classId, confidence) ->
                val label = detectionHelper.getGestureLabel(classId)
                binding.tvClassificationResult.text =
                    "$label (${(confidence * 100).toInt()}%)"
            } ?: run {
                binding.tvClassificationResult.text = "No gesture detected"
            }
        }
    }

    private fun handleDetectionWithSpotify(detections: List<Pair<Int, Float>>){
        val topDetection = detections.maxByOrNull { it.second }
        topDetection?.let { (classId, confidence) ->
            val label = detectionHelper.getGestureLabel(classId)
            if (label == lastLabel){
                if (System.currentTimeMillis() - lastLabelTimestamp >= labelChangeThreshold) {
                    callSpotifyFunction(label)
                }
            }else{
                lastLabel = label
                lastLabelTimestamp = System.currentTimeMillis()
                callSpotifyFunction(label)
            }
        } ?: run {
            Log.d(TAG, "Not calling Spotify - no gesture")
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
        detectionHelper.close()
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