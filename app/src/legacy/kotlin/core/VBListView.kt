package core

import android.content.Context
import android.graphics.Rect
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import gs.presentation.ViewBinder
import org.blokada.R

class VBListView(
        ctx: Context,
        attributeSet: AttributeSet
) : FrameLayout(ctx, attributeSet) {

    init {
        inflate(context, R.layout.vblistview_content, this)
    }

    private val listView = findViewById<RecyclerView>(R.id.list)

    init {
        listView.addItemDecoration(VerticalSpace(context.dpToPx(4)))
    }

    private val adapter = object : RecyclerView.Adapter<ListerViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListerViewHolder {
            val creator = viewCreators[viewType]!!
            val view = creator.createView(context, parent)
            return ListerViewHolder(view, creator)
        }

        override fun onBindViewHolder(holder: ListerViewHolder, position: Int) {
            val oldDash = items[holder.adapterPosition]
            oldDash.detach(holder.view)
            val dash = items[position]
            dash.attach(holder.view)
        }

        override fun onViewRecycled(holder: ListerViewHolder) = holder.creator.detach(holder.view)
        override fun getItemCount() = items.size
        override fun getItemViewType(position: Int) = items[position].viewType
    }

    init {
        val manager = LinearLayoutManager(context)
        manager.stackFromEnd = true
        listView.layoutManager = manager
        listView.adapter = adapter
    }

    private val viewCreators = mutableMapOf<Int, ViewBinder>()
    private val items = mutableListOf<ViewBinder>()

    private data class ListerViewHolder(
            val view: View,
            val creator: ViewBinder
    ): RecyclerView.ViewHolder(view)

    fun add(item: ViewBinder) {
        items.add(item)
        viewCreators[item.viewType] = item
        adapter.notifyItemInserted(items.size - 1)
//        listView.smoothScrollToPosition(items.size - 1)
    }

    class VerticalSpace(val height: Int): RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.top = height
        }
    }
}
