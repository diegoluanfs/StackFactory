package com.diegoluan.stackfactory.referral

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tela aberta apenas sob demanda: não gera um bitmap pesado em cada frame do jogo.
 * A área branca ao redor do QR é indispensável para leitura por câmeras.
 */
@Composable
fun ReferralDialog(
    uid: String,
    isGuest: Boolean,
    busy: Boolean,
    error: String?,
    accountCollision: Boolean,
    onGoogleLink: () -> Unit,
    onUseExistingGoogle: () -> Unit,
    onKeepGuest: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val inviteLink = remember(uid) { ReferralLinks.urlForUid(uid) }
    val inviteCode = remember(uid) { ReferralLinks.codeForUid(uid) }
    val qrPixels = (configuration.screenWidthDp - 104).coerceIn(160, 240)
    val qrState by produceState(initialValue = Pair<Bitmap?, Boolean>(null, false), key1 = inviteLink) {
        value = withContext(Dispatchers.Default) {
            Pair(runCatching { encodeQr(inviteLink) }.getOrNull(), true)
        }
    }
    val qrBitmap = qrState.first

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (configuration.screenHeightDp - 64).coerceAtLeast(220).dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF101B33))
                .border(1.dp, Color(0xFF52DCFF).copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("INDICAR AMIGO", color = Color(0xFF68E6FF), fontSize = 15.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text("Seu convite exclusivo", color = Color.White.copy(alpha = 0.78f),
                        fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("Fechar", color = Color.White)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(12.dp)
                    .size(qrPixels.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code do convite para instalar o StackFactory",
                        modifier = Modifier.size(qrPixels.dp)
                    )
                } else if (!qrState.second) {
                    CircularProgressIndicator(color = Color(0xFF2461B3))
                } else {
                    Text("QR indisponível. Use o botão Copiar.",
                        color = Color(0xFF172139), fontSize = 12.sp,
                        textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("SF-$inviteCode", color = Color(0xFFFFD67A), fontWeight = FontWeight.Bold,
                fontSize = 13.sp, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Seu amigo pode escanear o código para abrir o StackFactory na Google Play.",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("StackFactory", inviteLink))
                        android.widget.Toast.makeText(context, "Link copiado", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copiar", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, ReferralLinks.shareMessage(uid))
                        }
                        context.startActivity(Intent.createChooser(intent, "Indicar StackFactory"))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF265CC1))
                ) {
                    Text("Compartilhar", fontSize = 12.sp)
                }
            }
            if (isGuest) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Você está como visitante. Vincular sua conta Google ajuda a manter " +
                        "o mesmo código ao trocar de aparelho.",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp, textAlign = TextAlign.Center
                )
                if (accountCollision) {
                    TextButton(onClick = onUseExistingGoogle, enabled = !busy) {
                        Text("Entrar na conta Google existente", color = Color(0xFFFFD67A))
                    }
                    TextButton(onClick = onKeepGuest, enabled = !busy) {
                        Text("Manter minha conta visitante", color = Color.White)
                    }
                } else {
                    TextButton(onClick = onGoogleLink, enabled = !busy) {
                        Text(if (busy) "Conectando..." else "Vincular conta Google",
                            color = Color(0xFF68E6FF))
                    }
                }
                if (!error.isNullOrBlank()) {
                    Text(error, color = Color(0xFFFFC4A5), fontSize = 11.sp,
                        textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text("Convites não geram Lumis automaticamente.",
                color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp)
        }
    }
}

private fun encodeQr(value: String): Bitmap {
    val size = 512
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    val dark = android.graphics.Color.BLACK
    val light = android.graphics.Color.WHITE
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix.get(x, y)) dark else light
        }
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
        it.setPixels(pixels, 0, size, 0, 0, size, size)
    }
}
