package core

data class ActiveBackgroundItem(val what: String, val blocked: Boolean, val time: Time)

interface ActiveBackground {
    fun setRecentHistory(items: List<ActiveBackgroundItem>)
    fun setTunnelState(state: TunnelState)
    fun setOnClickSwitch(onClick: () -> Unit)
    fun addToHistory(item: ActiveBackgroundItem)
    fun onScroll(fraction: Float, oldPosition: Int, newPosition: Int)
    fun onOpenSection(after: () -> Unit)
    fun onCloseSection()
    fun onPositionChanged(position: Int)
}

