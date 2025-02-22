package com.example.gestify.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

import com.example.gestify.SpotifyConnection

import androidx.navigation.Navigation

import com.example.gestify.GestureClassifierHelper
import com.example.gestify.R
import com.example.gestify.databinding.FragmentCameraBinding
import org.tensorflow.lite.task.vision.classifier.Classifications
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment(), GestureClassifierHelper.ClassifierListener {

    companion object {
        private const val TAG = "Gesture Classifier"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private var lastLabel: String? = null
    private var lastLabelTimestamp: Long = 0
    private val labelChangeThreshold = 3000L // 3 seconds

    private lateinit var spotifyConnection: SpotifyConnection

    private lateinit var gestureClassifierHelper: GestureClassifierHelper
    private lateinit var bitmapBuffer: Bitmap

    private val gestureToActionMap: Map<String, String> = mapOf(
        "up" to "volumeUp",
        "down" to "volumeDown",
        "left" to "rewind",
        "right" to "skip",
        "leftclick" to "play",
        "rightclick" to "pause",
        "scrollup" to "mute",
        "scrolldown" to "unmute"
    )


    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    override fun onResume() {
        super.onResume()

        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(),
                R.id.fragment_container
            )
                .navigate(CameraFragmentDirections.actionCameraToPermissions())
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        spotifyConnection = SpotifyConnection(requireContext(), fragmentCameraBinding.tvMusicStatus)
        spotifyConnection.initiateSpotifyLogin(requireActivity())

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gestureClassifierHelper =
            GestureClassifierHelper(
                context = requireContext(),
                gestureClassifierListener = this
            )


        cameraExecutor = Executors.newSingleThreadExecutor()

        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }



    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider =
            cameraProvider
                ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector - makes assumption that we're only using the front
        // camera
        val cameraSelector =
            CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        if (!::bitmapBuffer.isInitialized) {
                            // The image rotation and RGB image buffer are initialized only once
                            // the analyzer has started running
                            bitmapBuffer = Bitmap.createBitmap(
                                image.width,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )
                        }

                        classifyImage(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun classifyImage(image: ImageProxy) {
        // Copy out RGB bits to the shared bitmap buffer
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        // Because we use front camera so we need flip the bitmap before
        // classify
        val flipMatrix = Matrix().apply {
            postScale(
                -1f,
                1f,
                bitmapBuffer.width / 2f,
                bitmapBuffer.height / 2f
            )
        }

        val flippedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0,
            bitmapBuffer.width, bitmapBuffer.height, flipMatrix, true
        )

        // calculate rotation degrees after flip image
        val imageRotation = 180 - image.imageInfo.rotationDegrees
        // Pass Bitmap and rotation to the gesture classifier helper for
        // processing and classification
        gestureClassifierHelper.classify(flippedBitmap, imageRotation)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResults(
        results: List<Classifications>?,
        inferenceTime: Long
    ) {
        activity?.runOnUiThread {
            // Find the TextView and update it with the classification result
            val label = results?.joinToString("\n") { classification ->
                val label = classification.categories.firstOrNull()?.label ?: "No label"
                label
            } ?: "No results"
            val resultText = results?.joinToString("\n") { classification ->
                val score = classification.categories.firstOrNull()?.score?.let {
                    String.format(Locale.US, "%.2f", it)
                } ?: "--"
                "$label: $score"
            } ?: "No results"


            fragmentCameraBinding.tvClassificationResult.text = resultText

            callSpotifyFunction(label)

            if (label == lastLabel) {
                if (System.currentTimeMillis() - lastLabelTimestamp >= labelChangeThreshold) {
                    // Call your function here since the label has been the same for 3 seconds
                    callSpotifyFunction(label)
                }
            } else {
                // If the label has changed, reset the timer
                lastLabel = label
                lastLabelTimestamp = System.currentTimeMillis()
            }


        }
    }

    private fun callSpotifyFunction(label: String) {
        val action = gestureToActionMap[label]
        fragmentCameraBinding.gestureClassified.text = action

        if (spotifyConnection.isSpotifyConnected) {
            //Log.d("Spotify", "WOULD BE CALLING " + action)
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
}
