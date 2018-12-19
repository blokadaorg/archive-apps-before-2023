package core

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import org.blokada.R

data class DashboardItem(
        val iconResId: Int,
        val nameResId: Int,
        val dash: gs.presentation.Dash
)

class DashboardItemView(
        ctx: Context,
        attributeSet: AttributeSet
) : RelativeLayout(ctx, attributeSet) {

    var iconResId: Int = 0
        set(value) {
            field = value
            iconView.setImageResource(value)
        }

    var text: String = ""
        set(value) {
            field = value
            textView.text = value
        }

    private val iconView by lazy { findViewById<ImageView>(R.id.icon) }
    private val textView by lazy { findViewById<TextView>(R.id.text) }

    override fun onFinishInflate() {
        super.onFinishInflate()
    }
}
