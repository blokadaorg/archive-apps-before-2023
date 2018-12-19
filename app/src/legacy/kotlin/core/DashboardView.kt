package core

import android.content.Context
import android.os.SystemClock
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
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

class DashboardView(
        ctx: Context,
        attributeSet: AttributeSet
) : SlidingUpPanelLayout(ctx, attributeSet), Backable {

    private val tunnelEvents by lazy { ctx.inject().instance<EnabledStateActor>() }
    private val tun by lazy { ctx.inject().instance<Tunnel>() }

    private val sections by lazy { createDashboardSections(ctx.ktx("sections.create")) }

    private val topDashCache = DashCache()
    private val mainDashCache = DashCache()
    private var isOpen = false
    private var openDash: gs.presentation.Dash? = null
    private val inter = DecelerateInterpolator(2f)

    private val widthMultiplier = 4

    override fun onFinishInflate() {
        super.onFinishInflate()

        panelHeight = (height * 0.8f).toInt()
        shadowHeight = 0
        setDragView(fg_drag)
        isOverlayed = true
        //setScrollableView()
        //setAnchorPoint(0.8f)

        val ktx = "dashboard".ktx()
        ktx.on(Events.REQUEST_FORWARDED) {
            bg_packets.addToHistory(ActiveBackgroundItem(it, false, SystemClock.elapsedRealtime()))
        }

        ktx.on(Events.REQUEST_BLOCKED) {
            bg_packets.addToHistory(ActiveBackgroundItem(it, true, SystemClock.elapsedRealtime()))
        }

        tunnelEvents.listeners.add(object : IEnabledStateActorListener {
            override fun startActivating() = bg_packets.setTunnelState(TunnelState.ACTIVATING)
            override fun finishActivating() = bg_packets.setTunnelState(TunnelState.ACTIVE)
            override fun startDeactivating() = bg_packets.setTunnelState(TunnelState.DEACTIVATING)
            override fun finishDeactivating() = bg_packets.setTunnelState(TunnelState.INACTIVE)
        })

//        activeBackgroundView.setOnClickSwitch {
//            tun.enabled %= !tun.enabled()
//        }

        bg_packets.setTunnelState(tun.tunnelState())
        fg_nav_primary.adapter = dashCardsAdapter
        fg_nav_primary.setItemTransitionTimeMillis(100)
        fg_nav_primary.setItemTransformer(ScaleTransformer.Builder().setMinScale(0.5f).build())
        fg_nav_primary.scrollToPosition(1)
        fg_nav_primary.addScrollStateChangeListener(object : DiscreteScrollView.ScrollStateChangeListener<ViewHolder> {
            override fun onScroll(scrollPosition: Float, currentPosition: Int, newPosition: Int, currentHolder: ViewHolder?, newCurrent: ViewHolder?) {
                bg_colors.onScroll(Math.abs(scrollPosition), currentPosition, newPosition)
            }

            override fun onScrollStart(currentItemHolder: ViewHolder, adapterPosition: Int) {
                currentItemHolder.view.hideText()
                sections.getOrNull(adapterPosition)?.apply {
                    mainDashCache.detach(main.dash, fg_content)
                }
            }

            override fun onScrollEnd(currentItemHolder: ViewHolder, adapterPosition: Int) {}
        })

        fg_nav_primary.addOnItemChangedListener(object : DiscreteScrollView.OnItemChangedListener<ViewHolder> {
            override fun onCurrentItemChanged(viewHolder: ViewHolder?, adapterPosition: Int) {
                viewHolder?.apply { view.showText() }
                sections.getOrNull(adapterPosition)?.apply {
                    //topDashCache.use(topDash, topContainerView.context, topContainerView)
                }

                //topContainerView.animate().setDuration(200).alpha(1f)
            }
        })

        fg_drag.alpha = 0.7f
        fg_content.visibility = View.GONE

        var resized = false
        viewTreeObserver.addOnGlobalLayoutListener {
            if (!resized) {
                resize()
                resized = true
            }
        }
    }

    private fun resize() {
        val layoutParams = fg_drag.layoutParams as FrameLayout.LayoutParams
        layoutParams.height = height
        layoutParams.width = width * widthMultiplier
        fg_drag.layoutParams = layoutParams
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

    override fun handleBackPressed(): Boolean {
        val dash = openDash
        if (dash is Backable && dash.handleBackPressed()) return true
        openDash = null

        if (isOpen) {
            isOpen = false
            fg_nav_primary.visibility = View.VISIBLE
            fg_nav_primary.animate().setDuration(200).alpha(1f)
            fg_nav_secondary.animate().setDuration(200).alpha(0f).doAfter {
                fg_nav_secondary.visibility = View.GONE
                fg_nav_secondary.clearAll()
            }
            onCloseSection()
            return true
        }
        return false
    }

    private data class ViewHolder(val view: DashboardItemView): RecyclerView.ViewHolder(view)

    var i = 0
    private val dashCardsAdapter = object : RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.dashboard_item, parent, false)
                    as DashboardItemView
            view.setOnClickListener {
                val clicked = view.tag as Int
                if (clicked == fg_nav_primary.currentItem) {
                    isOpen = true
                    fg_nav_primary.animate().setDuration(200).alpha(0f).doAfter {
                        fg_nav_primary.visibility = View.GONE
                    }

                    sections.getOrNull(clicked)?.apply {
                        fg_placeholder_title.text = context.getString(main.nameResId)
                        fg_placeholder_icon.setImageResource(main.iconResId)
                    }

                    onOpenSection {
                        val container = fg_content
                        sections.getOrNull(clicked)?.apply {
                            mainDashCache.use(main.dash, ctx, container)
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
                                                mainDashCache.use(main.dash, ctx, container)
                                                openDash = main.dash
                                                fg_placeholder_title.text = context.getString(main.nameResId)
                                                fg_placeholder_icon.setImageResource(main.iconResId)
                                                flashPlaceholder()
                                            }
                                            else -> {
                                                subsections.getOrNull(position - 1)?.apply {
                                                    mainDashCache.use(dash, ctx, container)
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
