package com.diegoluan.stackfactory.daily

import java.time.LocalDate

enum class MissionType {
    STACK_BLOCKS,
    FINISH_GAMES,
    REACH_SCORE
}

data class DailyMission(
    val date: LocalDate,
    val type: MissionType,
    val target: Int,
    val progress: Int,
    val completed: Boolean
) {
    val title: String
        get() = when (type) {
            MissionType.STACK_BLOCKS -> "Empilhe $target blocos"
            MissionType.FINISH_GAMES -> "Conclua $target partidas"
            MissionType.REACH_SCORE -> "Alcance $target pontos em uma partida"
        }
}
