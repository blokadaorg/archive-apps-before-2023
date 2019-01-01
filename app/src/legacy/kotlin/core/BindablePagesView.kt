package core

import android.content.Context
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import gs.presentation.ViewBinder

class BindablePagesView(
        ctx: Context,
        attributeSet: AttributeSet
) : ViewPager(ctx, attributeSet) {

    private val dashAdapter = object : PagerAdapter() {
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val dash = pages[position]
            val view = dash.createView(context, container)
            dash.attach(view)
            container.addView(view)
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, view: Any) {
            container.removeView(view as View)
            pages[position].detach(view)
        }

        override fun isViewFromObject(view: View, obj: Any) = view == obj
        override fun getCount() = pages.size
    }

    var pages: List<ViewBinder> = emptyList()
        set(value) {
            field = value
            adapter = dashAdapter
        }

}
