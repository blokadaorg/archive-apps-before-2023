package gs.property

import gs.environment.Environment
import gs.environment.Worker
import org.blokada.BuildConfig

abstract class Version {
        abstract val appName: Property<String>
        abstract val name: Property<String>
        abstract val previousCode: Property<Int>
        abstract val nameCore: Property<String>
        abstract val obsolete: Property<Boolean>
}

class VersionImpl (
        xx: Environment
) : Version() {

        override val appName = Property.of({ "gs" })
        override val name = Property.of({ "0.0" })
        override val previousCode = Property.ofPersisted({ 0 }, BasicPersistence(xx, "previous_code"))
        override val nameCore = Property.of({ BuildConfig.VERSION_NAME })
        override val obsolete = Property.of({ false })
}
