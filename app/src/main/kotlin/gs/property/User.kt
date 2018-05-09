package gs.property

import android.content.Context
import com.github.salomonbrys.kodein.*
import gs.environment.AIdentityPersistence
import gs.environment.Environment
import gs.environment.Identity
import gs.environment.identityFrom

abstract class User {
    abstract val identity: Property<Identity>
}

class UserImpl (
    private val xx: Environment
) : User() {

    private val ctx: Context by xx.instance()
    override val identity = Property.ofPersisted({ identityFrom("") }, AIdentityPersistence(ctx))
}

fun newUserModule(ctx: Context): Kodein.Module {
    return Kodein.Module {
        bind<User>() with singleton { UserImpl(xx = lazy) }
    }
}
