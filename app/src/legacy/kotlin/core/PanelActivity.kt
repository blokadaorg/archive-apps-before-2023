package core

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import gs.obsolete.Sync
import kotlinx.coroutines.experimental.runBlocking
import org.blokada.R
import tunnel.askTunnelPermission
import java.lang.ref.WeakReference


class PanelActivity : Activity() {

    private val dashboardView by lazy { findViewById<DashboardView>(R.id.root) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard)
//        setFullScreenWindowLayoutInDisplayCutout(window)
//        trai()
        activityRegister.register(this)
    }

    override fun onBackPressed() {
        if (!dashboardView.handleBackPressed()) super.onBackPressed()
    }

    fun trai() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN // Set layout full screen
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

}

val activityRegister = ActiveActivityRegister()

class ActiveActivityRegister {

    private var activity = Sync(WeakReference(null as Activity?))

    fun register(activity: Activity) {
        this.activity = Sync(WeakReference(activity))
    }

    fun askPermissions() {
        val act = activity.get().get() ?: throw Exception("starting MainActivity")
        val deferred = askTunnelPermission(Kontext.new("static perm ask"), act)
        runBlocking {
            val response = deferred.await()
            if (!response) { throw Exception("could not get tunnel permissions") }
        }
    }
}
