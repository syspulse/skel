package io.syspulse.skel.user.store

import io.syspulse.skel.config.Configuration

@deprecated("Use UserStoreDBSync", "")
class UserStoreDB(configuration: Configuration, dbConfigRef: String)
    extends UserStoreDBSync(configuration, dbConfigRef)
