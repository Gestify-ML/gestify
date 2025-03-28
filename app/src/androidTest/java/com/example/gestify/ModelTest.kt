package com.example.gestify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class ModelTest {
    // Model Test used to see if TFLite model works on image from Hagrid
    companion object {
        private const val TAG = "ModelTest"
        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private val GESTURE_LABELS = listOf(
            "middle finger", "Dislike", "Fist", "four", "like",
            "one", "palm", "Three", "Two up", "no gesture"
        )
    }

    private lateinit var interpreter: Interpreter
    private val context: Context by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Before
    fun setup() {
        val model = loadModelFile()
        interpreter = Interpreter(model)
    }

    @Test
    fun testModelWithStaticImage() {
        // Load test image
        val testBitmap = loadTestImage() ?: run {
            fail("Failed to load test image")
            return
        }

        val inputBuffer = prepareInputBuffer(testBitmap)

        // prepare output tensor (1,14,8400)
        val output = Array(1) { Array(14) { FloatArray(8400) } }

        // run inference
        interpreter.run(inputBuffer, output)

        // process results
        val detections = processOutput(output[0])
        Log.d(TAG, "Found ${detections.size} detections")

        // print detailed predictions
        printDetailedPredictions(detections)

        // verify at least one "dislike" detected with high confidence
        val dislikeDetections = detections.filter { it.first == 1 } // Class 1 = Dislike
        assertTrue("No 'dislike' gestures detected", dislikeDetections.isNotEmpty())

        val topDetection = dislikeDetections.maxByOrNull { it.second }
        Log.d(TAG, "Top detection: ${GESTURE_LABELS[topDetection!!.first]} (${topDetection.second})")
        assertTrue("Confidence too low", topDetection.second > CONFIDENCE_THRESHOLD)
    }

    private fun loadTestImage(): Bitmap? {
        return try {
            BitmapFactory.decodeResource(context.resources, R.drawable.fist2)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading test image", e)
            null
        }
    }

    private fun loadModelFile(): ByteBuffer {
        return FileUtil.loadMappedFile(context, "ten_gestures_full.tflite")
    }

    private fun prepareInputBuffer(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        return ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            for (pixel in pixels) {
                putFloat(Color.red(pixel) / 255f)
                putFloat(Color.green(pixel) / 255f)
                putFloat(Color.blue(pixel) / 255f)
            }
            rewind()
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
            if (maxScore > CONFIDENCE_THRESHOLD) {
                detections.add(classId to maxScore)
            }
        }

        return detections
    }

    private fun printDetailedPredictions(detections: List<Pair<Int, Float>>) {
        // group detections by class
        val groupedDetections = detections.groupBy { it.first }

        Log.d(TAG, "========== Detailed Predictions ==========")
        Log.d(TAG, "Total detections above threshold: ${detections.size}")

        // print stats for each class
        groupedDetections.forEach { (classId, classDetections) ->
            val label = GESTURE_LABELS.getOrElse(classId) { "Unknown" }
            val count = classDetections.size
            val maxConfidence = classDetections.maxOfOrNull { it.second } ?: 0f
            val avgConfidence = classDetections.map { it.second }.average().toFloat()

            Log.d(TAG, "Class: $label (ID: $classId)")
            Log.d(TAG, "  Detections: $count")
            Log.d(TAG, "  Max confidence: $maxConfidence")
            Log.d(TAG, "  Avg confidence: $avgConfidence")

            // print top 5 detections for this class if there are many
            if (count > 5) {
                Log.d(TAG, "  Top 5 detections:")
                classDetections.sortedByDescending { it.second }
                    .take(5)
                    .forEachIndexed { index, detection ->
                        Log.d(TAG, "    ${index + 1}. Confidence: ${detection.second}")
                    }
            }
        }

        // print top 5 detections overall
        Log.d(TAG, "========== Top 5 Detections Overall ==========")
        detections.sortedByDescending { it.second }
            .take(5)
            .forEachIndexed { index, detection ->
                val label = GESTURE_LABELS.getOrElse(detection.first) { "Unknown" }
                Log.d(TAG, "${index + 1}. $label (ID: ${detection.first}) - Confidence: ${detection.second}")
            }
    }
}