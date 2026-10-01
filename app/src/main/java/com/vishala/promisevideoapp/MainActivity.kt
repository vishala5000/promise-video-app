package com.vishala.promisevideoapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var etCount: EditText
    private lateinit var btnGenerate: Button
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private val dataFile by lazy { File(filesDir, "data.txt") }
    private val fontFile by lazy { File(filesDir, "font.ttf") }
    private val prefs by lazy { getSharedPreferences("AppPrefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etCount = findViewById(R.id.etCount)
        btnGenerate = findViewById(R.id.btnGenerate)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)

        checkPermissions()
        downloadAssetsIfNeeded()

        btnGenerate.setOnClickListener {
            val countStr = etCount.text.toString().trim()
            startGeneration(countStr)
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_VIDEO), 100)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
            }
        }
    }

    private fun downloadAssetsIfNeeded() {
        lifecycleScope.launch(Dispatchers.IO) {
            tvStatus.post { tvStatus.text = "Downloading assets from GitHub Release..." }
            if (!dataFile.exists()) downloadFile("https://github.com/vishala5000/promise-video-app/releases/download/assets/data.txt", dataFile)
            if (!fontFile.exists()) downloadFile("https://github.com/vishala5000/promise-video-app/releases/download/assets/font.ttf", fontFile)
            tvStatus.post { tvStatus.text = "Assets ready. Enter count or leave blank for ALL unique." }
        }
    }

    private suspend fun downloadFile(url: String, destFile: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connect()
        if (connection.responseCode == 200) {
            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
        }
    }

    private fun startGeneration(requestedCountStr: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val allLines = if (dataFile.exists()) dataFile.readLines().filter { it.isNotBlank() } else emptyList()
            val generatedSet = prefs.getStringSet("generated_promises", emptySet()) ?: emptySet()
            val availableLines = allLines.filter { it !in generatedSet }

            if (availableLines.isEmpty()) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "All unique promises have been generated lifetime! No repeats possible."
                }
                return@launch
            }

            val count = if (requestedCountStr.isBlank()) Int.MAX_VALUE else requestedCountStr.toIntOrNull() ?: 1
            val toGenerate = availableLines.take(count)
            val actualCount = toGenerate.size

            withContext(Dispatchers.Main) {
                progressBar.max = actualCount
                progressBar.progress = 0
                tvStatus.text = "Generating $actualCount unique videos..."
                btnGenerate.isEnabled = false
            }

            val videoGenerator = VideoGenerator(this@MainActivity)
            val newGeneratedSet = generatedSet.toMutableSet()

            for ((index, line) in toGenerate.withIndex()) {
                val safeFileName = "promise_${System.currentTimeMillis()}_$index.mp4"
                
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Generating ${index + 1}/$actualCount: ${line.take(40)}..."
                }

                val success = videoGenerator.generateVideoSync(line, safeFileName)
                if (success) newGeneratedSet.add(line)

                withContext(Dispatchers.Main) {
                    progressBar.progress = index + 1
                }
            }

            prefs.edit().putStringSet("generated_promises", newGeneratedSet).apply()

            withContext(Dispatchers.Main) {
                val savePath = getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.absolutePath
                tvStatus.text = "Done! Generated $actualCount unique videos.\nSaved to: $savePath"
                btnGenerate.isEnabled = true
                Toast.makeText(this@MainActivity, "Videos saved to App Movies folder", Toast.LENGTH_LONG).show()
            }
        }
    }
}
