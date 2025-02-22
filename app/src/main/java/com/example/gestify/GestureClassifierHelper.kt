package com.example.gestify

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier

class GestureClassifierHelper(
    var threshold: Float = 0.5f,
    var maxResults: Int = 3,
    val context: Context,
    val gestureClassifierListener: ClassifierListener?
) {
    private var gestureClassifier: ImageClassifier? = null

    init {
        setupGestureClassifier()
    }


    private fun setupGestureClassifier() {
        val optionsBuilder = ImageClassifier.ImageClassifierOptions.builder()
            .setScoreThreshold(threshold)
            .setMaxResults(maxResults)


        try {
            gestureClassifier =
                ImageClassifier.createFromFileAndOptions(
                    context, MODEL_PATH,
                    optionsBuilder.build()
                )
        } catch (e: IllegalStateException) {
            gestureClassifierListener?.onError(
                "Gesture classifier failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(TAG, "TFLite failed to load model with error: " + e.message)
        }
    }

    fun classify(image: Bitmap, imageRotation: Int) {
        if (gestureClassifier == null) {
            setupGestureClassifier()
        }

        // Inference time is the difference between the system time at the start and finish of the
        // process
        var inferenceTime = SystemClock.uptimeMillis()

        // Create preprocessor for the image.
        // See https://www.tensorflow.org/lite/inference_with_metadata/
        //            lite_support#imageprocessor_architecture
        val imageProcessor =
            ImageProcessor.Builder()
                .add(Rot90Op(imageRotation / 90))
                .build()

        // Preprocess the image and convert it into a TensorImage for classification.
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        val results = gestureClassifier?.classify(tensorImage)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        gestureClassifierListener?.onResults(results, inferenceTime)
    }

    interface ClassifierListener {
        fun onError(error: String)
        fun onResults(
            results: List<Classifications>?,
            inferenceTime: Long
        )
    }

    companion object {
        private const val MODEL_PATH = "model_metadata.tflite"
        private const val TAG = "GestureClassifierHelper"
    }
}
