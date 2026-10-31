package com.diegoluan.stackfactory.game.skills

/** Habilidades gratuitas obtidas por XP; são reiniciadas em cada partida. */
enum class SkillType {
    RESTORE_WIDTH, EXTRA_LIFE, SHIELD, SLOW_TIME, XP_BOOST, WIDTH_INSURANCE
}

data class SkillCard(val type: SkillType, val name: String, val description: String)

object SkillDeck {
    private val cards = listOf(
        SkillCard(SkillType.RESTORE_WIDTH, "Barra reforçada", "Recupera a largura original do topo da torre e do bloco."),
        SkillCard(SkillType.EXTRA_LIFE, "Vida extra", "Recupera 1 vida, até o máximo de 5."),
        SkillCard(SkillType.SHIELD, "Escudo protetor", "Ignora o próximo erro completo de encaixe."),
        SkillCard(SkillType.SLOW_TIME, "Câmera lenta", "Reduz a velocidade dos próximos 5 blocos."),
        SkillCard(SkillType.XP_BOOST, "Estudante", "Receba mais 3 XP por bloco até o fim da partida."),
        SkillCard(SkillType.WIDTH_INSURANCE, "Seguro de precisão", "Impede uma grande perda de largura em 1 encaixe.")
    )
    fun draw(count: Int): List<SkillCard> = cards.shuffled().take(count)
}
