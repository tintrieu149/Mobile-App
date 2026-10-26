package vn.wom.ghinhanh

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

abstract class Shape(val color: Int, val width: Float) {
    abstract fun draw(c: Canvas)

    protected fun strokePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = this@Shape.color
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
}

class PenShape(color: Int, width: Float, x: Float, y: Float) : Shape(color, width) {
    val path = Path().apply { moveTo(x, y); lineTo(x, y) }
    fun moveTo(x: Float, y: Float) = path.lineTo(x, y)
    override fun draw(c: Canvas) = c.drawPath(path, strokePaint())
}

class LineShape(val kind: AnnotateView.Tool, color: Int, width: Float, val x1: Float, val y1: Float) : Shape(color, width) {
    var x2 = x1
    var y2 = y1
    fun length() = hypot(x2 - x1, y2 - y1)

    override fun draw(c: Canvas) {
        val p = strokePaint()
        when (kind) {
            AnnotateView.Tool.RECT -> c.drawRect(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2), p)
            AnnotateView.Tool.OVAL -> c.drawOval(RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)), p)
            else -> {
                c.drawLine(x1, y1, x2, y2, p)
                val angle = atan2(y2 - y1, x2 - x1)
                val head = width * 5f
                for (side in intArrayOf(-1, 1)) {
                    val a = angle + Math.PI.toFloat() + side * 0.5f
                    c.drawLine(x2, y2, x2 + head * cos(a), y2 + head * sin(a), p)
                }
            }
        }
    }
}

class TextShape(val text: String, color: Int, val size: Float, val x: Float, val y: Float) : Shape(color, size) {
    override fun draw(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            isFakeBoldText = true
        }
        var yy = y
        for (line in text.split("\n")) {
            // viền trắng để chữ đọc được trên mọi nền ảnh
            p.style = Paint.Style.STROKE
            p.strokeWidth = size * 0.18f
            p.color = if (color == Color.WHITE) Color.BLACK else Color.WHITE
            c.drawText(line, x, yy, p)
            p.style = Paint.Style.FILL
            p.color = color
            c.drawText(line, x, yy, p)
            yy += size * 1.2f
        }
    }
}

class NumberShape(val n: Int, color: Int, val radius: Float, val x: Float, val y: Float) : Shape(color, radius) {
    override fun draw(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawCircle(x, y, radius, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = radius * 0.12f
        p.color = Color.WHITE
        c.drawCircle(x, y, radius, p)
        p.style = Paint.Style.FILL
        p.textSize = radius * 1.2f
        p.isFakeBoldText = true
        p.textAlign = Paint.Align.CENTER
        p.color = if (color == Color.WHITE || color == Color.YELLOW) Color.BLACK else Color.WHITE
        val fm = p.fontMetrics
        c.drawText(n.toString(), x, y - (fm.ascent + fm.descent) / 2f, p)
    }
}

/** Khung ảnh để vẽ chú thích; mọi hình lưu theo toạ độ ảnh gốc nên xuất ra không bị lệch. */
class AnnotateView(ctx: Context) : View(ctx) {
    enum class Tool { PEN, ARROW, RECT, OVAL, NUMBER, TEXT }

    var tool = Tool.PEN
    var color = Color.RED
    /** 0 = mỏng, 1 = vừa, 2 = dày */
    var level = 1
    var onTextRequest: ((Float, Float) -> Unit)? = null
    var onChange: (() -> Unit)? = null

    private var bitmap: Bitmap? = null
    private val shapes = ArrayList<Shape>()
    private val redoStack = ArrayList<Shape>()
    private var current: Shape? = null
    private var nextNumber = 1
    private val matrix = Matrix()
    private val inverse = Matrix()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    val canUndo get() = shapes.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    val hasEdits get() = shapes.isNotEmpty()

    fun setBitmap(bmp: Bitmap) {
        bitmap = bmp
        shapes.clear()
        redoStack.clear()
        nextNumber = 1
        fit()
        invalidate()
    }

    private fun unit() = bitmap?.let { max(it.width, it.height) } ?: 1000
    private fun strokeWidth() = unit() * 0.005f * floatArrayOf(0.7f, 1.4f, 2.6f)[level]
    private fun textSize() = unit() * floatArrayOf(0.025f, 0.04f, 0.06f)[level]
    private fun badgeRadius() = unit() * floatArrayOf(0.02f, 0.03f, 0.045f)[level]

    private fun fit() {
        val b = bitmap ?: return
        if (width == 0 || height == 0) return
        matrix.setRectToRect(
            RectF(0f, 0f, b.width.toFloat(), b.height.toFloat()),
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            Matrix.ScaleToFit.CENTER,
        )
        matrix.invert(inverse)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fit()
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        canvas.save()
        canvas.concat(matrix)
        canvas.drawBitmap(b, 0f, 0f, bitmapPaint)
        shapes.forEach { it.draw(canvas) }
        current?.draw(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val b = bitmap ?: return false
        val pt = floatArrayOf(e.x, e.y)
        inverse.mapPoints(pt)
        val x = pt[0].coerceIn(0f, b.width.toFloat())
        val y = pt[1].coerceIn(0f, b.height.toFloat())
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                when (tool) {
                    Tool.PEN -> current = PenShape(color, strokeWidth(), x, y)
                    Tool.ARROW, Tool.RECT, Tool.OVAL -> current = LineShape(tool, color, strokeWidth(), x, y)
                    Tool.NUMBER -> commit(NumberShape(nextNumber++, color, badgeRadius(), x, y))
                    Tool.TEXT -> onTextRequest?.invoke(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (val s = current) {
                    is PenShape -> {
                        for (i in 0 until e.historySize) {
                            val h = floatArrayOf(e.getHistoricalX(i), e.getHistoricalY(i))
                            inverse.mapPoints(h)
                            s.moveTo(h[0], h[1])
                        }
                        s.moveTo(x, y)
                    }
                    is LineShape -> { s.x2 = x; s.y2 = y }
                }
            }
            MotionEvent.ACTION_UP -> {
                val s = current
                current = null
                if (s is LineShape && s.length() < unit() * 0.01f) {
                    // chạm nhẹ: bỏ qua
                } else if (s != null) {
                    commit(s)
                }
            }
            MotionEvent.ACTION_CANCEL -> current = null
        }
        invalidate()
        return true
    }

    private fun commit(s: Shape) {
        shapes.add(s)
        redoStack.clear()
        onChange?.invoke()
    }

    fun addText(text: String, x: Float, y: Float) {
        if (text.isBlank()) return
        commit(TextShape(text, color, textSize(), x, y))
        invalidate()
    }

    fun undo() {
        if (shapes.isEmpty()) return
        val s = shapes.removeAt(shapes.size - 1)
        if (s is NumberShape) nextNumber = s.n
        redoStack.add(s)
        onChange?.invoke()
        invalidate()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val s = redoStack.removeAt(redoStack.size - 1)
        if (s is NumberShape) nextNumber = s.n + 1
        shapes.add(s)
        onChange?.invoke()
        invalidate()
    }

    /** Ảnh gốc + mọi hình đã vẽ, ở độ phân giải ảnh gốc. */
    fun render(): Bitmap? {
        val b = bitmap ?: return null
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        shapes.forEach { it.draw(c) }
        return out
    }
}
