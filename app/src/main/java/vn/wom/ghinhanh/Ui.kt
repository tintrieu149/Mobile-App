package vn.wom.ghinhanh

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

const val BLUE = 0xFF1565C0.toInt()
const val GREY = 0xFFE3E7EC.toInt()

/** Nút gọn; chọn rồi thì tô xanh. */
fun Context.chip(label: String, onClick: () -> Unit): MaterialButton =
    MaterialButton(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        insetTop = 0
        insetBottom = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(12), 0, dp(12), 0)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        setOnClickListener { onClick() }
        mark(false)
    }

fun MaterialButton.mark(selected: Boolean) {
    setBackgroundColor(if (selected) BLUE else GREY)
    setTextColor(if (selected) Color.WHITE else Color.BLACK)
}

fun Context.colorDot(color: Int, selected: Boolean): View =
    View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(dp(6), dp(4), dp(6), dp(4)) }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(if (selected) 4 else 1), if (selected) BLUE else 0xFF9AA4AF.toInt())
        }
    }
