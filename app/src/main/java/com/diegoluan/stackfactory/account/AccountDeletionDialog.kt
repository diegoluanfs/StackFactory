package com.diegoluan.stackfactory.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

/**
 * Recebe um pedido de exclusao, nao promete que o pedido ja foi processado.
 * A exclusao dos registros do Firestore e do Auth exige processamento administrativo.
 */
@Composable
fun AccountDeletionDialog(uid: String, onDismiss: () -> Unit) {
    var sending by remember(uid) { mutableStateOf(false) }
    var result by remember(uid) { mutableStateOf<String?>(null) }
    var success by remember(uid) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Excluir conta e dados") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Você pode solicitar a exclusão da sua conta e dos dados associados " +
                        "ao StackFactory. A solicitação será enviada com segurança ao Firebase."
                )
                Text(
                    "Seu pedido será analisado e processado. Não desinstale o jogo " +
                        "para enviar a solicitação."
                )
                if (result != null) {
                    Spacer(modifier = androidx.compose.ui.Modifier.height(3.dp))
                    Text(result!!)
                }
            }
        },
        confirmButton = {
            if (!success) {
                TextButton(
                    enabled = !sending,
                    onClick = {
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser == null || currentUser.uid != uid) {
                            result = "Sessão inválida. Entre novamente antes de solicitar a exclusão."
                            return@TextButton
                        }
                        sending = true
                        result = "Enviando solicitação…"
                        val ref = FirebaseFirestore.getInstance()
                            .collection("account_deletion_requests")
                            .document(uid)
                        ref.get(Source.SERVER)
                            .addOnSuccessListener { snapshot ->
                                if (snapshot.exists()) {
                                    success = true
                                    result = "Sua solicitação já foi recebida."
                                    sending = false
                                } else {
                                    ref.set(
                                        mapOf(
                                            "uid" to uid,
                                            "status" to "pending",
                                            "requested_at" to FieldValue.serverTimestamp()
                                        )
                                    ).addOnSuccessListener {
                                        success = true
                                        sending = false
                                        result = "Solicitação recebida. A exclusão será processada."
                                    }.addOnFailureListener { error ->
                                        sending = false
                                        result = "Não foi possível enviar o pedido: " +
                                            (error.localizedMessage ?: "tente novamente com internet")
                                    }
                                }
                            }
                            .addOnFailureListener { error ->
                                sending = false
                                result = "Não foi possível verificar sua solicitação: " +
                                    (error.localizedMessage ?: "verifique a conexão")
                            }
                    }
                ) { Text(if (sending) "Enviando…" else "Solicitar exclusão") }
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) {
                Text(if (success) "Fechar" else "Cancelar")
            }
        }
    )
}
