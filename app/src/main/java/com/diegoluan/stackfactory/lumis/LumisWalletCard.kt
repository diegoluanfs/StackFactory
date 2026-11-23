package com.diegoluan.stackfactory.lumis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Gold = Color(0xFFFBBF24)
private val Navy = Color(0xFF101B2D)
private val Muted = Color(0xFFAFC0D5)

/** Exibe o saldo local. Sem CTA de compra nem promessa de resgate. */
@Composable
fun LumisWalletCard(snapshot: LumisSnapshot) {
    var detailsOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Navy.copy(alpha = .94f))
            .border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(20.dp))
            .clickable { detailsOpen = true }
            .padding(18.dp)
            .semantics {
                contentDescription = "Carteira Lumis. Saldo ${LumisRules.formatUnits(snapshot.balanceUnits)} Lumis. Toque para detalhes."
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Gold.copy(alpha = .15f))
                    .border(1.dp, Gold.copy(alpha = .65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", color = Gold, fontWeight = FontWeight.Black, fontSize = 28.sp)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("CARTEIRA LUMIS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text("${LumisRules.formatUnits(snapshot.balanceUnits)} LM", color = Color.White,
                    fontWeight = FontWeight.ExtraBold, fontSize = 27.sp)
            }
            Text("DETALHES ›", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Hoje: +${LumisRules.formatUnits(snapshot.earnedTodayUnits)} LM", color = Gold,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Grátis • Local", color = Muted, fontSize = 11.sp)
        }
    }

    if (detailsOpen) {
        AlertDialog(
            onDismissRequest = { detailsOpen = false },
            title = { Text("Seus Lumis", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text("Saldo: ${LumisRules.formatUnits(snapshot.balanceUnits)} LM",
                        fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Bronze (40 pontos): +0,10 LM")
                    Text("Prata (75 pontos): +0,20 LM")
                    Text("Ouro (100 pontos): +0,30 LM")
                    Text("A cada 7 dias consecutivos: +0,50 LM")
                    Text("Vale somente o melhor selo do dia. Ao melhorar de selo, você recebe a diferença.",
                        fontSize = 12.sp)
                    if (snapshot.recentEvents.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Últimas recompensas", fontWeight = FontWeight.Bold)
                        snapshot.recentEvents.forEach { event ->
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(event.label, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                Text("+${LumisRules.formatUnits(event.deltaUnits)} LM",
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text("Lumis são recompensas gratuitas locais, sem valor em dinheiro. " +
                        "Avatares, skins e carteira entre jogos chegarão em uma atualização futura.",
                        fontSize = 11.sp, color = Color.DarkGray)
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsOpen = false }) { Text("FECHAR") }
            }
        )
    }
}
