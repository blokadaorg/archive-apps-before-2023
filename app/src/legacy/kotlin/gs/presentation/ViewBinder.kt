package gs.presentation

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.blokada.R

/**
 * Represents basic view binding structure that can be embedded in lists or displayed independently,
 * also as a widget.
 */
interface ViewBinder {
    fun createView(ctx: Context, parent: ViewGroup): View
    fun attach(view: View)
    fun detach(view: View)

    val viewType: Int
}

interface CallbackViewBinder : ViewBinder {
    fun onAttached(attached: () -> Unit)
    fun onDetached(detached: () -> Unit)
}

typealias On = Boolean

open class LayoutViewBinder(
        private val resId: Int
) : ViewBinder {

    override fun createView(ctx: Context, parent: ViewGroup): View {
        return LayoutInflater.from(ctx).inflate(resId, parent, false)
    }

    override fun attach(view: View) = Unit
    override fun detach(view: View) = Unit
    override val viewType = ViewTypeGenerator.get(this, resId)
}

@Deprecated("old dashboard going away")
abstract class IconDash(
        val onClick: () -> Unit = {},
        val iconRes: Int? = null
) : LayoutViewBinder(R.layout.dash_icon) {

    open fun attachIcon(v: IconDashView) {}
    open fun detachIcon(v: IconDashView) {}

    override fun attach(view: View) {
        view as IconDashView
        view.onClick = onClick
        if (iconRes != null) view.iconRes = iconRes
        attachIcon(view)
    }

    override fun detach(view: View) {
        val v = view as IconDashView
        v.onClick = {}
        detachIcon(v)
    }

}

class DashCache {

    private val views = mutableMapOf<ViewBinder, View>()
    private val parents = mutableMapOf<ViewGroup, ViewBinder>()

    fun use(dash: ViewBinder, ctx: Context, parent: ViewGroup) = {
        val view = views.getOrPut(dash,
                { dash.createView(ctx, parent)} )
        val current = parents.getOrPut(parent, { dash })
        if (current != dash) {
            views[current]?.apply { current.detach(this) }
        }
        parent.removeAllViews()
        parents[parent] = dash
        parent.addView(view)
        dash.attach(view)
    }()

    fun detach(dash: ViewBinder, parent: ViewGroup) = {
        parents[parent]?.apply {
            views[dash]?.apply { dash.detach(this) }
            parent.removeAllViews()
            parents.remove(parent)
        }
    }()

}

object ViewTypeGenerator {
    fun get(instance: Any, payload: Any? = null) = when {
        payload == null -> instance.hashCode()
        else -> instance.javaClass.hashCode() + payload.hashCode()
    }
}


