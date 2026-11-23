package com.diegoluan.stackfactory.auth

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.diegoluan.stackfactory.R
import com.diegoluan.stackfactory.telemetry.StackTelemetry
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/**
 * Login Firebase independente do Play Games e do consentimento de Analytics.
 * Se já houver UID anônimo, o Google é vinculado ao MESMO UID.
 * Colisão entre contas não faz merge automático de medalhas ou Lumis.
 */
class StackAuthController(private val activity: ComponentActivity) {
    private val credentialManager = CredentialManager.create(activity)
    private val uiPrefs = activity.getSharedPreferences("sf_auth_ui_v1", Context.MODE_PRIVATE)
    private val firebaseReady = FirebaseApp.getApps(activity).isNotEmpty()
    private val auth: FirebaseAuth? = if (firebaseReady) FirebaseAuth.getInstance() else null

    var user: FirebaseUser? by mutableStateOf(auth?.currentUser)
        private set
    var offlineEnabled by mutableStateOf(uiPrefs.getBoolean("continue_offline", false))
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var accountCollision by mutableStateOf(false)
        private set
    private var pendingExistingCredential: AuthCredential? = null

    init {
        if (!firebaseReady) {
            error = "Firebase não configurado. Confira o google-services.json."
        }
        auth?.addAuthStateListener { current -> user = current.currentUser }
    }

    fun useOfflineMode() {
        error = null
        offlineEnabled = true
        uiPrefs.edit().putBoolean("continue_offline", true).apply()
    }

    fun showLogin() {
        offlineEnabled = false
        uiPrefs.edit().putBoolean("continue_offline", false).apply()
        error = null
    }

    fun signInAsGuest() {
        val service = auth ?: return showError("Firebase não está disponível.")
        if (busy) return
        error = null
        busy = true
        if (service.currentUser != null) {
            user = service.currentUser
            busy = false
            return
        }
        service.signInAnonymously().addOnCompleteListener(activity) { task ->
            busy = false
            if (task.isSuccessful) user = task.result?.user
            else showError("Não foi possível criar o perfil de visitante. Confira a conexão e o provedor Anônimo no Firebase.")
        }
    }

    fun signInWithGoogle() {
        val service = auth ?: return showError("Firebase não está disponível.")
        if (busy) return
        accountCollision = false
        pendingExistingCredential = null
        error = null
        val clientId = activity.resources.getIdentifier(
            "default_web_client_id", "string", activity.packageName
        )
        if (clientId == 0) return showError(
            "Cliente OAuth ausente. Atualize o google-services.json após cadastrar SHA-1 e ativar Google."
        )
        busy = true
        activity.lifecycleScope.launch {
            try {
                // Fluxo dedicado ao clique no botao "Continuar com Google".
                // Ao contrario do bottom sheet, permite escolher/reativar contas
                // e e recomendado quando a acao foi solicitada pelo jogador.
                val option = GetSignInWithGoogleOption.Builder(
                    activity.getString(clientId)
                ).build()
                val response = credentialManager.getCredential(
                    context = activity,
                    request = GetCredentialRequest.Builder()
                        .addCredentialOption(option).build()
                )
                val selected = response.credential
                if (selected !is CustomCredential ||
                    selected.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    busy = false
                    return@launch showError("O Google não retornou uma credencial válida.")
                }
                val idToken = GoogleIdTokenCredential.createFrom(selected.data).idToken
                val googleCredential = GoogleAuthProvider.getCredential(idToken, null)
                val existing = service.currentUser
                val result = if (existing != null && existing.isAnonymous) {
                    existing.linkWithCredential(googleCredential)
                } else {
                    service.signInWithCredential(googleCredential)
                }
                result.addOnCompleteListener(activity) { task ->
                    busy = false
                    if (task.isSuccessful) {
                        user = task.result?.user
                        error = null
                    } else if (task.exception is FirebaseAuthUserCollisionException ||
                        task.exception?.javaClass?.simpleName?.contains("Collision") == true
                    ) {
                        // O visitante NÃO é substituído silenciosamente pela conta existente.
                        pendingExistingCredential = googleCredential
                        accountCollision = true
                        error = "Esta conta Google já possui um perfil. Os dados do visitante não serão combinados automaticamente."
                    } else {
                        Log.w("StackAuth", "Falha no login Google", task.exception)
                        showError("Falha ao autenticar com Google. Confira o SHA-1 de Debug, o provedor Google e o JSON atualizado.")
                    }
                }
            } catch (cancelled: GetCredentialCancellationException) {
                busy = false
            } catch (noAccount: NoCredentialException) {
                busy = false
                Log.w("StackAuth", "Nenhuma credencial Google no dispositivo", noAccount)
                showError(
                    "Nenhuma conta Google disponivel. No emulador, use uma imagem " +
                        "Google Play e adicione uma conta em Configuracoes > Contas. " +
                        "Depois tente novamente."
                )
            } catch (error: GetCredentialException) {
                busy = false
                Log.w("StackAuth", "Falha no seletor Google", error)
                showError("Nao foi possivel abrir o Google. Confira Play Services, internet e OAuth no Firebase.")
            } catch (error: GoogleIdTokenParsingException) {
                busy = false
                showError("A credencial Google recebida é inválida.")
            } catch (error: Exception) {
                busy = false
                Log.w("StackAuth", "Falha na autenticação", error)
                showError("Não foi possível concluir o login. Confira a configuração do Firebase.")
            }
        }
    }

    /** Decisão explícita do usuário após colisão, sem merge automático de dados. */
    fun enterExistingGoogleAccount() {
        val service = auth ?: return
        val credential = pendingExistingCredential ?: return
        if (busy || !accountCollision) return
        busy = true
        // Nunca enviar telemetria pendente da conta antiga para o novo UID.
        StackTelemetry.prepareForAccountChange(activity)
        service.signInWithCredential(credential).addOnCompleteListener(activity) { task ->
            busy = false
            if (task.isSuccessful) {
                pendingExistingCredential = null
                accountCollision = false
                user = task.result?.user
                error = null
            } else {
                showError("Não foi possível acessar a conta existente. O progresso local foi preservado.")
            }
        }
    }

    fun dismissCollision() {
        accountCollision = false
        pendingExistingCredential = null
        error = null
    }

    /** Sair não apaga o progresso do SQLite/SharedPreferences. */
    fun signOut() {
        StackTelemetry.prepareForAccountChange(activity)
        auth?.signOut()
        user = null
        // Return to the game menu; the account icon now says "Iniciar sessão".
        // This preference affects the UI only; progress and credentials aren't copied.
        offlineEnabled = true
        uiPrefs.edit().putBoolean("continue_offline", true).apply()
        pendingExistingCredential = null
        accountCollision = false
        error = null
        activity.lifecycleScope.launch {
            runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        }
    }

    private fun showError(message: String) { error = message }
}
