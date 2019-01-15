package core

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.ramotion.foldingcell.FoldingCell
import gs.presentation.LayoutViewBinder
import org.blokada.R
import java.util.*

class Slot {
    enum class Type {
        INFO, FORWARD, BLOCK, COUNTER, STATUS, NEW, EDIT, APP
    }

    data class Action(val name: String, val callback: () -> Unit)

    data class Content(
            val label: String,
            val header: String = label,
            val description: String? = null,
            val detail: String? = null,
            val icon: Drawable? = null,
            val action1: Action? = null,
            val action2: Action? = null,
            val action3: Action? = null,
            val switched: Boolean? = null,
            val values: List<String> = emptyList(),
            val selected: String? = null
    )
}

/**
 * SlotMutex is used to make sure only one slot in a list can be openedat a time. It is optional.
 */
data class SlotMutex(internal var view: SlotView? = null)

abstract class SlotVB(private val slotMutex: SlotMutex = SlotMutex()) : LayoutViewBinder(R.layout.slotview) {

    override fun attach(view: View) {
        view as SlotView
        view.onTap = {
            val openedView = slotMutex.view
            when {
                openedView == null || !openedView.isUnfolded() -> {
                    slotMutex.view = view
                    view.unfold()
                }
                openedView.isUnfolded() -> {
                    openedView.fold()
                    view.onClose()
                }
            }
        }
    }

