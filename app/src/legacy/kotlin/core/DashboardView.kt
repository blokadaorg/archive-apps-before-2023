package core

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.support.v4.view.ViewPager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ListView
import android.widget.ScrollView
import com.github.salomonbrys.kodein.instance
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import gs.environment.inject
import gs.presentation.DashCache
import gs.presentation.doAfter
import kotlinx.android.synthetic.adblockerHome.dashboard.view.*
import tunnel.Events
import kotlin.math.max
import kotlin.math.min

class DashboardView(
        ctx: Context,
        attributeSet: AttributeSet
) : SlidingUpPanelLayout(ctx, attributeSet), Backable {

    private enum class State { INACTIVE, ANCHORED, OPENED, DRAGGING }

    private var state = State.INACTIVE
        set(value) {
            field = value
            when (value) {
                State.INACTIVE -> {
                    bg_colors.onScroll(1f, selectedPosition + 1, 0)
                    fg_nav_primary.alpha = 0f
                    fg_logo_icon.alpha = 0f
                    bg_start.alpha = 1f
                    bg_logo.alpha = 0f
                    bg_off_logo.alpha = 1f
                    bg_packets.alpha = 0f
                    fg_pager.alpha = 0f
                    bg_pager.alpha = 0f

                    val lp = fg_drag.layoutParams as FrameLayout.LayoutParams
                    lp.height = LayoutParams.MATCH_PARENT
                    lp.topMargin = 0
                    fg_drag.layoutParams = lp
                }
                State.ANCHORED -> {
                    bg_colors.onScroll(1f, 0, selectedPosition + 1)
                    fg_nav_primary.alpha = 1f
                    fg_logo_icon.alpha = 0f
                    bg_start.alpha = 0f
                    bg_logo.alpha = 0f
                    bg_off_logo.alpha = 0f
                    bg_packets.alpha = 1f
                    fg_pager.alpha = 0f
                    bg_pager.alpha = 1f

                    val lp = fg_drag.layoutParams as FrameLayout.LayoutParams
                    lp.height = context.dpToPx(44)
                    lp.topMargin = context.dpToPx(90)
                    fg_drag.layoutParams = lp
                }
                State.OPENED -> {
                    bg_colors.onScroll(1f, 0, selectedPosition + 1)
                    fg_nav_primary.alpha = 0f
                    fg_logo_icon.alpha = 0f
                    bg_start.alpha = 0f
                    bg_logo.alpha = 1f
                    bg_off_logo.alpha = 0f
                    bg_packets.alpha = 1f
                    fg_pager.alpha = 1f
                    bg_pager.alpha = 0f

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
    private var openDash: gs.presentation.ViewBinder? = null
    private val inter = DecelerateInterpolator(2f)
    private var selectedPosition = 0
    private var scrolledView: View? = null

    override fun onFinishInflate() {
        super.onFinishInflate()

        panelHeight = context.dpToPx(128)
        shadowHeight = 0
        setDragView(fg_drag)
        isOverlayed = true
//        setScrollableView(fg_content)

        bg_pager.setOnClickListener { openSelectedSection() }

        addPanelSlideListener(object : PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) {
                if (slideOffset < anchorPoint) {
                    val ratio = slideOffset / anchorPoint
                    bg_colors.onScroll(1 - ratio, selectedPosition + 1, 0)
                    fg_nav_primary.alpha = min(1f, ratio)
                    bg_start.alpha = 1 - min(1f, ratio)
                    bg_packets.alpha = min(1f, ratio)
                    bg_pager.alpha = min(1f, ratio)
                } else {
                    fg_nav_panel.alpha = max(0.7f, slideOffset)
                    fg_nav_primary.alpha = 1 - min(1f, (slideOffset - anchorPoint) * 3)
//                    fg_pager.alpha = min(1f, (slideOffset - anchorPoint) * 3)
                    bg_pager.alpha = 1 - min(1f, (slideOffset - anchorPoint) * 3)
                    bg_logo.alpha = (slideOffset - anchorPoint) / (1 - anchorPoint)
                }
            }

            override fun onPanelStateChanged(panel: View, previousState: PanelState, newState: PanelState) {
                val ktx = context.ktx("dragstate")
                when (newState) {
                    PanelState.DRAGGING -> {
                        fg_nav_primary.visibility = VISIBLE
//                        fg_nav_secondary.visibility = VISIBLE
                        closeSection()
//                        fg_nav_secondary.visibility = GONE
//                        fg_nav_secondary.clearAll()
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
        }

        ktx.on(Events.REQUEST_BLOCKED) {
            bg_packets.addToHistory(ActiveBackgroundItem(it, true, SystemClock.elapsedRealtime()))
        }

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

        bg_pager.pages = sections.map { DashboardSectionVB(it) }

        fg_nav_primary.viewPager = bg_pager
        fg_nav_primary.sleeping = true
        fg_nav_primary.sleepingListener = { sleeping ->
            if (sleeping) {
                fg_nav_primary.animate().setDuration(500).alpha(0f)
                fg_logo_icon.animate().setDuration(500).alpha(0.2f)
            } else {
                fg_nav_primary.animate().setDuration(200).alpha(1f)
                fg_logo_icon.animate().setDuration(200).alpha(0f)
            }
        }
        fg_nav_primary.section = context.getText(sections[selectedPosition].nameResId)
        bg_pager.setCurrentItem(0)
//        fg_nav_primary.adapter = dashCardsAdapter
//        fg_nav_primary.setItemTransitionTimeMillis(100)
//        fg_nav_primary.setItemTransformer(ScaleTransformer.Builder().setMinScale(0.5f).build())
//        fg_nav_primary.scrollToPosition(selectedPosition)

//        fg_nav_primary.addScrollStateChangeListener(object : DiscreteScrollView.ScrollStateChangeListener<ViewHolder> {
//            override fun onScroll(scrollPosition: Float, currentPosition: Int, newPosition: Int, currentHolder: ViewHolder?, newCurrent: ViewHolder?) {
//                bg_colors.onScroll(Math.abs(scrollPosition), currentPosition + 1, newPosition + 1)
//            }
//
//            override fun onScrollStart(currentItemHolder: ViewHolder, adapterPosition: Int) {
//                currentItemHolder.view.hideText()
//                sections.getOrNull(adapterPosition)?.apply {
//                    mainDashCache.detach(main.dash, fg_content)
//                    updateScrollableView()
//                }
//            }
//
//            override fun onScrollEnd(currentItemHolder: ViewHolder, adapterPosition: Int) {
//                selectedPosition = adapterPosition
//            }
//        })

//        fg_nav_primary.addOnItemChangedListener(object : DiscreteScrollView.OnItemChangedListener<ViewHolder> {
//            override fun onCurrentItemChanged(viewHolder: ViewHolder?, adapterPosition: Int) {
//                viewHolder?.apply { view.showText() }
//            }
//        })

        bg_pager.setOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, posPixels: Int) {
                val next = if (position == selectedPosition) position - 1 else position
                val off = if (positionOffset == 0f) 1f else 1f - positionOffset
//                val off = positionOffset
                bg_colors.onScroll(off, selectedPosition + 1, next + 1)
            }

            override fun onPageSelected(position: Int) {
                selectedPosition = position
                fg_nav_primary.section = context.getText(sections[selectedPosition].nameResId)
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
        val percentHeight = context.dpToPx(80).toFloat() / height
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
        fg_pager.visibility = View.VISIBLE
        fg_nav_secondary.visibility = View.VISIBLE
        after()
    }

    private fun onCloseSection() {
        fg_pager.visibility = View.GONE
        fg_nav_secondary.visibility = View.GONE
    }

    private var showTime = 3000L

    fun flashPlaceholder() {
        fg_nav_secondary.visibility = View.VISIBLE
//        fg_nav_secondary.animate().setDuration(200).translationY(0f)
        hidePlaceholder.removeMessages(0)
        hidePlaceholder.sendEmptyMessageDelayed(0, showTime)
        showTime = max(2000L, showTime - 500L)
    }

    private val hidePlaceholder = Handler {
//        fg_nav_secondary.animate().setDuration(500).translationY(500f)
        true
    }

    override fun setDragView(dragView: View?) {
        super.setDragView(dragView)
        dragView?.apply {
            setOnClickListener {
                when {
                    !isEnabled || !isTouchEnabled -> Unit
                    panelState == PanelState.EXPANDED -> panelState = PanelState.ANCHORED
//                    else -> panelState = PanelState.EXPANDED
                }
            }
        }
    }

    private fun updateScrollableView() {
        scrolledView = try {
            val child = fg_pager.getChildAt(0)
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
            fg_nav_secondary.section = context.getString(nameResId)
        }

        onOpenSection {
            sections.getOrNull(selectedPosition)?.apply {

                fg_pager.pages = subsections.map {
                    it.dash
                }

                fg_pager.setOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                    override fun onPageScrollStateChanged(state: Int) {
                    }

                    override fun onPageScrolled(position: Int, positionOffset: Float, posPixels: Int) {
                    }

                    override fun onPageSelected(position: Int) {
                        val section = sections[selectedPosition].subsections[position]
                        fg_nav_secondary.section = context.getText(section.nameResId)
                        flashPlaceholder()
                    }
                })
                fg_nav_secondary.viewPager = fg_pager
                fg_nav_secondary.background = true
                fg_nav_secondary.sleeping = true

//                mainDashCache.use(subsections.first().dash, context, fg_pager)
//                updateScrollableView()
                openDash = subsections.first().dash

                flashPlaceholder()

                if (subsections.isNotEmpty()) {
//                    fg_nav_secondary.addItem(BottomNavigationItem(main.iconResId, main.nameResId))
//                    subsections.forEach {
//                        fg_nav_secondary.addItem(BottomNavigationItem(it.iconResId, it.nameResId))
//                    }
//                    fg_nav_secondary.initialise()
//                    fg_nav_secondary.visibility = View.VISIBLE
//                    fg_nav_secondary.animate().setDuration(200).alpha(1f)
//                    fg_nav_secondary.setTabSelectedListener(object: BottomNavigationBar.OnTabSelectedListener {
//                        override fun onTabReselected(position: Int) = Unit
//
//                        override fun onTabUnselected(position: Int) = Unit
//
//                        override fun onTabSelected(position: Int) {
//                            when (position) {
//                                0 -> {
//                                    mainDashCache.use(main.dash, context, fg_content)
//                                    updateScrollableView()
//                                    openDash = main.dash
//                                    fg_placeholder_title.text = context.getString(main.nameResId)
//                                    fg_placeholder_icon.setImageResource(main.iconResId)
//                                    flashPlaceholder()
//                                }
//                                else -> {
//                                    subsections.getOrNull(position - 1)?.apply {
//                                        mainDashCache.use(dash, context, fg_content)
//                                        updateScrollableView()
//                                        openDash = dash
//                                        fg_placeholder_title.text = context.getString(nameResId)
//                                        fg_placeholder_icon.setImageResource(iconResId)
//                                        flashPlaceholder()
//                                    }
//                                }
//                            }
//                        }
//                    })
                }
            }

            (openDash as? Scrollable)?.apply {
                setOnScroll(
                        onScrollDown = {
                            //                            fg_nav_secondary.animate().setDuration(100).translationY(300f)
                        },
                        onScrollUp = {
                            //                            fg_nav_secondary.animate().setDuration(100).translationY(0f)
                        }
                )
            }
        }
    }

}
