package core

import android.content.Context
import android.text.Html
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.ramotion.foldingcell.FoldingCell
import gs.presentation.LayoutViewBinder
import org.blokada.R
import java.util.*

data class Slot(
        val type: SlotType,
        val payload: Any,
        val date: Date = Date()
)

enum class SlotType {
    GENERIC, FORWARD, BLOCK
}

class SlotVB(val slot: Slot): LayoutViewBinder(R.layout.slotview) {
    override fun attach(view: View) {
        view as SlotView
        view.slot = slot
    }
}

class SlotView(
        ctx: Context,
        attributeSet: AttributeSet
) : FrameLayout(ctx, attributeSet) {

    init {
        inflate(context, R.layout.slotview_content, this)
    }

    private val foldingView = getChildAt(0) as FoldingCell
    private val textView = findViewById<TextView>(R.id.folded_text)
    private val unfoldedTextView = findViewById<TextView>(R.id.unfolded_header)
    private val iconView = findViewById<ImageView>(R.id.folded_icon)
    private val actionIconView = findViewById<ImageView>(R.id.unfolded_action_icon)
    private val actionTextView = findViewById<TextView>(R.id.unfolded_action_text)

    init {
        setOnClickListener {
            foldingView.toggle(false)
        }
    }

    var slot: Slot? = null
        set(value) {
            field = value
            value?.apply { bind(this) }
        }

    private fun bind(slot: Slot) = when (slot.type) {
        SlotType.GENERIC -> {
            iconView.setImageResource(R.drawable.ic_info)
            iconView.setColorFilter(resources.getColor(R.color.colorActive))
            textView.text = slot.payload.toString()
            unfoldedTextView.text = slot.payload.toString()
        }
        SlotType.FORWARD -> {
            iconView.setImageResource(R.drawable.ic_arrow_right_circle)
            iconView.setColorFilter(resources.getColor(R.color.colorActive))
            textView.text = Html.fromHtml(context.getString(R.string.dashboard_forwarded, slot.payload.toString()))
            unfoldedTextView.text = slot.payload.toString()

            actionIconView.setImageResource(R.drawable.ic_verified)
            actionTextView.text = "Add to Blacklist"
        }
        SlotType.BLOCK -> {
            iconView.setImageResource(R.drawable.ic_block)
            iconView.setColorFilter(resources.getColor(R.color.colorAccentDark))
            textView.text = Html.fromHtml(context.getString(R.string.dashboard_blocked, slot.payload.toString()))
            unfoldedTextView.text = slot.payload.toString()

            actionIconView.setImageResource(R.drawable.ic_shield_outline)
            actionTextView.text = "Add to Whitelist"
        }
    }
}

