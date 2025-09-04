package com.example.nback

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.io.FileOutputStream

class DrawingView @JvmOverloads constructor(


    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    // DrawingView 클래스 상단 필드에 추가
    private var firstTouchMs: Long = 0L
    private var lastTouchMs: Long = 0L

    /** 새 trial의 응답 구간 시작 직전에 호출해서 타임스탬프를 초기화 */
    fun resetTouchCapture() {
        firstTouchMs = 0L
        lastTouchMs = 0L
    }

    /** 첫 터치 시각(컴퓨터 시간, epoch ms). 터치 없으면 null */
    fun getFirstTouchTimeMillis(): Long? = if (firstTouchMs == 0L) null else firstTouchMs
    /** 마지막 터치 시각(컴퓨터 시간, epoch ms). 터치 없으면 null */
    fun getLastTouchTimeMillis(): Long? = if (lastTouchMs == 0L) null else lastTouchMs

    fun getTouchDurationMillis(): Long =
        if (firstTouchMs > 0 && lastTouchMs >= firstTouchMs) lastTouchMs - firstTouchMs else 0L


    // 그리기 관련 변수들
    private var drawPath: Path = Path()
    private var drawPaint: Paint = Paint()
    private var canvasPaint: Paint = Paint(Paint.DITHER_FLAG)
    private var drawCanvas: Canvas? = null
    private var canvasBitmap: Bitmap? = null
    private var paths: MutableList<Path> = mutableListOf()
    private var paints: MutableList<Paint> = mutableListOf()

    // 터치 관련 변수들
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private val touchTolerance: Float = 4f

    // 사용자 그림 여부 확인
    private var hasDrawing: Boolean = false

    // 비트맵 생성 상태 관리
    private var bitmapReady: Boolean = false

    init {
        setupDrawing()
    }

    private fun setupDrawing() {
        drawPaint.apply {
            color = Color.BLACK
            isAntiAlias = true
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        Log.d("DrawingView", "onSizeChanged called: ${w}x${h} (old: ${oldw}x${oldh})")

        // 크기가 유효하지 않으면 비트맵 생성하지 않음
        if (w <= 0 || h <= 0) {
            Log.w("DrawingView", "Invalid size in onSizeChanged: ${w}x${h}, skipping bitmap creation")
            bitmapReady = false
            return
        }

        // 기존 비트맵 해제
        cleanupBitmap()

        try {
            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = canvasBitmap?.let { Canvas(it) }
            drawCanvas?.drawColor(Color.WHITE)
            bitmapReady = true

            Log.d("DrawingView", "Canvas bitmap created successfully: ${w}x${h}")
        } catch (e: Exception) {
            Log.e("DrawingView", "Failed to create canvas bitmap: ${e.message}")
            bitmapReady = false
            canvasBitmap = null
            drawCanvas = null
        }
    }

    private fun cleanupBitmap() {
        canvasBitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        canvasBitmap = null
        drawCanvas = null
        bitmapReady = false
    }

    // 비트맵이 준비되지 않았을 때 생성하는 함수
    private fun ensureBitmapReady() {
        if (!bitmapReady && width > 0 && height > 0) {
            Log.d("DrawingView", "Late bitmap creation: ${width}x${height}")
            onSizeChanged(width, height, 0, 0)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 비트맵이 준비되지 않았으면 시도
        if (!bitmapReady) {
            ensureBitmapReady()
        }

        // 캔버스 비트맵 그리기
        canvasBitmap?.let { bitmap ->
            if (!bitmap.isRecycled && bitmapReady) {
                canvas.drawBitmap(bitmap, 0f, 0f, canvasPaint)
            }
        }

        // 현재 그리고 있는 경로 그리기
        canvas.drawPath(drawPath, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        // 비트맵이 준비되지 않았으면 준비 시도
        if (!bitmapReady) {
            ensureBitmapReady()
        }

        // 여전히 준비되지 않았으면 터치 이벤트 무시
        if (!bitmapReady || drawCanvas == null) {
            Log.w("DrawingView", "Canvas not ready, ignoring touch event")
            return false
        }

        val touchX = event.x
        val touchY = event.y
        val now = System.currentTimeMillis()   // ▼ 컴퓨터 시간(절대시각)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // ▼ 첫 터치가 아니면 유지, 처음이면 기록
                if (firstTouchMs == 0L) firstTouchMs = now
                lastTouchMs = now
                touchStart(touchX, touchY)
            }
            MotionEvent.ACTION_MOVE -> {
                lastTouchMs = now
                touchMove(touchX, touchY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastTouchMs = now
                touchUp()
            }
        }

        invalidate()
        return true
    }

    private fun touchStart(x: Float, y: Float) {
        hasDrawing = true
        drawPath.moveTo(x, y)
        lastTouchX = x
        lastTouchY = y
    }

    private fun touchMove(x: Float, y: Float) {
        val dx = Math.abs(x - lastTouchX)
        val dy = Math.abs(y - lastTouchY)

        if (dx >= touchTolerance || dy >= touchTolerance) {
            drawPath.quadTo(lastTouchX, lastTouchY, (x + lastTouchX) / 2, (y + lastTouchY) / 2)
            lastTouchX = x
            lastTouchY = y
        }
    }

    private fun touchUp() {
        drawPath.lineTo(lastTouchX, lastTouchY)

        // 캔버스가 준비된 상태에서만 그리기
        if (bitmapReady && drawCanvas != null) {
            drawCanvas?.drawPath(drawPath, drawPaint)

            // 경로와 페인트 저장
            paths.add(Path(drawPath))
            paints.add(Paint(drawPaint))
        }

        drawPath.reset()
    }

    fun clearCanvas() {
        hasDrawing = false
        paths.clear()
        paints.clear()
        drawPath.reset()

        // 비트맵이 준비된 상태에서만 지우기
        if (bitmapReady && drawCanvas != null) {
            drawCanvas?.drawColor(Color.WHITE)
        }

        invalidate()
        Log.d("DrawingView", "Canvas cleared")
    }

    fun hasUserDrawing(): Boolean {
        return hasDrawing && paths.isNotEmpty()
    }

    fun getRecognizedText(): String {
        if (!hasUserDrawing()) {
            return ""
        }

        // TODO: ML Kit Digital Ink Recognition 연동
        return ""
    }

    // ====== 수정: 하나의 메서드만 유지하고 오버로드 제거 ======
    fun saveCanvasAsPNG(fileName: String, directoryPath: String): Boolean {
        return try {
            // 현재 뷰 크기 확인
            val viewWidth = width
            val viewHeight = height

            Log.d("DrawingView", "Attempting to save image: ${viewWidth}x${viewHeight}")

            if (viewWidth <= 0 || viewHeight <= 0) {
                Log.e("DrawingView", "Invalid view size for saving: ${viewWidth}x${viewHeight}")
                return false
            }

            // 새로운 비트맵 생성해서 현재 뷰 그리기
            val bitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 흰색 배경
            canvas.drawColor(Color.WHITE)

            // 저장된 모든 경로들 그리기
            for (i in paths.indices) {
                if (i < paints.size) {
                    canvas.drawPath(paths[i], paints[i])
                }
            }

            // 디렉토리 생성
            val directory = File(directoryPath)
            if (!directory.exists()) {
                directory.mkdirs()
            }

            // 파일 저장
            val file = File(directory, "$fileName.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            bitmap.recycle()

            Log.d("DrawingView", "Image saved successfully: ${file.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e("DrawingView", "Failed to save image: ${e.message}")
            false
        }
    }

    fun setBrushSize(newSize: Float) {
        drawPaint.strokeWidth = newSize
    }

    fun setBrushColor(newColor: Int) {
        drawPaint.color = newColor
    }

    fun undo() {
        if (paths.isNotEmpty()) {
            paths.removeAt(paths.size - 1)
            paints.removeAt(paints.size - 1)

            redrawCanvas()

            if (paths.isEmpty()) {
                hasDrawing = false
            }
        }
    }

    private fun redrawCanvas() {
        if (bitmapReady && drawCanvas != null) {
            drawCanvas?.drawColor(Color.WHITE)

            for (i in paths.indices) {
                if (i < paints.size) {
                    drawCanvas?.drawPath(paths[i], paints[i])
                }
            }
        }

        invalidate()
    }

    fun getDrawingBounds(): RectF? {
        if (!hasUserDrawing()) return null

        val bounds = RectF()
        for (path in paths) {
            val pathBounds = RectF()
            path.computeBounds(pathBounds, true)
            if (bounds.isEmpty) {
                bounds.set(pathBounds)
            } else {
                bounds.union(pathBounds)
            }
        }

        return if (bounds.isEmpty) null else bounds
    }

    fun getDrawingComplexity(): Int {
        return paths.size
    }

    fun getDrawingCoverageRatio(): Float {
        val bounds = getDrawingBounds() ?: return 0f
        val totalArea = width * height
        if (totalArea <= 0) return 0f

        val drawingArea = bounds.width() * bounds.height()
        return drawingArea / totalArea
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanupBitmap()
        Log.d("DrawingView", "DrawingView detached and cleaned up")
    }
}