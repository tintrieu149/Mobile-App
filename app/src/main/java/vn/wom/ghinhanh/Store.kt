package vn.wom.ghinhanh

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Một mục: ảnh (đã vẽ ghi chú) và/hoặc ghi chú chữ. */
data class Entry(
    val id: String,
    val createdAt: Long,
    val note: String,
    val image: String?,
)

/** Lưu cục bộ trong bộ nhớ riêng của app: entries.json + thư mục images. */
object Store {
    private fun indexFile(ctx: Context) = File(ctx.filesDir, "entries.json")

    fun imagesDir(ctx: Context) = File(ctx.filesDir, "images").also { it.mkdirs() }

    fun imageFile(ctx: Context, entry: Entry): File? = entry.image?.let { File(imagesDir(ctx), it) }

    @Synchronized
    fun load(ctx: Context): MutableList<Entry> {
        val f = indexFile(ctx)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            val out = ArrayList<Entry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Entry(
                        o.getString("id"),
                        o.getLong("t"),
                        o.optString("note", ""),
                        if (o.has("img")) o.getString("img") else null,
                    ),
                )
            }
            out.sortByDescending { it.createdAt }
            out
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    fun save(ctx: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("t", it.createdAt)
            o.put("note", it.note)
            if (it.image != null) o.put("img", it.image)
            arr.put(o)
        }
        val tmp = File(ctx.filesDir, "entries.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(indexFile(ctx))) {
            indexFile(ctx).writeText(arr.toString())
            tmp.delete()
        }
    }

    @Synchronized
    fun upsert(ctx: Context, entry: Entry) {
        val list = load(ctx)
        val i = list.indexOfFirst { it.id == entry.id }
        if (i >= 0) list[i] = entry else list.add(entry)
        save(ctx, list)
    }

    @Synchronized
    fun delete(ctx: Context, entry: Entry) {
        imageFile(ctx, entry)?.delete()
        save(ctx, load(ctx).filter { it.id != entry.id })
    }

    /** Ghi ảnh vào thư viện ảnh của máy (Pictures/GhiNhanh). */
    fun saveToGallery(ctx: Context, bmp: Bitmap, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GhiNhanh")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}
