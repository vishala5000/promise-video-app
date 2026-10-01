package com.vishala.promisevideoapp

import android.content.Context
import android.graphics.*
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream
import java.util.Random

class VideoGenerator(private val context: Context) {

    fun generateVideoSync(promiseText: String, outputFileName: String): Boolean {
        val framesDir = File(context.cacheDir, "frames")
        if (!framesDir.exists()) framesDir.mkdirs()
        framesDir.listFiles()?.forEach { it.delete() }

        val words = promiseText.split(" ").filter { it.isNotBlank() }
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 1920f, Color.parseColor("#1a1a2e"), Color.parseColor("#0f3460"), Shader.TileMode.CLAMP)
        }
        val cardPaint = Paint().apply { color = Color.parseColor("#16213e"); style = Paint.Style.FILL }
        val revealCardPaint = Paint().apply {
            shader = LinearGradient(0f, 300f, 0f, 1600f, Color.parseColor("#1a1a2e"), Color.parseColor("#0f3460"), Shader.TileMode.CLAMP)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply { color = Color.parseColor("#e94560"); style = Paint.Style.STROKE; strokeWidth = 12f }

        val shuffleTextPaint = TextPaint().apply {
            color = Color.WHITE; textSize = 50f; textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val revealTextPaint = TextPaint().apply {
            color = Color.WHITE; textSize = 65f; isAntiAlias = true
        }

        val fontFile = File(context.filesDir, "font.ttf")
        if (fontFile.exists()) {
            revealTextPaint.typeface = Typeface.createFromFile(fontFile)
            shuffleTextPaint.typeface = Typeface.createFromFile(fontFile)
        }

        val random = Random()

        // Phase 1: Fast shuffle like dice (0 to 2 seconds, 60 frames at 30fps)
        for (i in 0 until 60) {
            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawRect(0f, 0f, 1080f, 1920f, bgPaint)

            for (j in 0 until 6) {
                val randomWord = words.randomOrNull() ?: "Promise"
                val x = 540f + (random.nextFloat() * 400f - 200f)
                val y = 960f + (random.nextFloat() * 400f - 200f)
                val rotation = random.nextFloat() * 60f - 30f

                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(rotation)

                val rect = RectF(-250f, -100f, 250f, 100f)
                canvas.drawRoundRect(rect, 40f, 40f, cardPaint)
                canvas.drawRoundRect(rect, 40f, 40f, borderPaint)
                canvas.drawText(randomWord, 0f, 15f, shuffleTextPaint)
                canvas.restore()
            }

            FileOutputStream(File(framesDir, "frame_${i.toString().padStart(4, '0')}.png")).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
        }

        // Phase 2: Reveal one promise card (2 to 5 seconds, 90 frames at 30fps)
        for (i in 60 until 150) {
            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawRect(0f, 0f, 1080f, 1920f, bgPaint)

            val cardRect = RectF(90f, 300f, 990f, 1600f)
            canvas.drawRoundRect(cardRect, 50f, 50f, revealCardPaint)
            canvas.drawRoundRect(cardRect, 50f, 50f, borderPaint)

            val textWidth = 800f
            val staticLayout = StaticLayout.Builder.obtain(promiseText, 0, promiseText.length, revealTextPaint, textWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(1.4f, 1.4f)
                .setIncludePad(false)
                .build()

            canvas.save()
            val startY = 960f - (staticLayout.height / 2f)
            canvas.translate(140f, startY)
            staticLayout.draw(canvas)
            canvas.restore()

            FileOutputStream(File(framesDir, "frame_${i.toString().padStart(4, '0')}.png")).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
        }

        // Phase 3: Compile with FFmpeg (H.264, 1080x1920)
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val outputFile = File(outputDir, outputFileName)
        val framesPattern = File(framesDir, "frame_%04d.png").absolutePath
        val outputPath = outputFile.absolutePath

        val command = "-y -framerate 30 -i $framesPattern -c:v libx264 -pix_fmt yuv420p -s 1080x1920 -t 5 $outputPath"
        val session = FFmpegKit.execute(command)
        
        framesDir.listFiles()?.forEach { it.delete() } // Cleanup frames
        return ReturnCode.isSuccess(session.returnCode)
    }
}
