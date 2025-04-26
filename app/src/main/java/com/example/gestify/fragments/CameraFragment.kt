package com.example.gestify.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
        "palm" to "unmute",
        "middle_finger" to "surprise"
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

            val topDetection = detections.maxByOrNull { it.second }
            if (topDetection != null) {
                updateUiWithDetection(topDetection)
                handleDetectionWithSpotify(topDetection)
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

    private fun updateUiWithDetection(topDetection: Pair<Int, Float>) {
        activity?.runOnUiThread {
            topDetection.let { (classId, confidence) ->
                val label = detectionHelper.getGestureLabel(classId)
                binding.tvClassificationResult.text =
                    "$label (${(confidence * 100).toInt()}%)"
            }
        }
    }

    private fun handleDetectionWithSpotify(topDetection: Pair<Int, Float>){
        val topDetectionLabel = detectionHelper.getGestureLabel(topDetection.first)

        if (topDetectionLabel == lastLabel){
            if (System.currentTimeMillis() - lastLabelTimestamp >= labelChangeThreshold) {
                callSpotifyFunction(topDetectionLabel)
            }
        }else{
            lastLabel = topDetectionLabel
            lastLabelTimestamp = System.currentTimeMillis()
            callSpotifyFunction(topDetectionLabel)
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
                "surprise" -> spotifyConnection.surprise()
                else -> println("Unknown Gesture")
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