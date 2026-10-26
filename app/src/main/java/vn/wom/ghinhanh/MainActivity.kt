package vn.wom.ghinhanh

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var all: List<Entry> = emptyList()
    private var query = ""
    private lateinit var adapter: EntryAdapter
    private lateinit var empty: TextView
    private var pendingPhoto: File? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingPhoto
        if (ok && f != null && f.length() > 0) openEditor(f.absolutePath)
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val f = newCacheFile()
            try {
                contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                openEditor(f.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(this, "Không mở được ảnh", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPhoto = savedInstanceState?.getString("photo")?.let { File(it) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF4F6F8.toInt())
            fitsSystemWindows = true
        }
        root.addView(TextView(this).apply {
            text = "Ghi Nhanh"
            setTextColor(Color.WHITE)
            textSize = 20f
            setBackgroundColor(BLUE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        })
        root.addView(EditText(this).apply {
            hint = "Tìm trong ghi chú…"
            setSingleLine()
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(Color.WHITE)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s.toString().trim()
                    applyFilter()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        })

        adapter = EntryAdapter()
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            clipToPadding = false
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        empty = TextView(this).apply {
            text = "Chưa có gì.\nBấm “Chụp” để chụp ảnh rồi vẽ ghi chú lên."
            gravity = Gravity.CENTER
            setTextColor(0xFF6B7682.toInt())
            textSize = 16f
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }
        root.addView(empty)
        root.addView(list)

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            fun add(label: String, primary: Boolean, action: () -> Unit) {
                addView(chip(label, action).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(4), 0, dp(4), 0) }
                    textSize = 16f
                    mark(primary)
                })
            }
            add("📷 Chụp", true) { capture() }
            add("🖼 Chọn ảnh", false) { pickImage.launch("image/*") }
            add("📝 Ghi chú", false) { startActivity(Intent(this@MainActivity, EditorActivity::class.java)) }
        })
        setContentView(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingPhoto?.let { outState.putString("photo", it.absolutePath) }
    }

    override fun onResume() {
        super.onResume()
        all = Store.load(this)
        applyFilter()
    }

    private fun applyFilter() {
        val shown = if (query.isEmpty()) all else all.filter { it.note.contains(query, ignoreCase = true) }
        adapter.items = shown
        adapter.notifyDataSetChanged()
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun newCacheFile(): File {
        val dir = File(cacheDir, "camera").also { it.mkdirs() }
        return File(dir, "IMG_${System.currentTimeMillis()}.jpg")
    }

    private fun capture() {
        val f = newCacheFile()
        pendingPhoto = f
        try {
            takePicture.launch(FileProvider.getUriForFile(this, "$packageName.files", f))
        } catch (e: Exception) {
            Toast.makeText(this, "Máy không mở được camera", Toast.LENGTH_LONG).show()
        }
    }

    private fun openEditor(path: String) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_PATH, path))
    }

    private fun confirmDelete(e: Entry) {
        AlertDialog.Builder(this)
            .setTitle("Xóa mục này?")
            .setMessage("Ảnh đã lưu trong thư viện máy (Pictures/GhiNhanh) không bị xóa.")
            .setPositiveButton("Xóa") { _, _ ->
                Store.delete(this, e)
                all = Store.load(this)
                applyFilter()
            }
            .setNegativeButton("Giữ lại", null)
            .show()
    }

    private inner class EntryAdapter : RecyclerView.Adapter<EntryAdapter.VH>() {
        var items: List<Entry> = emptyList()
        private val thumbs = LruCache<String, Bitmap>(60)
        private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))

        inner class VH(val row: LinearLayout, val img: ImageView, val note: TextView, val time: TextView) :
            RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val img = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(GREY)
                layoutParams = LinearLayout.LayoutParams(ctx.dp(84), ctx.dp(84))
            }
            val note = TextView(ctx).apply {
                textSize = 15f
                setTextColor(Color.BLACK)
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val time = TextView(ctx).apply {
                textSize = 12f
                setTextColor(0xFF6B7682.toInt())
            }
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ctx.dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(note)
                addView(time)
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(ctx.dp(10), ctx.dp(10), ctx.dp(10), ctx.dp(10))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = ctx.dp(8) }
                addView(img)
                addView(col)
            }
            return VH(row, img, note, time)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val e = items[position]
            h.note.text = e.note.ifEmpty { if (e.image != null) "(ảnh không có ghi chú)" else "" }
            h.time.text = fmt.format(Date(e.createdAt))
            val file = Store.imageFile(this@MainActivity, e)
            h.img.visibility = if (file == null) View.GONE else View.VISIBLE
            h.img.setImageBitmap(file?.let { f ->
                thumbs.get(f.name + f.lastModified()) ?: Images.load(f.absolutePath, 256)?.also {
                    thumbs.put(f.name + f.lastModified(), it)
                }
            })
            h.row.setOnClickListener {
                startActivity(Intent(this@MainActivity, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_ID, e.id))
            }
            h.row.setOnLongClickListener { confirmDelete(e); true }
        }
    }
}
