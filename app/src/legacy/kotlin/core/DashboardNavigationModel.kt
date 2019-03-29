package core

import gs.presentation.ViewBinder


internal class DashboardNavigationModel(
        val sections: List<DashboardSection>,
        val onOn: (Int) -> Unit = {},
        val onOff: (Int) -> Unit = {},
        val onSectionChanged: (DashboardSection) -> Unit = {},
        val onMenuOpened: (DashboardSection, Int, ViewBinder) -> Unit = { _, _ -> },
        val onMenuClosed: (Int) -> Unit = {},
        val onToggleItem: (Navigable) -> Unit = {}
) {

    private var on = false

    private var sectionIndex: Int = 1
    private var section: DashboardSection = sections[sectionIndex]

    private var openedMenu: ViewBinder? = null
    private var selectedItem: SlotVB? = null

    fun panelDragging() {

    }

    fun panelAnchored() {

    }

    fun panelCollapsed() {

    }

    fun panelExpanded() {

    }

    fun mainViewPagerSwiped(position: Int) {

    }

    fun menuViewPagerSwiped(position: Int) {


    }

    fun tunnelActivating() {

    }

    fun tunnelDeactivated() {

    }

    fun backPressed(): Boolean {

        val dash = openDash
        if (dash is Backable && dash.handleBackPressed()) return true
        openDash = null

        if (isOpen) {
            sliding.panelState = PanelState.ANCHORED
            return true
        }
    }

    fun leftKey() {

    }

    fun rightKey() {

    }

    fun upKey() {

    }

    fun downKey() {

    }

    fun selectKey() {
        val item = selectedItem
        when {
            !on -> onOn(sectionIndex)
            item is Navigable -> onToggleItem(item)
        }
    }

    fun getOpenedSection() = {
        val m = mode
        when(m) {
            Mode.Off -> null
            is Mode.On -> m.section
            is Mode.Menu -> m.section
            is Mode.Item -> m.section
            is Mode.MenuItem -> m.section
        }
    }()

    fun getOpenedSectionIndex() = {
        val section = getOpenedSection()
        when (section) {
            null -> -1
            else -> sections.indexOf(section)
        }
    }()
}