    override fun detach(view: View) {
        view as SlotView
        view.unbind()
        slotMutex.view = null
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
    private val foldedContainerView = findViewById<ViewGroup>(R.id.folded)
    private val unfoldedContainerView = findViewById<ViewGroup>(R.id.unfolded)
    private val textView = findViewById<TextView>(R.id.folded_text)
    private val iconView = findViewById<ImageView>(R.id.folded_icon)
    private val timeView = findViewById<TextView>(R.id.folded_time)
    private val headerView = findViewById<TextView>(R.id.unfolded_header)
    private val descriptionView = findViewById<TextView>(R.id.unfolded_description)
    private val editView = findViewById<EditText>(R.id.unfolded_edit)
    private val detailView = findViewById<TextView>(R.id.unfolded_detail)
    private val switchFoldedView = findViewById<TextView>(R.id.folded_switch)
    private val switchUnfoldedView = findViewById<TextView>(R.id.unfolded_switch)
    private val action0View = findViewById<Button>(R.id.unfolded_action0)
    private val action1View = findViewById<Button>(R.id.unfolded_action1)
    private val action2View = findViewById<Button>(R.id.unfolded_action2)
    private val action3View = findViewById<Button>(R.id.unfolded_action3)
    private val switchViews = listOf(switchFoldedView, switchUnfoldedView)

    init {
        setOnClickListener { onTap() }
        action0View.setOnClickListener { onTap() }
        editView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val error = onInput(s.toString())
                if (error != null) {
                    descriptionView.apply {
                        visibility = View.VISIBLE
                        text = error
                        setTextColor(resources.getColor(R.color.colorAccentDark))
                    }
                    switchViews.forEach {
                        it.visibility = View.VISIBLE
                        it.setText(R.string.slot_invalid)
                        it.setTextColor(resources.getColor(R.color.colorAccentDark))
                    }
                } else {
                    descriptionView.apply {
                        visibility = View.GONE
                    }
                    switchViews.forEach {
                        it.visibility = View.VISIBLE
                        it.setText(R.string.slot_set)
                        it.setTextColor(resources.getColor(R.color.switch_on))
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
        })
    }

    var type: Slot.Type? = null
        set(value) {
            field = value
            value?.apply { bind(this) }
        }

    var content: Slot.Content? = null
        set(value) {
            field = value
            value?.apply { bind(this) }
            foldingView.fold(true)
            timeRefreshHandler.sendEmptyMessage(0)
        }

    var date: Date? = null
        set(value) {
            field = value
            content?.apply { bind(this) }
            timeRefreshHandler.sendEmptyMessage(0)
        }

    var input: String = ""
        set(value) {
            field = value
            editView.setText(value, TextView.BufferType.EDITABLE)
        }

    var onTap = {}
        set(value) {
            field = value
            setOnClickListener { value() }
        }

    var onClose = {}
    var onSwitch = { _: Boolean -> }
    var onSelect = { _: String -> }
    var onInput: (String) -> String? = { _: String -> null }

    fun fold() = foldingView.fold(false)
    fun unfold() = foldingView.unfold(false)
    fun isUnfolded() = foldingView.isUnfolded

    fun enableAlternativeBackground() {
        foldingView.initialize(1000, resources.getColor(R.color.colorBackgroundLight), 0)
        foldedContainerView.setBackgroundResource(R.drawable.bg_dashboard_item_alternative)
        unfoldedContainerView.setBackgroundResource(R.drawable.bg_dashboard_item_unfolded_alternative)
    }

    private fun bind(content: Slot.Content) {
        textView.text = Html.fromHtml(content.label)
        when {
            date != null -> switchViews.forEach { it.visibility = View.GONE }
            content.values.isNotEmpty() && content.selected in content.values -> switchViews.forEach {
                it.visibility = View.VISIBLE
                it.text = content.selected
                it.setTextColor(resources.getColor(R.color.switch_off))
            }
            type == Slot.Type.EDIT && content.switched == null -> switchViews.forEach {
                it.visibility = View.VISIBLE
                it.text = context.getString(R.string.slot_unset)
                it.setTextColor(resources.getColor(R.color.switch_off))
            }
            content.switched == null -> switchViews.forEach { it.visibility = View.GONE }
            content.switched -> switchViews.forEach {
                it.visibility = View.VISIBLE
                it.setText(R.string.slot_switch_on)
                it.setTextColor(resources.getColor(R.color.switch_on))
            }
            else -> switchViews.forEach {
                it.visibility = View.VISIBLE
                it.setText(R.string.slot_switch_off)
                it.setTextColor(resources.getColor(R.color.switch_off))
            }
        }

        headerView.text = Html.fromHtml(content.header)
        if (content.description != null) descriptionView.text = Html.fromHtml(content.description)
        if (content.detail != null) {
            detailView.visibility = View.VISIBLE
            detailView.text = content.detail
        } else detailView.visibility = View.GONE

        if (content.icon != null) {
            iconView.setImageDrawable(content.icon)
        }

        bind(content.action1, content.action2, content.action3)
    }

    private fun bind(type: Slot.Type) {
        iconView.setColorFilter(resources.getColor(R.color.colorActive))
        editView.visibility = View.GONE
        when (type) {
            Slot.Type.INFO -> {
                iconView.setImageResource(R.drawable.ic_info)
            }
            Slot.Type.FORWARD -> {
                iconView.setImageResource(R.drawable.ic_arrow_right_circle)
            }
            Slot.Type.BLOCK -> {
                iconView.setImageResource(R.drawable.ic_block)
                iconView.setColorFilter(resources.getColor(R.color.colorAccent))
            }
            Slot.Type.COUNTER -> {
                iconView.setImageResource(R.drawable.ic_counter)
            }
            Slot.Type.STATUS -> {
                iconView.setImageResource(R.drawable.ic_power)
            }
            Slot.Type.NEW -> {
                iconView.setImageResource(R.drawable.ic_filter_add)
                iconView.setColorFilter(resources.getColor(R.color.colorAccent))
            }
            Slot.Type.EDIT -> {
                iconView.setImageResource(R.drawable.ic_edit)
                editView.visibility = View.VISIBLE
            }
            Slot.Type.APP -> {
                iconView.setColorFilter(resources.getColor(android.R.color.transparent))
            }
        }
    }

    private fun bind(action1: Slot.Action?, action2: Slot.Action?, action3: Slot.Action?) {
        listOf(action0View, action1View, action2View, action3View).forEach {
            it.visibility = View.GONE
        }

        when {
            action1  == null && (content?.values?.size ?: 0) > 1 -> {
                // Set action that rolls through possible values
                val c = content!!
                val nextValueIndex = (c.values.indexOf(c.selected) + 1) % c.values.size
                val nextValue = c.values[nextValueIndex]
                bind(Slot.Action(nextValue, {
                    content = c.copy(selected = nextValue)
                    onSelect(nextValue)
                }), action1View)
                bind(action2, action2View)
                bind(action3, action3View)
            }
            action1 == null && content?.switched != null -> {
                // Set action that switches between two boolean values
                val c = content!!
                val nextValue = !(c.switched!!)
                val nextValueName = resources.getString(if (nextValue) R.string.slot_switch_on
                        else R.string.slot_switch_off)
                bind(Slot.Action(nextValueName, {
                    content = c.copy(switched = nextValue)
                    onSwitch(nextValue)
                }), action1View)
                bind(action2, action2View)
                bind(action3, action3View)
            }
            action1 == null -> {
                // Show the default "close" action
                action0View.visibility = View.VISIBLE
            }
            else -> {
                listOf(
                        action1 to action1View,
                        action2 to action2View,
                        action3 to action3View
                ).forEach { bind(it.first, it.second) }
            }
        }
    }

    private fun bind(action: Slot.Action?, view: TextView) {
        if (action != null) {
            view.visibility = View.VISIBLE
            view.text = action.name
            view.setOnClickListener { action.callback() }
        } else view.visibility = View.GONE
    }

    fun unbind() = timeRefreshHandler.removeMessages(0)

    private val timeRefreshHandler = Handler {
        if (date != null) {
            timeView.visibility = View.VISIBLE
            timeView.text = DateUtils.getRelativeTimeSpanString(date?.time!!, Date().time,
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
            scheduleTimeRefresh()
        } else
            timeView.visibility = View.GONE
        true
    }

    private fun scheduleTimeRefresh() {
        timeRefreshHandler.sendEmptyMessageDelayed(0, 60 * 1000)
    }
}

