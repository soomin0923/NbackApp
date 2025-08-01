package com.example.nback

import android.content.Context
import android.graphics.*
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    // 그리기 관련
    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val canvasPaint = Paint(Paint.DITHER_FLAG)
    private lateinit var drawCanvas: Canvas
    private lateinit var canvasBitmap: Bitmap
    private val path = Path()
    private val strokes = mutableListOf<Path>()

    // 손글씨 인식 관련
    private var recognizedText = ""
    private var hasDrawing = false

    // S Pen 감지
    private var isUsingSPen = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawCanvas = Canvas(canvasBitmap)
        drawCanvas.drawColor(Color.WHITE)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(canvasBitmap, 0f, 0f, canvasPaint)
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        // S Pen 감지 및 필압 적용
        when (event.getToolType(0)) {
            MotionEvent.TOOL_TYPE_STYLUS -> {
                isUsingSPen = true
                val pressure = event.pressure
                paint.strokeWidth = 6f + (pressure * 6f) // 6~12px
                paint.color = Color.BLACK
            }
            MotionEvent.TOOL_TYPE_FINGER -> {
                isUsingSPen = false
                paint.strokeWidth = 10f
                paint.color = Color.BLUE // 손가락은 파란색으로 구분
            }
            else -> {
                paint.strokeWidth = 8f
                paint.color = Color.BLACK
            }
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                hasDrawing = true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
            }
            MotionEvent.ACTION_UP -> {
                drawCanvas.drawPath(path, paint)
                strokes.add(Path(path))
                path.reset()

                // 간단한 숫자 인식 시도
                recognizeNumber()
            }
        }

        invalidate()
        return true
    }

    // 간단한 숫자 인식 (실제 환경에서는 ML Kit 사용)
    private fun recognizeNumber() {
        if (!hasDrawing) {
            recognizedText = ""
            return
        }

        // 더미 구현: 실제로는 ML Kit의 Digital Ink Recognition 사용
        // 현재는 확률적으로 숫자 인식 시뮬레이션
        val numbers = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

        // 스트로크 수에 따른 숫자 추정 (매우 간단한 휴리스틱)
        recognizedText = when (strokes.size) {
            1 -> listOf("1", "7", "0").random()
            2 -> listOf("2", "3", "4", "5", "6", "7", "9").random()
            3 -> listOf("8", "4", "6", "9").random()
            else -> numbers.random()
        }
    }

    fun clearCanvas() {
        drawCanvas.drawColor(Color.WHITE)
        strokes.clear()
        path.reset()
        recognizedText = ""
        hasDrawing = false
        invalidate()
    }

    fun getRecognizedText(): String {
        return if (hasDrawing) recognizedText else ""
    }

    fun hasUserDrawing(): Boolean = hasDrawing

    fun isUsingSPen(): Boolean = isUsingSPen

    // 캔버스를 PNG 파일로 저장
    fun saveCanvasAsPNG(fileName: String, participantName: String = "Unknown"): Boolean {
        return try {
            // Downloads 폴더에 nback_images 서브폴더 생성
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val imageDir = File(downloadsDir, "nback_images")
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }

            // PNG 파일로 저장 (참가자 이름 포함)
            val finalFileName = "${participantName}_${fileName}"
            val file = File(imageDir, "$finalFileName.png")
            val fileOutputStream = FileOutputStream(file)

            // 현재 캔버스를 Bitmap으로 변환하여 저장
            canvasBitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()

            Log.d("DrawingView", "Image saved: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("DrawingView", "Failed to save image: ${e.message}")
            false
        }
    }
}