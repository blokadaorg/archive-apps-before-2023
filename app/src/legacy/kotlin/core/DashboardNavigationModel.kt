package core

import gs.presentation.ViewBinder


internal class DashboardNavigationModel(
        val sections: List<DashboardSection>,
        val onTurnedOn: (Int) -> Unit = {},
        val onTurnedOff: (Int) -> Unit = {},
        val onSectionChanged: (DashboardSection, sectionIndex: Int) -> Unit = { _, _ -> },
        val onMenuOpened: (DashboardSection, sectionIndex: Int, ViewBinder, menuIndex: Int) -> Unit = { _, _, _, _ -> },
        val onMenuClosed: (Int) -> Unit = {}
) {

    private var on = false

    private var sectionIndex = 1
    private var section = sections[sectionIndex]

    private var menuIndex = 0
    private var menuOpened: ViewBinder? = null

    private var slotSelected: SlotVB? = null
    private var slotOpened = false

    init {
        setNewSection(section)
    }

    fun panelAnchored() {
        when {
            !on -> {
                on = true
                onTurnedOn(sectionIndex)
            }
            else -> {
                setNewMenu(null)
            }
        }
    }

    fun panelCollapsed() {
        if (on) {
            on = false
            onTurnedOff(sectionIndex)
        }
    }

    fun panelExpanded() {
        if (menuOpened == null) {
            val menu = section.subsections[menuIndex].dash
            setNewMenu(menu)
        }
    }

    fun mainViewPagerSwiped(position: Int) {
        if (position != sectionIndex) {
            sectionIndex = position
            setNewSection(sections[sectionIndex])
        }
    }

    fun menuViewPagerSwiped(position: Int) {
        if (position != menuIndex) {
            menuIndex = position
            val menu = section.subsections[menuIndex].dash
            setNewMenu(menu)
        }
    }

    fun tunnelActivating() {
        if (!on) {
            on = true
            onTurnedOn(sectionIndex)
        }
    }

    fun tunnelDeactivated() {
        if (on) {
            on = false
            onTurnedOff(sectionIndex)
        }
    }

    fun selectKey() {
        val item = slotSelected
        when {
            !on -> {
                on = true
                onTurnedOn(sectionIndex)
            }
            item is Navigable && !slotOpened -> {
                setSlotOpened(true)
            }
            item is Navigable -> {
                setSlotOpened(false)
            }
            menuOpened == null -> {
                val menu = section.subsections[menuIndex].dash
                setNewMenu(menu)
            }
        }
    }

    fun backPressed(): Boolean {
        val item = slotSelected
        val menu = menuOpened
        return when {
            item is Navigable && slotOpened -> {
                setSlotOpened(false)
                true
            }
            item is Navigable -> {
                setSlotUnselected()
                true
            }
            menu is Backable && menu.handleBackPressed() -> {
                true
            }
            menu != null -> {
                setNewMenu(null)
                true
            }
            else -> {
                false
            }
        }
    }

    fun leftKey() {
        val item = slotSelected
        val menu = menuOpened
        when {
            item is Navigable && slotOpened -> {
                item.left()
                setSlotOpened(false)
            }
            menu != null -> {
                if (menuIndex > 0) {
                    menuIndex--
                    val newMenu = section.subsections[menuIndex].dash
                    setNewMenu(newMenu)
                }
            }
            sectionIndex > 0 -> {
                sectionIndex--
                val newSection = sections[sectionIndex]
                setNewSection(newSection)
                onSectionChanged(newSection, sectionIndex)
                section = newSection
            }
        }
    }

    fun rightKey() {
        val item = slotSelected
        val menu = menuOpened
        when {
            item is Navigable && slotOpened -> {
                item.right()
                setSlotOpened(false)
            }
            menu != null -> {
                if (menuIndex < section.subsections.size - 1) {
                    menuIndex++
                    val newMenu = section.subsections[menuIndex].dash
                    setNewMenu(newMenu)
                }
            }
            sectionIndex < sections.size - 1 -> {
                sectionIndex++
                val newSection = sections[sectionIndex]
                setNewSection(newSection)
                onSectionChanged(newSection, sectionIndex)
                section = newSection
            }
        }
    }

    fun upKey() {
        val item = slotSelected
        val menu = menuOpened
        when {
            item is Navigable && slotOpened -> {
                item.up()
                setSlotOpened(false)
            }
            menu != null -> {
                (menu as? ListSection)?.apply {
                    selectPrevious()
                }
            }
            else -> {
                (section.dash as? ListSection)?.apply {
                    selectPrevious()
                }
            }
        }
    }

    fun downKey() {
        val item = slotSelected
        val menu = menuOpened
        when {
            item is Navigable && slotOpened -> {
                item.down()
                setSlotOpened(false)
            }
            menu != null -> {
                (menu as? ListSection)?.apply {
                    selectNext()
                }
            }
            else -> {
                (section.dash as? ListSection)?.apply {
                    selectNext()
                }
            }
        }
    }

    private fun setNewMenu(menu: ViewBinder?) {
        if (menu != null) {
            menuOpened = menu
            slotOpened = false
            slotSelected = null
            (menu as? ListSection)?.run {
                setOnSelected { slot ->
                    slotSelected = slot
                    slotOpened = false
                }
            }
            onMenuOpened(section, sectionIndex, menu, menuIndex)
        } else {
            menuOpened = null
            onMenuClosed(sectionIndex)
        }
    }

    private fun setNewSection(section: DashboardSection) {
        this.section = section
        slotOpened = false
        slotSelected = null
        (section.dash as? ListSection)?.run {
            setOnSelected { slot ->
                slotSelected = slot
                slotOpened = false
            }
        }
        menuIndex = 0
        onSectionChanged(section, sectionIndex)
    }

    private fun setSlotOpened(opened: Boolean) {
        slotOpened = opened
        if (opened) {
            slotSelected?.enter()
            val menu = menuOpened
            if (menu != null) {
                (menu as? ListSection)?.run {
                    scrollToSelected()
                }
            } else {
                (section.dash as? ListSection)?.run {
                    scrollToSelected()
                }
            }
        } else {
            slotSelected?.exit()
        }
    }

    private fun setSlotUnselected() {
        val menu = menuOpened
        if (menu != null) {
            (menu as? ListSection)?.run {
                unselect()
            }
        } else {
            (section.dash as? ListSection)?.run {
                unselect()
            }
        }
        slotSelected = null
    }

    fun getOpenedSection() = section

    fun getOpenedSectionIndex() = {
        val section = getOpenedSection()
        when (section) {
            null -> -1
            else -> sections.indexOf(section)
        }
    }()

}
