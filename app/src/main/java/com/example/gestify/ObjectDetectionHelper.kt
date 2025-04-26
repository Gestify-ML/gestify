package com.example.gestify

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Collections

class ObjectDetectionHelper(
    private val context: Context,
    private val confidenceThreshold: Float = 0.7f
) {
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private val inputSize = 640

    init {
        Log.d(TAG, "Initializing ONNX Runtime...")
        try {
            // Load the ONNX model
            val modelBytes = context.assets.open("ten_gestures_full.onnx").use { inputStream ->
                inputStream.readBytes()
            }

            ortEnvironment = OrtEnvironment.getEnvironment()
            ortSession = ortEnvironment?.createSession(modelBytes)

            Log.d(TAG, "ONNX Runtime initialized successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX Runtime", e)
        }
    }

    fun detectWithDetails(bitmap: Bitmap): List<Pair<Int, Float>> {
        if (ortSession == null) {
            Log.e(TAG, "ONNX Session is null!")
            return emptyList()
        }

        return try {
            // Prepare input tensor
            val inputTensor = prepareInputTensor(bitmap)

            // Run inference (replace "images" with your model's actual input name)
            val startTime = SystemClock.uptimeMillis()
            val results = ortSession?.run(Collections.singletonMap("images", inputTensor))
            val endTime = SystemClock.uptimeMillis()

            val inferenceTime = endTime - startTime
            Log.d("ONNX Inference Time", "Inference time: $inferenceTime ms")

            // Process results
            val output = results?.get(0)?.value as Array<Array<FloatArray>>
            results.close()
            inputTensor.close()

            processOutput(output[0])
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            emptyList()
        }
    }

    private fun prepareInputTensor(bitmap: Bitmap): OnnxTensor {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val floatArray = FloatArray(3 * inputSize * inputSize)
        val intValues = IntArray(inputSize * inputSize)

        resized.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        // Normalize to [0,1] and convert to CHW format
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixel = intValues[i * inputSize + j]
                floatArray[i * inputSize + j] = (pixel shr 16 and 0xFF) / 255.0f       // R
                floatArray[inputSize*inputSize + i * inputSize + j] = (pixel shr 8 and 0xFF) / 255.0f  // G
                floatArray[2*inputSize*inputSize + i * inputSize + j] = (pixel and 0xFF) / 255.0f      // B
            }
        }

        // Convert to FloatBuffer
        val floatBuffer = ByteBuffer
            .allocateDirect(floatArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(floatArray)
        floatBuffer.rewind()

        return OnnxTensor.createTensor(
            ortEnvironment!!,
            floatBuffer,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        )
    }

    private fun prepareInputTensorFloat16(bitmap: Bitmap): OnnxTensor {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val floatArray = FloatArray(3 * inputSize * inputSize)
        val intValues = IntArray(inputSize * inputSize)

        resized.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        // Normalize and convert to CHW format
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixel = intValues[i * inputSize + j]
                floatArray[i * inputSize + j] = (pixel shr 16 and 0xFF) / 255.0f       // R
                floatArray[inputSize * inputSize + i * inputSize + j] = (pixel shr 8 and 0xFF) / 255.0f  // G
                floatArray[2 * inputSize * inputSize + i * inputSize + j] = (pixel and 0xFF) / 255.0f    // B
            }
        }

        // Convert float array to float16 ByteBuffer
        val byteBuffer = ByteBuffer.allocateDirect(floatArray.size * 2)
            .order(ByteOrder.nativeOrder())

        for (value in floatArray) {
            byteBuffer.putShort(float32ToFloat16(value))
        }
        byteBuffer.rewind()

        return OnnxTensor.createTensor(
            ortEnvironment!!,
            byteBuffer,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()),
            OnnxJavaType.FLOAT16
        )
    }

    fun float32ToFloat16(value: Float): Short {
        val intBits = java.lang.Float.floatToIntBits(value)
        val sign = (intBits ushr 16) and 0x8000
        val valExponent = ((intBits ushr 23) and 0xFF) - 127 + 15
        val mantissa = (intBits ushr 13) and 0x3FF

        return when {
            valExponent <= 0 -> (sign or 0).toShort()
            valExponent >= 0x1F -> (sign or 0x7C00).toShort() // Inf
            else -> (sign or (valExponent shl 10) or mantissa).toShort()
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

    fun close() {
        ortSession?.close()
        ortEnvironment?.close()
    }

    companion object {
        private const val TAG = "ObjectDetectionHelper"
        private val gestureClasses = listOf(
            "middle_finger", "dislike", "fist", "four", "like",
            "one", "palm", "three", "two_up", "no_gesture"
        )
    }
}