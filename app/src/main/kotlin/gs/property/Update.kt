package gs.property

import gs.environment.Environment

/**
 *
 */
abstract class Update {
    abstract val updating: Property<Boolean>
}

class UpdateImpl(
        private val xx: Environment
) : Update() {
    override val updating = Property.of({ false })
}
