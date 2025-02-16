package com.example.gestify
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform


class PythonKotlin : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Python if not already initialized
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Get the Python instance
        val py = Python.getInstance()
        val pyModule = py.getModule("myscript")  // myscript.py must be in app/src/main/python

        // Call the function
        val result = pyModule.callAttr("test_numpy").toDouble()
        Log.d("PythonResult", "Result: $result")  // Should log: Result: 4.0
    }

}