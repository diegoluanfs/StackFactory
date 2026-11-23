package com.diegoluan.stackfactory.referral

import java.security.MessageDigest

/**
 * Links de convite sem informações pessoais. O identificador do Firebase Auth
 * NUNCA é colocado no QR ou no link compartilhado.
 *
 * O código é estável para um UID (inclusive quando um visitante vincula Google
 * ao mesmo UID). É uma referência de convite, NÃO uma prova de instalação.
 */
object ReferralLinks {
    private const val PACKAGE_NAME = "com.diegoluan.stackfactory"
    private const val DOMAIN_SEPARATOR = "StackFactory:referral:v1:"
    private val HEX = "0123456789ABCDEF".toCharArray()

    fun codeForUid(uid: String): String {
        require(uid.isNotBlank()) { "A referral code requires an authenticated account." }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((DOMAIN_SEPARATOR + uid).toByteArray(Charsets.UTF_8))
        val result = StringBuilder(20)
        for (index in 0 until 10) {
            val byte = digest[index].toInt() and 0xFF
            result.append(HEX[byte ushr 4])
            result.append(HEX[byte and 0x0F])
        }
        return result.toString()
    }

    /**
     * A Play Store recebe o código na query 'referrer'. Esta versão ainda não
     * confirma instalação ou gera recompensas por indicação.
     */
    fun urlForUid(uid: String): String =
        "https://play.google.com/store/apps/details?id=$PACKAGE_NAME&referrer=sf_${codeForUid(uid)}"

    fun shareMessage(uid: String): String =
        "Jogue StackFactory comigo! Empilhe blocos e conquiste medalhas.\n\n" +
            urlForUid(uid) + "\n\nCódigo de indicação: SF-${codeForUid(uid)}"
}
