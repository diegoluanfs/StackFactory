package com.diegoluan.stackfactory.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diegoluan.stackfactory.R
import kotlinx.coroutines.delay

/** Splash Compose após splash do sistema; sem alterar o AndroidManifest. */
@Composable
fun StackAuthGate(controller: StackAuthController, content: @Composable () -> Unit) {
    var splash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1250)
        splash = false
    }
    when {
        splash -> StackFactorySplash()
        controller.user == null && !controller.offlineEnabled -> StackLoginScreen(controller)
        else -> content()
    }
}

@Composable
private fun StackFactorySplash() {
    Box(Modifier.fillMaxSize().background(Color(0xFF070A1A)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.stackfactory_arte_abertura),
            contentDescription = "StackFactory — abertura",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun StackLoginScreen(controller: StackAuthController) {
    Box(Modifier.fillMaxSize().background(Color(0xFF080C20))) {
        Image(
            painter = painterResource(R.drawable.stackfactory_arte_abertura),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x4407091A), Color(0xBB07091A), Color(0xF507091A))
                )
            )
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("SEU PROGRESSO, SEU JOGO", color = Color.White,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Escolha como deseja entrar. Visitantes podem vincular o Google depois.",
                color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { controller.signInWithGoogle() },
                enabled = !controller.busy,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Continuar com Google", color = Color(0xFF172139), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { controller.signInAsGuest() },
                enabled = !controller.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Jogar como visitante", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { controller.useOfflineMode() },
                enabled = !controller.busy
            ) {
                Text("Continuar offline, sem backup", color = Color.White.copy(alpha = 0.9f))
            }
            if (controller.busy) {
                Spacer(Modifier.height(14.dp))
                CircularProgressIndicator(color = Color(0xFF47E9FF))
            }
            controller.error?.let { message ->
                Spacer(Modifier.height(14.dp))
                Text(message, color = Color(0xFFFFD6BA), fontSize = 12.sp,
                    textAlign = TextAlign.Center)
            }
            if (controller.accountCollision) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { controller.enterExistingGoogleAccount() },
                    enabled = !controller.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Entrar na conta Google existente", color = Color.White)
                }
                OutlinedButton(onClick = { controller.dismissCollision() }) {
                    Text("Permanecer como visitante", color = Color.White)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("O login não ativa as estatísticas opcionais nem altera seu saldo Lumis.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
                textAlign = TextAlign.Center)
        }
    }
}
