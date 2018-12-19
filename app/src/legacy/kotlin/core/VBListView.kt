package core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
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

    var onItemRemove = { item: ViewBinder -> }
    var onEndReached = { }

    init {
        inflate(context, R.layout.vblistview_content, this)
    }

    private val listView = findViewById<RecyclerView>(R.id.list)
    private val containerView = findViewById<ConstraintLayout>(R.id.container)
    private var layoutManager = LinearLayoutManager(context)

    init {
        listView.addItemDecoration(VerticalSpace(context.dpToPx(6)))
        listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (!recyclerView.canScrollVertically(1)) {
                    onEndReached()
                }
            }
        })
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

    private val touchHelper = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.END) {
        override fun onMove(p0: RecyclerView, p1: RecyclerView.ViewHolder, p2: RecyclerView.ViewHolder) = false

        override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
            onItemRemove(items[holder.adapterPosition])
            items.removeAt(holder.adapterPosition)
            adapter.notifyItemRemoved(holder.adapterPosition)
        }

        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                                 dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
            viewHolder.itemView.alpha = 1f - (dX / viewHolder.itemView.width) * 2
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    init {
        layoutManager.stackFromEnd = true
        listView.layoutManager = layoutManager
        listView.adapter = adapter
//        ItemTouchHelper(touchHelper).attachToRecyclerView(listView)
    }

    private val viewCreators = mutableMapOf<Int, ViewBinder>()
    private val items = mutableListOf<ViewBinder>()

    private data class ListerViewHolder(
            val view: View,
            val creator: ViewBinder
    ): RecyclerView.ViewHolder(view)

    fun add(item: ViewBinder, position: Int = -1) {
        viewCreators[item.viewType] = item
        if (position == -1) {
            items.add(item)
            adapter.notifyItemInserted(items.size - 1)
        } else {
            val firstWasVisible = layoutManager.findFirstCompletelyVisibleItemPosition() == 0
            items.add(position, item)
            adapter.notifyItemInserted(position)
            if (firstWasVisible) listView.smoothScrollToPosition(0)
        }
//        listView.smoothScrollToPosition(items.size - 1)
    }

    fun remove(item: ViewBinder) {
        val position = items.indexOf(item)
        items.remove(item)
        adapter.notifyItemRemoved(position)
    }

    fun set(items: List<ViewBinder>) {
        this.items.clear()
        this.items.addAll(items)
        items.forEach { viewCreators[it.viewType] = it }
        adapter.notifyDataSetChanged()
//        listView.smoothScrollToPosition(items.size - 1)
    }

    fun enableAlternativeMode() {
        layoutManager = LinearLayoutManager(context)
        listView.layoutManager = layoutManager

//        val lp = containerView.layoutParams as FrameLayout.LayoutParams
//        lp.marginEnd = 0
//        lp.marginStart = 0
//        containerView.layoutParams = lp
    }

    class VerticalSpace(val height: Int): RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.top = height
        }
    }
}
