package com.diegoluan.stackfactory.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF0D172A)
private val Slate = Color(0xFF18283D)
private val TextSoft = Color(0xFFCBD5E1)
private val Bronze = Color(0xFFEDAB73)
private val Silver = Color(0xFFDDE7F2)
private val Gold = Color(0xFFFFD46C)
private val Blue = Color(0xFF60A5FA)

private fun tierColor(tier: MedalTier) = when (tier) {
    MedalTier.BRONZE -> Bronze
    MedalTier.SILVER -> Silver
    MedalTier.GOLD -> Gold
    MedalTier.NONE -> TextSoft
}

@Composable
fun CheckInDashboard(state: CheckInState) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Navy, Color(0xFF132745))))
            .border(1.dp, Color.White.copy(alpha = .13f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("SUA JORNADA", color = Blue, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Spacer(Modifier.height(4.dp))
                Text("Metas e recompensas", color = Color.White,
                    fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = .09f))
                    .padding(horizontal = 11.dp, vertical = 9.dp)
            ) {
                Text("${state.completedDaysOfSeven}/7 dias", color = Color.White,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("A melhor partida do dia define seu selo. Alcance 40, 75 ou 100 pontos para evoluir.",
            color = TextSoft, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(16.dp))

        CardSection {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("HOJE", color = TextSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text("${state.bestScoreToday} pts", color = Color.White,
                        fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
                val current = state.todayMedal
                Text(if (current == MedalTier.NONE) "A CONQUISTAR" else "${current.title.uppercase()} ✓",
                    color = tierColor(current), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(MedalTier.BRONZE, MedalTier.SILVER, MedalTier.GOLD).forEach { tier ->
                    MedalCard(
                        modifier = Modifier.weight(1f),
                        tier = tier,
                        active = state.todayMedal >= tier
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            val progress = (state.bestScoreToday.toFloat() / state.goldTarget).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .semantics { contentDescription = "Progresso diário: ${state.bestScoreToday} de ${state.goldTarget} pontos para ouro" },
                color = Gold, trackColor = Color(0xFF34455D)
            )
            Spacer(Modifier.height(9.dp))
            Text(
                if (state.nextTier == null) "Ouro conquistado! Meta máxima do dia atingida."
                else "Faltam ${state.pointsToNextTier} pontos para ${state.nextTier!!.title}.",
                color = if (state.nextTier == null) Gold else TextSoft,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(11.dp))

        CardSection {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SEQUÊNCIA", color = TextSoft,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("7 dias de consistência", color = Color.White,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text("${state.completedDaysOfSeven}/7", color = Blue,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(7) { index ->
                    val achieved = index < state.completedDaysOfSeven
                    Box(
                        modifier = Modifier.weight(1f).height(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (achieved) Color(0xFF2455AA) else Color(0xFF29384E))
                            .semantics { contentDescription = "Dia ${index + 1}: ${if (achieved) "concluído" else "pendente"}" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (achieved) "✓" else "${index + 1}", color = Color.White,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.completedDaysOfSeven / 7f },
                modifier = Modifier.fillMaxWidth().height(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .semantics { contentDescription =
                        "Sequência de consistência: ${state.completedDaysOfSeven} de 7 dias" },
                color = Blue,
                trackColor = Color(0xFF34455D)
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = when {
                    state.consecutiveDays >= 7 && state.completedToday ->
                        "★ Meta alcançada! ${state.consecutiveDays} dias seguidos. Continue sua sequência!"
                    state.consecutiveDays >= 7 ->
                        "Você já tem ${state.consecutiveDays} dias seguidos. Faça 40 pontos hoje para manter a sequência!"
                    state.completedToday ->
                        "✓ Hoje concluído. Faltam ${7 - state.completedDaysOfSeven} dia(s) para a meta."
                    else ->
                        "Conquiste 40 pontos em uma partida hoje para manter a sequência."
                },
                color = if (state.consecutiveDays >= 7) Gold else TextSoft,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.height(11.dp))

        CardSection {
            Text("ESTA SEMANA", color = TextSoft, fontSize = 11.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text("Seus melhores selos de segunda a domingo",
                color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.height(13.dp))
            val weekdays = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                weekdays.forEachIndexed { index, label ->
                    val tier = state.weekDayMedals.getOrElse(index) { MedalTier.NONE }
                    val future = index + 1 > state.currentDayIndex
                    Column(
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (index + 1 == state.currentDayIndex)
                                Color(0xFF274669) else Color(0xFF1D2D43))
                            .semantics { contentDescription =
                                "$label: ${if (future) "ainda não chegou" else tier.title}" },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(label, color = TextSoft, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 9.dp))
                        Spacer(Modifier.height(5.dp))
                        Box(
                            modifier = Modifier.size(26.dp)
                                .clip(CircleShape)
                                .background(tierColor(tier).copy(alpha = if (tier == MedalTier.NONE) .07f else .20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(when (tier) {
                                MedalTier.BRONZE -> "B"
                                MedalTier.SILVER -> "P"
                                MedalTier.GOLD -> "O"
                                MedalTier.NONE -> if (future) "·" else "–"
                            }, color = tierColor(tier), fontSize = 13.sp,
                                fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            WeeklyTarget("Semana Bronze", "3 dias com bronze ou melhor", state.weekBronzeDays,
                state.weekBronzeTargetDays, Bronze)
            Spacer(Modifier.height(13.dp))
            WeeklyTarget("Semana Prata", "5 dias com prata ou melhor", state.weekSilverDays,
                state.weekSilverTargetDays, Silver)
            Spacer(Modifier.height(13.dp))
            WeeklyTarget("Semana Ouro", "7 dias com ouro", state.weekGoldDays,
                state.weekGoldTargetDays, Gold)
            if (state.weeklyCompleted) {
                Spacer(Modifier.height(12.dp))
                Text("✓ Semana perfeita! Ouro em todos os sete dias.",
                    color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text("Seus selos são gratuitos e ficam salvos neste aparelho. Para manter a sequência de 7 dias, conquiste pelo menos bronze em uma partida por dia. Sem login obrigatório.",
            color = TextSoft.copy(alpha = .8f), fontSize = 10.sp,
            lineHeight = 14.sp, textAlign = TextAlign.Start)
    }
}

@Composable
private fun CardSection(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Slate)
            .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        content = { content() }
    )
}

@Composable
private fun MedalCard(modifier: Modifier, tier: MedalTier, active: Boolean) {
    val accent = tierColor(tier)
    val glyph = when (tier) {
        MedalTier.BRONZE -> "B"
        MedalTier.SILVER -> "P"
        MedalTier.GOLD -> "O"
        MedalTier.NONE -> "?"
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (active) accent.copy(alpha = .18f) else Color(0xFF25364C))
            .border(1.dp, accent.copy(alpha = if (active) .65f else .20f), RoundedCornerShape(13.dp))
            .padding(vertical = 11.dp, horizontal = 4.dp)
            .semantics { contentDescription =
                "${tier.title}: ${tier.threshold} pontos. ${if (active) "Conquistado" else "Pendente"}" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = .22f)),
            contentAlignment = Alignment.Center) {
            Text(glyph, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(6.dp))
        Text(tier.title, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("${tier.threshold} pts", color = Color.White, fontSize = 10.sp)
        Spacer(Modifier.height(3.dp))
        Text(if (active) "✓" else "·", color = if (active) accent else TextSoft,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeeklyTarget(title: String, subtitle: String, completed: Int, target: Int, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextSoft, fontSize = 10.sp)
        }
        Text("$completed/$target", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(7.dp))
    LinearProgressIndicator(
        progress = { (completed.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(6.dp)
            .clip(RoundedCornerShape(7.dp))
            .semantics { contentDescription = "$title: $completed de $target dias" },
        color = color, trackColor = Color(0xFF34455D)
    )
}
