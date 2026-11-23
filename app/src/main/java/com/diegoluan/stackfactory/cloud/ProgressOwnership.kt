package com.diegoluan.stackfactory.cloud

/** Nunca associar um backup local a um UID diferente sem migração explícita. */
internal object ProgressOwnership {
    fun mayUpload(localOwner: String?, signedUid: String): Boolean =
        signedUid.isNotBlank() && (localOwner == null || localOwner == signedUid)
}
