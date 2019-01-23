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
import android.widget.*
import com.github.salomonbrys.kodein.instance
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import gs.environment.inject
import gs.presentation.DashCache
import gs.presentation.doAfter
import org.blokada.R
import tunnel.Events
import kotlin.math.max
import kotlin.math.min

class DashboardView(
        ctx: Context,
        attributeSet: AttributeSet
) : FrameLayout(ctx, attributeSet), Backable {

    init {
        inflate(context, R.layout.dashboard_content, this)
    }

    private val sliding = findViewById<SlidingUpPanelLayout>(R.id.panel)
    private val bg_colors = findViewById<ColorfulBackground>(R.id.bg_colors)
    private val bg_nav = findViewById<DotsView>(R.id.bg_nav)
    private val bg_start = findViewById<LinearLayout>(R.id.bg_start)
    private val bg_logo = findViewById<LinearLayout>(R.id.bg_logo)
    private val bg_off_logo = findViewById<ImageView>(R.id.bg_off_logo)
    private val bg_pager = findViewById<VBPagesView>(R.id.bg_pager)
    private val bg_packets = findViewById<PacketsView>(R.id.bg_packets)
    private val bg_start_text = findViewById<TextView>(R.id.bg_start_text)
    private val fg_logo_icon = findViewById<ImageView>(R.id.fg_logo_icon)
    private val fg_pager = findViewById<VBPagesView>(R.id.fg_pager)
    private val fg_drag = findViewById<View>(R.id.fg_drag)
    private val fg_nav_panel = findViewById<View>(R.id.fg_nav_panel)
    private val fg_nav = findViewById<DotsView>(R.id.fg_nav)

    private enum class State { INACTIVE, ANCHORED, OPENED, DRAGGING }

    private var state = State.INACTIVE
        set(value) {
            field = value
            when (value) {
                State.INACTIVE -> {
                    bg_colors.onScroll(1f, openSection + 1, 0)
                    bg_nav.alpha = 0f
                    fg_logo_icon.alpha = 0f
                    bg_start.alpha = 1f
                    bg_logo.alpha = 0f
                    bg_off_logo.alpha = 1f
                    bg_packets.alpha = 0f
                    fg_pager.alpha = 0f
                    bg_pager.alpha = 0f
                    bg_pager.visibility = View.GONE

                    val lp = fg_drag.layoutParams as FrameLayout.LayoutParams
                    lp.height = LayoutParams.MATCH_PARENT
                    lp.topMargin = 0
                    fg_drag.layoutParams = lp
                }
                State.ANCHORED -> {
                    bg_colors.onScroll(1f, 0, openSection + 1)
                    bg_nav.alpha = 1f
                    fg_logo_icon.alpha = 0.7f
                    bg_start.alpha = 0f
                    bg_logo.alpha = 0f
                    bg_off_logo.alpha = 0f
                    bg_packets.alpha = 1f
                    fg_pager.alpha = 0f
                    bg_pager.visibility = View.VISIBLE
                    bg_pager.alpha = 1f

                    val lp = fg_drag.layoutParams as FrameLayout.LayoutParams
                    lp.height = context.dpToPx(110)
                    lp.topMargin = context.dpToPx(90)
                    fg_drag.layoutParams = lp
                }
                State.OPENED -> {
                    bg_colors.onScroll(1f, 0, openSection + 1)
                    bg_nav.alpha = 0f
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
    private var openSection = 1
    private var scrolledView: View? = null

    override fun onFinishInflate() {
        super.onFinishInflate()

        bg_pager.setOnClickListener { openSelectedSection() }
        setDragView(fg_drag)

        sliding.apply {
            panelHeight = context.dpToPx(128)
            shadowHeight = 0
            isOverlayed = true

            addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
                override fun onPanelSlide(panel: View?, slideOffset: Float) {
                    if (slideOffset < anchorPoint) {
                        val ratio = slideOffset / anchorPoint
                        bg_colors.onScroll(1 - ratio, openSection + 1, 0)
                        bg_nav.alpha = min(1f, ratio)
                        bg_start.alpha = 1 - min(1f, ratio)
                        bg_packets.alpha = min(1f, ratio)
                        bg_pager.alpha = min(1f, ratio)
                        fg_logo_icon.alpha = min(0.7f, ratio)
                    } else {
                        fg_nav_panel.alpha = max(0.7f, slideOffset)
                        bg_nav.alpha = 1 - min(1f, (slideOffset - anchorPoint) * 3)
//                    fg_pager.alpha = min(1f, (slideOffset - anchorPoint) * 3)
                        bg_pager.alpha = 1 - min(1f, (slideOffset - anchorPoint) * 3)
                        fg_logo_icon.alpha = 0.7f - min(1f, (slideOffset - anchorPoint) * 0.5f)
                        bg_logo.alpha = (slideOffset - anchorPoint) / (1 - anchorPoint)
                    }
                }

                override fun onPanelStateChanged(panel: View, previousState: SlidingUpPanelLayout.PanelState, newState: SlidingUpPanelLayout.PanelState) {
                    val ktx = context.ktx("dragstate")
                    when (newState) {
                        SlidingUpPanelLayout.PanelState.DRAGGING -> {
                            bg_nav.visibility = VISIBLE
//                        fg_nav_secondary.visibility = VISIBLE
                            closeSection()
//                        fg_nav_secondary.visibility = GONE
//                        fg_nav_secondary.clearAll()
                            bg_off_logo.animate().alpha(0f).interpolator = inter
                            stopAnimatingStart()
                            ktx.v("dragging")
                            state = State.DRAGGING
                        }
                        SlidingUpPanelLayout.PanelState.ANCHORED -> {
                            ktx.v("anchored")
                            tun.enabled %= true
                            onSectionClosed()
                            state = State.ANCHORED
                        }
                        SlidingUpPanelLayout.PanelState.COLLAPSED -> {
                            ktx.v("collapsed")
                            bg_off_logo.animate().alpha(1f).interpolator = inter
                            animateStart()
                            tun.enabled %= false
                            state = State.INACTIVE
                        }
                        SlidingUpPanelLayout.PanelState.EXPANDED -> {
                            ktx.v("expanded")
                            tun.enabled %= true
                            bg_nav.visibility = GONE
                            openSelectedSection()
                            state = State.OPENED
                        }
                    }
                }
            })
        }


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
                if (sliding.panelState == SlidingUpPanelLayout.PanelState.COLLAPSED) {
                    sliding.panelState = SlidingUpPanelLayout.PanelState.ANCHORED
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
                sliding.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            }
        })

        bg_pager.pages = sections.map {
            when (it.nameResId) {
                R.string.dashboard_name_explore -> HomeDashboardSectionVB(context)
                R.string.dashboard_settings_name -> AdvancedDashboardSectionVB(context)
                R.string.dashboard_name_apps -> AppsDashboardSectionVB(context)
                else -> DashboardSectionVB(context.ktx("dashboard-section"), it)
            }
        }

        bg_nav.viewPager = bg_pager
        bg_nav.sleeping = true
        bg_nav.section = context.getText(sections[openSection].nameResId)
        bg_nav.sleepingListener = { sleeping ->
            if (sleeping) bg_nav.animate().setDuration(1000).alpha(0f)
            else bg_nav.animate().setDuration(200).alpha(1f)
        }
        bg_pager.setCurrentItem(0)
        bg_pager.offscreenPageLimit = 3
//        fg_nav_primary.adapter = dashCardsAdapter
//        fg_nav_primary.setItemTransitionTimeMillis(100)
//        fg_nav_primary.setItemTransformer(ScaleTransformer.Builder().setMinScale(0.5f).build())
//        fg_nav_primary.scrollToPosition(openSection)

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
//                openSection = adapterPosition
//            }
//        })

//        fg_nav_primary.addOnItemChangedListener(object : DiscreteScrollView.OnItemChangedListener<ViewHolder> {
//            override fun onCurrentItemChanged(viewHolder: ViewHolder?, adapterPosition: Int) {
//                viewHolder?.apply { view.showText() }
//            }
//        })

        bg_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, posPixels: Int) {
                val next = position + 1
                bg_colors.onScroll(positionOffset, next, next + 1)
            }

            override fun onPageSelected(position: Int) {
                openSection = position
                bg_nav.section = makeSectionName(sections[openSection])
                sections.getOrNull(openSection)?.apply {
                    val icon = when (nameResId) {
                        R.string.dashboard_name_ads -> R.drawable.ic_blocked
                        R.string.dashboard_name_apps -> R.drawable.ic_apps
                        R.string.dashboard_settings_name -> R.drawable.ic_tune
                        else -> R.drawable.blokada
                    }
                    fg_logo_icon.animate().setDuration(200).alpha(0f).doAfter {
                        fg_logo_icon.setImageResource(icon)
                        fg_logo_icon.animate().setDuration(200).alpha(0.7f)
                    }
                }
            }
        })

        bg_pager.currentItem = openSection
        bg_nav.section = makeSectionName(sections[openSection])

        var resized = false
        viewTreeObserver.addOnGlobalLayoutListener {
            if (!resized) {
                resize()
                resized = true
            }
        }

        bg_packets.setTunnelState(tun.tunnelState())
        if (tun.tunnelState() !in listOf(TunnelState.DEACTIVATED, TunnelState.INACTIVE)) {
            sliding.panelState = SlidingUpPanelLayout.PanelState.ANCHORED
        } else {
            animateStart()
            state = State.INACTIVE
        }
    }

    private fun resize() {
//        val layoutParams = fg_drag.layoutParams as FrameLayout.LayoutParams
//        layoutParams.width = width * widthMultiplier
//        fg_drag.layoutParams = layoutParams
        val percentHeight = context.dpToPx(80).toFloat() / height
        sliding.anchorPoint = percentHeight
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
        fg_nav.visibility = View.VISIBLE
        after()
    }

    var onSectionClosed = {}

    private fun onCloseSection() {
        fg_pager.visibility = View.GONE
        fg_nav.visibility = View.GONE
    }

    private var showTime = 3000L

    fun flashPlaceholder() {
        fg_nav.visibility = View.VISIBLE
//        fg_nav_secondary.animate().setDuration(200).translationY(0f)
        hidePlaceholder.removeMessages(0)
        hidePlaceholder.sendEmptyMessageDelayed(0, showTime)
        showTime = max(2000L, showTime - 500L)
    }

    private val hidePlaceholder = Handler {
//        fg_nav_secondary.animate().setDuration(500).translationY(500f)
        true
    }

    fun setDragView(dragView: View?) {
        sliding.setDragView(dragView)
        dragView?.apply {
            setOnClickListener {
                when {
                    !isEnabled || !sliding.isTouchEnabled -> Unit
                    sliding.panelState == SlidingUpPanelLayout.PanelState.EXPANDED -> sliding.panelState = SlidingUpPanelLayout.PanelState.ANCHORED
                    else -> sliding.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
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
        sliding.setScrollableView(scrolledView)
    }

    override fun handleBackPressed(): Boolean {
        val dash = openDash
        if (dash is Backable && dash.handleBackPressed()) return true
        openDash = null

        if (isOpen) {
            sliding.panelState = SlidingUpPanelLayout.PanelState.ANCHORED
            return true
        }
        return false
    }

    private fun closeSection() {
        isOpen = false
        onCloseSection()
    }

    private fun makeSectionName(section: DashboardSection, subsection: DashboardNavItem? = null): String {
        return if (subsection == null) context.getString(section.nameResId)
        else {
            "%s ⸬ %s".format(
                    context.getString(section.nameResId),
                    context.getString(subsection.nameResId)
            )
        }
    }

    private fun openSelectedSection() {
        isOpen = true
        bg_nav.animate().setDuration(200).alpha(0f).doAfter {
            bg_nav.visibility = View.GONE
        }

        sections.getOrNull(openSection)?.apply {
            fg_nav.section = makeSectionName(sections[openSection], subsections.firstOrNull())
        }

        onOpenSection {
            sections.getOrNull(openSection)?.apply {

                fg_pager.pages = subsections.map {
                    it.dash
                }

                fg_pager.setOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                    override fun onPageScrollStateChanged(state: Int) {
                    }

                    override fun onPageScrolled(position: Int, positionOffset: Float, posPixels: Int) {
                    }

                    override fun onPageSelected(position: Int) {
                        val section = sections[openSection].subsections[position]
                        fg_nav.section = makeSectionName(sections[openSection], section)
                        flashPlaceholder()
                    }
                })
                fg_nav.viewPager = fg_pager
//                fg_nav_secondary.background = true
//                fg_nav.sleeping = true

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
