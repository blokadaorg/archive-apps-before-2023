package core

import android.content.Context
import android.os.SystemClock
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ListView
import android.widget.ScrollView
import com.ashokvarma.bottomnavigation.BottomNavigationBar
import com.ashokvarma.bottomnavigation.BottomNavigationItem
import com.github.salomonbrys.kodein.instance
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.yarolegovich.discretescrollview.DiscreteScrollView
import com.yarolegovich.discretescrollview.transform.ScaleTransformer
import gs.environment.inject
import gs.presentation.DashCache
import gs.presentation.doAfter
import kotlinx.android.synthetic.adblockerHome.dashboard.view.*
import org.blokada.R
import tunnel.Events
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

class DashboardView(
        ctx: Context,
        attributeSet: AttributeSet
) : SlidingUpPanelLayout(ctx, attributeSet), Backable {

    private val items = mutableListOf<String>()
    private val manager = LinearLayoutManager(context)
    private data class AViewHolder(val view: DashboardItemView): RecyclerView.ViewHolder(view)
    private val adapterek = object : RecyclerView.Adapter<AViewHolder>() {
        override fun onCreateViewHolder(p0: ViewGroup, p1: Int): AViewHolder {
            val v = LayoutInflater.from(context).inflate(R.layout.dashboard_item, p0, false) as DashboardItemView
            return AViewHolder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(p0: AViewHolder, p1: Int) {
            p0.view.text = items[p1]
        }
    }

    private val animator = object : RecyclerView.ItemAnimator() {
        override fun isRunning(): Boolean {
            return false
        }

        override fun runPendingAnimations() {
        }

        override fun endAnimation(holder: RecyclerView.ViewHolder) {
        }

        override fun endAnimations() {
        }

        override fun animatePersistence(holder: RecyclerView.ViewHolder, p1: ItemHolderInfo,
                                        p2: ItemHolderInfo) = false

        override fun animateDisappearance(holder: RecyclerView.ViewHolder, p1: ItemHolderInfo,
                                          p2: ItemHolderInfo?) = false

        override fun animateChange(holder: RecyclerView.ViewHolder, p1: RecyclerView.ViewHolder,
                                   p2: ItemHolderInfo, p3: ItemHolderInfo) = false

        override fun animateAppearance(holder: RecyclerView.ViewHolder, p1: ItemHolderInfo?,
                                       p2: ItemHolderInfo): Boolean {
            holder.itemView.alpha = 0f
            holder.itemView.animate().alpha(1f).setDuration(500).doAfter {
                dispatchAnimationFinished(holder)
            }
            return true
        }

    }

    private enum class State { INACTIVE, ANCHORED, OPENED, DRAGGING }
    private var state = State.INACTIVE
        set(value) {
            field = value
            when (value) {
                State.INACTIVE -> {
                    bg_colors.onScroll(1f, selectedPosition + 1, 0)
                    fg_nav_primary.alpha = 0f
                    bg_start.alpha = 1f
                    bg_logo.alpha = 0f
                    bg_off_logo.alpha = 1f
                    bg_packets.alpha = 0f
                    bg_info.alpha = 0f
                }
                State.ANCHORED -> {
                    bg_colors.onScroll(1f, 0, selectedPosition + 1)
                    fg_nav_primary.alpha = 1f
                    bg_start.alpha = 0f
                    bg_logo.alpha = 0f
                    bg_off_logo.alpha = 0f
                    bg_packets.alpha = 1f
                    bg_info.alpha = 1f

                    val lp = fg_drag.layoutParams as FrameLayout.LayoutParams
                    lp.height = context.dpToPx(40)
                    lp.topMargin = context.dpToPx(90)
                    fg_drag.layoutParams = lp
                }
                State.OPENED -> {
                    bg_colors.onScroll(1f, 0, selectedPosition + 1)
                    fg_nav_primary.alpha = 1f
                    bg_start.alpha = 0f
                    bg_logo.alpha = 1f
                    bg_off_logo.alpha = 0f
                    bg_packets.alpha = 1f
                    bg_info.alpha = 0f

                    val lp = fg_drag.layoutParams as FrameLayout.LayoutParams
                    lp.height = context.dpToPx(130)
                    lp.topMargin = 0
                    fg_drag.layoutParams = lp
                }
                State.DRAGGING -> {

                }
            }
        }

    private val tunnelEvents by lazy { ctx.inject().instance<EnabledStateActor>() }
    private val tun by lazy { ctx.inject().instance<Tunnel>() }

    private val sections by lazy { createDashboardSections(ctx.ktx("sections.create")) }

    private val mainDashCache = DashCache()
    private var isOpen = false
    private var openDash: gs.presentation.Dash? = null
    private val inter = DecelerateInterpolator(2f)
    private var selectedPosition = 1
    private var scrolledView: View? = null

    private val dateFormat = SimpleDateFormat("HH:mm")

    private val wasBottom = true

    override fun onFinishInflate() {
        super.onFinishInflate()

        panelHeight = context.dpToPx(128)
        shadowHeight = 0
        setDragView(fg_drag)
        isOverlayed = true
        setScrollableView(fg_content)

        addPanelSlideListener(object : PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) {
                if (slideOffset < anchorPoint) {
                    val ratio = slideOffset / anchorPoint
                    bg_colors.onScroll(1 - ratio, selectedPosition + 1, 0)
                    fg_nav_primary.alpha = min(1f, ratio)
                    bg_start.alpha = 1 - min(1f, ratio)
                    bg_packets.alpha = min(1f, ratio)
                    bg_info.alpha = min(1f, ratio)
                }
                else {
                    fg_nav_panel.alpha = max(0.7f, slideOffset)
                    fg_nav_primary.alpha = 1 - min(1f, (slideOffset - anchorPoint) * 3)
                    fg_content.alpha = min(1f, (slideOffset - anchorPoint) * 3)
                    bg_info.alpha = 1 - min(1f, (slideOffset - anchorPoint) * 3)
                    bg_logo.alpha = (slideOffset - anchorPoint) / (1 - anchorPoint)
                }
            }

            override fun onPanelStateChanged(panel: View, previousState: PanelState, newState: PanelState) {
                val ktx = context.ktx("dragstate")
                when (newState) {
                    PanelState.DRAGGING -> {
                        fg_nav_primary.visibility = VISIBLE
                        fg_nav_secondary.visibility = VISIBLE
                        closeSection()
                        fg_nav_secondary.visibility = GONE
                        fg_nav_secondary.clearAll()
                        bg_off_logo.animate().alpha(0f).interpolator = inter
                        stopAnimatingStart()
                        ktx.v("dragging")
                        state = State.DRAGGING
                    }
                    PanelState.ANCHORED -> {
                        ktx.v("anchored")
                        tun.enabled %= true
                        state = State.ANCHORED
                    }
                    PanelState.COLLAPSED -> {
                        ktx.v("collapsed")
                        bg_off_logo.animate().alpha(1f).interpolator = inter
                        animateStart()
                        tun.enabled %= false
                        state = State.INACTIVE
                    }
                    PanelState.EXPANDED -> {
                        ktx.v("expanded")
                        tun.enabled %= true
                        fg_nav_primary.visibility = GONE
                        openSelectedSection()
                        state = State.OPENED
                    }
                }
            }
        })

        val ktx = "dashboard".ktx()
        ktx.on(Events.REQUEST_FORWARDED) {
            bg_packets.addToHistory(ActiveBackgroundItem(it, false, SystemClock.elapsedRealtime()))
            items.add("${dateFormat.format(Date())} Forwarded $it")
            if (wasBottom) bg_info_list.scrollToPosition(items.size - 1)
//            adapterek.notifyDataSetChanged()
            adapterek.notifyItemInserted(items.size - 1)
            if (items.size > 13) {
                items.removeAt(0)
                adapterek.notifyItemRemoved(0)
            }
        }

        ktx.on(Events.REQUEST_BLOCKED) {
            bg_packets.addToHistory(ActiveBackgroundItem(it, true, SystemClock.elapsedRealtime()))
            items.add("${dateFormat.format(Date())} BLOCKED $it")
            if (wasBottom) bg_info_list.scrollToPosition(items.size - 1)
//            adapterek.notifyDataSetChanged()
            adapterek.notifyItemInserted(items.size - 1)
            if (items.size > 13) {
                items.removeAt(0)
                adapterek.notifyItemRemoved(0)
            }
        }

        manager.stackFromEnd = true
        bg_info_list.layoutManager = manager
        bg_info_list.adapter = adapterek
//        bg_info_list.itemAnimator = animator

        tunnelEvents.listeners.add(object : IEnabledStateActorListener {
            override fun startActivating() {
                bg_packets.setTunnelState(TunnelState.ACTIVATING)
                if (panelState == PanelState.COLLAPSED) {
                    panelState = PanelState.ANCHORED
                }
            }

            override fun finishActivating() {
                bg_packets.setTunnelState(TunnelState.ACTIVE)
            }

            override fun startDeactivating() {
                bg_packets.setTunnelState(TunnelState.DEACTIVATING)
            }

            override fun finishDeactivating() {
                bg_packets.setTunnelState(TunnelState.INACTIVE)
                panelState = PanelState.COLLAPSED
            }
        })

        fg_nav_primary.adapter = dashCardsAdapter
        fg_nav_primary.setItemTransitionTimeMillis(100)
        fg_nav_primary.setItemTransformer(ScaleTransformer.Builder().setMinScale(0.5f).build())
        fg_nav_primary.scrollToPosition(selectedPosition)
        fg_nav_primary.addScrollStateChangeListener(object : DiscreteScrollView.ScrollStateChangeListener<ViewHolder> {
            override fun onScroll(scrollPosition: Float, currentPosition: Int, newPosition: Int, currentHolder: ViewHolder?, newCurrent: ViewHolder?) {
                bg_colors.onScroll(Math.abs(scrollPosition), currentPosition + 1, newPosition + 1)
            }

            override fun onScrollStart(currentItemHolder: ViewHolder, adapterPosition: Int) {
                currentItemHolder.view.hideText()
                sections.getOrNull(adapterPosition)?.apply {
                    mainDashCache.detach(main.dash, fg_content)
                    updateScrollableView()
                }
            }

            override fun onScrollEnd(currentItemHolder: ViewHolder, adapterPosition: Int) {
                selectedPosition = adapterPosition
            }
        })

        fg_nav_primary.addOnItemChangedListener(object : DiscreteScrollView.OnItemChangedListener<ViewHolder> {
            override fun onCurrentItemChanged(viewHolder: ViewHolder?, adapterPosition: Int) {
                viewHolder?.apply { view.showText() }
            }
        })

        var resized = false
        viewTreeObserver.addOnGlobalLayoutListener {
            if (!resized) {
                resize()
                resized = true
            }
        }

        bg_packets.setTunnelState(tun.tunnelState())
        if (tun.tunnelState() !in listOf(TunnelState.DEACTIVATED, TunnelState.INACTIVE)) {
            panelState = PanelState.ANCHORED
        } else {
            animateStart()
        }
    }

    private fun resize() {
//        val layoutParams = fg_drag.layoutParams as FrameLayout.LayoutParams
//        layoutParams.width = width * widthMultiplier
//        fg_drag.layoutParams = layoutParams
        val percentHeight = context.dpToPx(130).toFloat() / height
        anchorPoint = percentHeight
    }

    private fun animateStart() {
        val anim = AlphaAnimation(0.2f, 1f)
        anim.duration = 800
        anim.repeatCount = Animation.INFINITE
        anim.repeatMode = Animation.REVERSE
        bg_start_text.startAnimation(anim)
    }

    private fun stopAnimatingStart() {
        bg_start_text.clearAnimation()
    }

    private fun onOpenSection(after: () -> Unit) {
        fg_content.visibility = View.VISIBLE
        after()
        fg_placeholder.animate().setDuration(1000).alpha(0f).doAfter {
            fg_placeholder.visibility = View.GONE
        }
        fg_placeholder.visibility = View.VISIBLE
        fg_placeholder.alpha = 0f
        fg_placeholder.animate().setInterpolator(inter).setDuration(400).alpha(1.0f).translationY(0f)
    }

    private fun onCloseSection() {
        fg_content.visibility = View.GONE
        fg_placeholder.alpha = 0f
    }

    fun flashPlaceholder() {
        fg_placeholder.alpha = 1f
        fg_placeholder.visibility = View.VISIBLE
        fg_placeholder.animate().setDuration(1000).alpha(0f).doAfter {
            fg_placeholder.visibility = View.GONE
        }
    }

    private fun updateScrollableView() {
        scrolledView = try {
            val child = fg_content.getChildAt(0)
            when (child) {
                is Scrollable -> child.getScrollableView()
                is ScrollView -> child
                is RecyclerView -> child
                is ListView -> child
                is GridView -> child
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        setScrollableView(scrolledView)
    }

    override fun handleBackPressed(): Boolean {
        val dash = openDash
        if (dash is Backable && dash.handleBackPressed()) return true
        openDash = null

        if (isOpen) {
            panelState = PanelState.ANCHORED
            return true
        }
        return false
    }

    private fun closeSection() {
        isOpen = false
        onCloseSection()
    }

    private fun openSelectedSection() {
        isOpen = true
        fg_nav_primary.animate().setDuration(200).alpha(0f).doAfter {
            fg_nav_primary.visibility = View.GONE
        }

        sections.getOrNull(selectedPosition)?.apply {
            fg_placeholder_title.text = context.getString(main.nameResId)
            fg_placeholder_icon.setImageResource(main.iconResId)
        }

        onOpenSection {
            sections.getOrNull(selectedPosition)?.apply {
                mainDashCache.use(main.dash, context, fg_content)
                updateScrollableView()
                openDash = main.dash

                if (subsections.isNotEmpty()) {
                    fg_nav_secondary.addItem(BottomNavigationItem(main.iconResId, main.nameResId))
                    subsections.forEach {
                        fg_nav_secondary.addItem(BottomNavigationItem(it.iconResId, it.nameResId))
                    }
                    fg_nav_secondary.initialise()
                    fg_nav_secondary.visibility = View.VISIBLE
                    fg_nav_secondary.animate().setDuration(200).alpha(1f)
                    fg_nav_secondary.setTabSelectedListener(object: BottomNavigationBar.OnTabSelectedListener {
                        override fun onTabReselected(position: Int) = Unit

                        override fun onTabUnselected(position: Int) = Unit

                        override fun onTabSelected(position: Int) {
                            when (position) {
                                0 -> {
                                    mainDashCache.use(main.dash, context, fg_content)
                                    updateScrollableView()
                                    openDash = main.dash
                                    fg_placeholder_title.text = context.getString(main.nameResId)
                                    fg_placeholder_icon.setImageResource(main.iconResId)
                                    flashPlaceholder()
                                }
                                else -> {
                                    subsections.getOrNull(position - 1)?.apply {
                                        mainDashCache.use(dash, context, fg_content)
                                        updateScrollableView()
                                        openDash = dash
                                        fg_placeholder_title.text = context.getString(nameResId)
                                        fg_placeholder_icon.setImageResource(iconResId)
                                        flashPlaceholder()
                                    }
                                }
                            }
                        }
                    })
                }
            }

            (openDash as? Scrollable)?.apply {
                setOnScroll(
                        onScrollDown = {
                            fg_nav_secondary.animate().setDuration(100).translationY(300f)
                        },
                        onScrollUp = {
                            fg_nav_secondary.animate().setDuration(100).translationY(0f)
                        }
                )
            }
        }
    }

    private data class ViewHolder(val view: DashboardNavItemView): RecyclerView.ViewHolder(view)

    var i = 0
    private val dashCardsAdapter = object : RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.dashboard_nav_item, parent, false)
                    as DashboardNavItemView
            view.setOnClickListener {
                val clicked = view.tag as Int
                if (clicked == fg_nav_primary.currentItem) {
                    panelState = PanelState.EXPANDED
                } else fg_nav_primary.smoothScrollToPosition(view.tag as Int)
            }
            return ViewHolder(view)
        }

        override fun getItemCount() = sections.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            sections.getOrNull(position)?.apply {
                holder.view.iconResId = main.iconResId
                holder.view.text = ctx.getString(main.nameResId)
                holder.view.tag = position
            }
        }
    }

}
