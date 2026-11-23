package com.diegoluan.stackfactory.referral

/**
 * Somente o nosso formato de campanha na Google Play é aceito.
 * Nunca aceitar URLs arbitrárias, UID, dados pessoais ou comandos externos.
 */
object ReferralInstallParser {
    private val playCode = Regex("^sf_([0-9a-fA-F]{20})$")
    private val bareCode = Regex("^(?:SF-)?([0-9a-fA-F]{20})$", RegexOption.IGNORE_CASE)
    const val MAX_INSTALL_AGE_MS = 30L * 24 * 60 * 60 * 1000

    fun parsePlayReferrer(raw: String?): String? =
        raw?.trim()?.let { playCode.matchEntire(it)?.groupValues?.get(1)?.uppercase() }

    // Para validar a entrada ADB de Debug. Nao aceita URLs externas.
    fun parseDebugCode(raw: String?): String? = raw?.trim()?.let {
        parsePlayReferrer(it) ?: bareCode.matchEntire(it)?.groupValues?.get(1)?.uppercase()
    }

    fun isRecentInstall(installBeginMs: Long, nowMs: Long): Boolean =
        installBeginMs > 0L && nowMs >= installBeginMs &&
            nowMs - installBeginMs <= MAX_INSTALL_AGE_MS

    fun canAttributeToNewAccount(
        installBeginMs: Long,
        createdAccountMs: Long,
        nowMs: Long
    ): Boolean = isRecentInstall(installBeginMs, nowMs) &&
        createdAccountMs > 0L &&
        // Aceita criação logo antes ou até 30 dias após a instalação.
        createdAccountMs >= installBeginMs - 10L * 60 * 1000 &&
        createdAccountMs <= nowMs + 10L * 60 * 1000
}
