package vn.wom.ghinhanh

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.UUID

/**
 * Màn hình vẽ chú thích lên ảnh và nhập ghi chú.
 * Extras: EXTRA_ID (sửa mục có sẵn) hoặc EXTRA_PATH (ảnh mới), hoặc không có gì (chỉ ghi chú chữ).
 */
class EditorActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_PATH = "path"
    }

    private lateinit var canvasView: AnnotateView
    private lateinit var noteEdit: EditText
    private lateinit var undoBtn: MaterialButton
    private lateinit var redoBtn: MaterialButton
    private lateinit var levelBtn: MaterialButton
    private val toolButtons = LinkedHashMap<AnnotateView.Tool, MaterialButton>()
    private val colorRow by lazy { LinearLayout(this) }
    private val palette = intArrayOf(Color.RED, Color.YELLOW, 0xFF2E7D32.toInt(), 0xFF1565C0.toInt(), Color.WHITE, Color.BLACK)

    private var existing: Entry? = null
    private var hasImage = false
    private var saving = false
    private val levelNames = arrayOf("Nét: mỏng", "Nét: vừa", "Nét: dày")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getStringExtra(EXTRA_ID)
        val path = intent.getStringExtra(EXTRA_PATH)
        existing = id?.let { i -> Store.load(this).firstOrNull { it.id == i } }
        val imagePath = path ?: existing?.let { Store.imageFile(this, it)?.absolutePath }
        hasImage = imagePath != null

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            fitsSystemWindows = true
        }

        root.addView(buildTopBar())

        canvasView = AnnotateView(this).apply {
            setBackgroundColor(0xFF20242A.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            onTextRequest = { x, y -> askText(x, y) }
            onChange = { refreshUndo() }
        }
        if (hasImage) {
            root.addView(canvasView)
            root.addView(buildTools())
            val bmp = Images.load(imagePath!!, 2560)
            if (bmp == null) {
                Toast.makeText(this, "Không đọc được ảnh", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            canvasView.setBitmap(bmp)
        } else {
            root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 0f) })
        }

        noteEdit = EditText(this).apply {
            hint = "Ghi chú…"
            setText(existing?.note ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP
            if (hasImage) {
                minLines = 1
                maxLines = 3
            } else {
                minLines = 6
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        if (hasImage) {
            noteEdit.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(noteEdit)
        root.addView(buildBottomBar())
        setContentView(root)

        if (hasImage) {
            selectTool(AnnotateView.Tool.PEN)
            refreshColors()
            refreshUndo()
        }
    }

    private fun buildTopBar(): View = TextView(this).apply {
        text = if (hasImage) "Vẽ lên ảnh" else "Ghi chú"
        setTextColor(Color.WHITE)
        textSize = 18f
        setBackgroundColor(BLUE)
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }

    private fun buildTools(): View {
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = linkedMapOf(
            AnnotateView.Tool.PEN to "✏ Vẽ",
            AnnotateView.Tool.ARROW to "➜ Mũi tên",
            AnnotateView.Tool.RECT to "▭ Khung",
            AnnotateView.Tool.OVAL to "◯ Tròn",
            AnnotateView.Tool.NUMBER to "① Số",
            AnnotateView.Tool.TEXT to "T Chữ",
        )
        labels.forEach { (tool, label) ->
            val b = chip(label) { selectTool(tool) }
            toolButtons[tool] = b
            tools.addView(b)
        }
        undoBtn = chip("↶") { canvasView.undo() }
        redoBtn = chip("↷") { canvasView.redo() }
        levelBtn = chip(levelNames[1]) {
            canvasView.level = (canvasView.level + 1) % 3
            levelBtn.text = levelNames[canvasView.level]
        }
        tools.addView(levelBtn)
        tools.addView(undoBtn)
        tools.addView(redoBtn)

        colorRow.orientation = LinearLayout.HORIZONTAL
        colorRow.gravity = Gravity.CENTER_VERTICAL

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(HorizontalScrollView(this@EditorActivity).apply {
                isHorizontalScrollBarEnabled = false
                addView(tools)
            })
            addView(HorizontalScrollView(this@EditorActivity).apply {
                isHorizontalScrollBarEnabled = false
                addView(colorRow)
            })
        }
    }

    private fun buildBottomBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(6), dp(4), dp(6), dp(6))
        fun add(label: String, primary: Boolean, action: () -> Unit) {
            addView(chip(label, action).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(dp(4), 0, dp(4), 0) }
                mark(primary)
            })
        }
        add("Hủy", false) { finish() }
        if (hasImage) add("Chia sẻ", false) { save(share = true) }
        add("Lưu", true) { save(share = false) }
    }

    private fun selectTool(t: AnnotateView.Tool) {
        canvasView.tool = t
        toolButtons.forEach { (k, b) -> b.mark(k == t) }
    }

    private fun refreshColors() {
        colorRow.removeAllViews()
        palette.forEach { c ->
            colorRow.addView(colorDot(c, c == canvasView.color).apply {
                setOnClickListener {
                    canvasView.color = c
                    refreshColors()
                }
            })
        }
    }

    private fun refreshUndo() {
        undoBtn.alpha = if (canvasView.canUndo) 1f else 0.4f
        redoBtn.alpha = if (canvasView.canRedo) 1f else 0.4f
    }

    private fun askText(x: Float, y: Float) {
        val input = EditText(this).apply {
            hint = "Nội dung chữ trên ảnh"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialog.Builder(this)
            .setTitle("Thêm chữ")
            .setView(input)
            .setPositiveButton("Thêm") { _, _ -> canvasView.addText(input.text.toString(), x, y) }
            .setNegativeButton("Bỏ", null)
            .show()
    }

    private fun save(share: Boolean) {
        if (saving) return
        val note = noteEdit.text.toString().trim()
        if (!hasImage && note.isEmpty()) {
            Toast.makeText(this, "Chưa có nội dung", Toast.LENGTH_SHORT).show()
            return
        }
        saving = true
        val app = applicationContext
        val old = existing
        val entryId = old?.id ?: UUID.randomUUID().toString()
        val created = old?.createdAt ?: System.currentTimeMillis()
        val bmp = if (hasImage) canvasView.render() else null
        val edited = old == null || canvasView.hasEdits
        Thread {
            var imageName: String? = old?.image
            var shareFile: File? = null
            if (bmp != null) {
                if (edited) {
                    imageName = "$entryId.jpg"
                    File(Store.imagesDir(app), imageName).outputStream().use {
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it)
                    }
                    try {
                        Store.saveToGallery(app, bmp, "GhiNhanh_$created")
                    } catch (e: Exception) {
                        // không ghi được vào thư viện thì vẫn giữ bản trong app
                    }
                }
                if (share) shareFile = File(Store.imagesDir(app), imageName!!)
            }
            Store.upsert(app, Entry(entryId, created, note, imageName))
            runOnUiThread {
                if (share && shareFile != null) {
                    val uri = FileProvider.getUriForFile(this, "$packageName.files", copyToCache(shareFile))
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        if (note.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, note)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Chia sẻ ảnh"))
                }
                Toast.makeText(this, "Đã lưu", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }

    /** FileProvider chỉ mở thư mục cache nên chép ảnh sang đó để chia sẻ. */
    private fun copyToCache(src: File): File {
        val dir = File(cacheDir, "share").also { it.mkdirs() }
        return File(dir, src.name).also { src.copyTo(it, overwrite = true) }
    }
}
