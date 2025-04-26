package com.example.gestify

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer

class ObjectDetectionHelper(
    private val context: Context,
    private val confidenceThreshold: Float = 0.5f
) {
    private var interpreter: Interpreter? = null

    init {
        Log.d(TAG, "Initializing interpreter...")
        try {
            // verify model file exists
            val modelFile = context.assets.open("ten_gestures_full.tflite").use {
                Log.d(TAG, "Model file size: ${it.available()} bytes")
                FileUtil.loadMappedFile(context, "ten_gestures_full.tflite")
            }
            interpreter = Interpreter(modelFile)
            Log.d(TAG, "Interpreter initialized successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize interpreter", e)
        }
    }

    fun detectWithDetails(inputBuffer: ByteBuffer): Pair<List<Pair<Int, Float>>, Long> {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter is null!")
            return Pair(emptyList(), (0.0).toLong())
        }

        val output = Array(1) { Array(14) { FloatArray(8400) } }

        return try {
            val startTime = SystemClock.uptimeMillis()
            interpreter?.run(inputBuffer, output)
            val endTime = SystemClock.uptimeMillis()
            val inferenceTimeMSeconds = (endTime - startTime)
            val inferenceTimeSeconds = inferenceTimeMSeconds / 1000.0
            Log.d("TFLite Inference Time", "Inference time: $inferenceTimeSeconds s")
            val detections = processOutput(output[0])
            Pair(detections, inferenceTimeMSeconds)

        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            Pair(emptyList(), (0.0).toLong())
        }
    }

    private fun processOutput(output: Array<FloatArray>): List<Pair<Int, Float>> {
        val detections = mutableListOf<Pair<Int, Float>>()

        // output[0-3] = bbox (cx,cy,w,h)
        // output[4-13] = class scores (10 gestures)

        for (i in 0 until 8400) {
            // get max class score and its index
            var maxScore = 0f
            var classId = -1
            for (c in 4..13) {
                if (output[c][i] > maxScore) {
                    maxScore = output[c][i]
                    classId = c - 4  // convert to 0-based class index
                }
            }

            // apply confidence threshold
            if (maxScore > confidenceThreshold) {
                detections.add(classId to maxScore)
            }
        }

        return detections
    }

    fun getGestureLabel(classId: Int): String {
        return gestureClasses.getOrElse(classId) { "unknown" }
    }

    companion object {
        private const val TAG = "ObjectDetectionHelper"
        private val gestureClasses = listOf(
            "middle_finger", "dislike", "fist", "four", "like",
            "one", "palm", "three", "two_up", "no_gesture"
        )
    }
}