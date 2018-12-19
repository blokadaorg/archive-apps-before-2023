package core

import android.content.Context
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.instance
import gs.presentation.ViewDash
import gs.presentation.WebDash
import org.blokada.R

data class DashboardSection(
        val main: DashboardNavItem,
        val subsections: List<DashboardNavItem> = emptyList(),
        val backgroundGradientResId: Int,
        val topDash: gs.presentation.Dash
)

data class DashboardNavItem(
        val iconResId: Int,
        val nameResId: Int,
        val dash: gs.presentation.Dash
)

fun createDashboardSections(ktx: AndroidKontext): List<DashboardSection> {
    val di = ktx.di()
    val pages: Pages = di.instance()

    var sections = emptyList<DashboardSection>()
    sections += DashboardSection(
            main = DashboardNavItem(
                    iconResId = R.drawable.ic_help_outline,
                    nameResId = R.string.dashboard_help_name,
                    dash = WebDash(LazyKodein(ktx.di), pages.help,
                            reloadOnError = true, javascript = true)
            ),
            subsections = listOf(
                    DashboardNavItem(
                            iconResId = R.drawable.ic_comment_multiple_outline,
                            nameResId = R.string.dashboard_community_name,
                            dash = ViewDash(R.layout.dash_placeholder)
                    ),
                    DashboardNavItem(
                            iconResId = R.drawable.ic_earth,
                            nameResId = R.string.main_blog_text,
                            dash = WebDash(LazyKodein(ktx.di), pages.news,
                                    forceEmbedded = true, reloadOnError = true, javascript = true)
                    ),
                    DashboardNavItem(
                            iconResId = R.drawable.ic_heart_box,
                            nameResId = R.string.main_donate_text,
                            dash = WebDash(LazyKodein(ktx.di), pages.donate,
                            reloadOnError = true, javascript = true)
                    ),
                    DashboardNavItem(
                            iconResId = R.drawable.ic_feedback,
                            nameResId = R.string.main_feedback_text,
                            dash = ViewDash(R.layout.dash_placeholder)
                    )
            ),
            backgroundGradientResId = R.array.gradient6,
            topDash = object: ViewDash(R.layout.dash_top) {
                override fun attach(view: View) {
                    view as DashTopView
                    view.big = ktx.ctx.getString(R.string.dashboard_help_name)
                }

                override fun detach(view: View) = Unit
            }

    )

    sections += DashboardSection(
            main = DashboardNavItem(
                    iconResId = R.drawable.ic_chart_donut,
                    nameResId = R.string.dashboard_stats_name,
                    dash = ViewDash(R.layout.dash_placeholder)
            ),
            backgroundGradientResId = R.array.gradient2,
            topDash = BlockedDash(ktx)
    )

    sections += DashboardSection(
            main = DashboardNavItem(
                    iconResId = R.drawable.ic_block,
                    nameResId = R.string.dashboard_ads_name,
                    dash = AdsDash(ktx)
            ),
            subsections = listOf(
                    DashboardNavItem(R.drawable.ic_apps, R.string.dashboard_log_name, ViewDash(R.layout.dash_placeholder)),
                    DashboardNavItem(R.drawable.ic_settings_outline, R.string.dashboard_ads_settings_name, ViewDash(R.layout.dash_placeholder))
            ),
            backgroundGradientResId = R.array.gradient3,
            topDash = HostsDash(ktx)
    )

    sections += DashboardSection(
            main = DashboardNavItem(
                    iconResId = R.drawable.ic_apps,
                    nameResId = R.string.dashboard_apps_name,
                    dash = WhitelistDash(ktx)
            ),
            subsections = listOf(
                    DashboardNavItem(R.drawable.ic_hexagon, R.string.dashboard_apps_system_name, ViewDash(R.layout.dash_placeholder))
            ),
            backgroundGradientResId = R.array.gradient4,
            topDash = AppsDash(ktx)
    )

    sections += DashboardSection(
            main = DashboardNavItem(
                    iconResId = R.drawable.ic_server,
                    nameResId = R.string.dashboard_dns_name,
                    dash = DnsMainDash(ktx)
            ),
            subsections = listOf(
                    DashboardNavItem(R.drawable.ic_settings_outline, R.string.dashboard_dns_settings_name, AdsDash(ktx))
            ),
            backgroundGradientResId = R.array.gradient5,
            topDash = DnsDash(ktx)
    )

    sections += DashboardSection(
            main = DashboardNavItem(
                    iconResId = R.drawable.ic_tune,
                    nameResId = R.string.dashboard_settings_name,
                    dash = SettingsMainDash(ktx)
            ),
            subsections = listOf(
                    DashboardNavItem(
                            iconResId = R.drawable.ic_code_tags,
                            nameResId = R.string.main_changelog,
                            dash = WebDash(LazyKodein(ktx.di), pages.changelog,
                            reloadOnError = true, javascript = true)),
                    DashboardNavItem(
                            iconResId = R.drawable.ic_info,
                            nameResId = R.string.main_credits,
                            dash = WebDash(LazyKodein(ktx.di), pages.credits,
                                    reloadOnError = true, javascript = true)),
                    DashboardNavItem(R.drawable.ic_new_releases, R.string.dashboard_update_name, UpdatesDash(ktx))
            ),
            backgroundGradientResId = R.array.gradient1,
            topDash = UpdateDash(ktx)
    )

    return sections
}

class DashboardNavItemView(
        ctx: Context,
        attributeSet: AttributeSet
) : FrameLayout(ctx, attributeSet) {

    var iconResId: Int = 0
        set(value) {
            field = value
            iconView.setImageResource(value)
        }

    var text: String = ""
        set(value) {
            field = value
            counterTextView.text = value
        }

    private val iconView by lazy { findViewById<ImageView>(R.id.icon) }
    private val counterTextView by lazy { findViewById<TextView>(R.id.counter_text) }

    override fun onFinishInflate() {
        super.onFinishInflate()
        hideText()
    }

    fun showText() {
        iconView.animate().scaleX(1f)
                .scaleY(1f).setDuration(200)
                .withEndAction( {
                    counterTextView.visibility = View.VISIBLE
                    iconView.setColorFilter(active)
                } )
                .start()
    }

    val active = ContextCompat.getColor(context, R.color.colorActive)
    val inactive = ContextCompat.getColor(context, R.color.colorActive)

    fun hideText() {
        iconView.setColorFilter(inactive)
        counterTextView.visibility = View.INVISIBLE
        iconView.animate().scaleX(1.1f).scaleY(1.1f)
                .setDuration(200)
                .start()
    }
}
