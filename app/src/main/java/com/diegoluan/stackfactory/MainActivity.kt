package com.diegoluan.stackfactory

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.diegoluan.stackfactory.ads.RewardedAdManager
import com.diegoluan.stackfactory.ads.AdManager
import com.diegoluan.stackfactory.ads.BannerAd
import com.diegoluan.stackfactory.ads.bannerHeightDp
import com.diegoluan.stackfactory.audio.GameAudioController
import com.diegoluan.stackfactory.checkin.FiveDayCheckInRepository
import com.diegoluan.stackfactory.checkin.CheckInState
import com.diegoluan.stackfactory.checkin.CheckInDashboard
import com.diegoluan.stackfactory.lumis.LumisRepository
import com.diegoluan.stackfactory.lumis.LumisSnapshot
import com.diegoluan.stackfactory.lumis.LumisWalletCard
import com.diegoluan.stackfactory.telemetry.StackTelemetry
import com.diegoluan.stackfactory.auth.StackAuthController
import com.diegoluan.stackfactory.auth.StackAuthGate
import com.diegoluan.stackfactory.cloud.PlayerProgressCloud
import com.diegoluan.stackfactory.playgames.PlayGamesController
import com.diegoluan.stackfactory.daily.DailyMission
import com.diegoluan.stackfactory.daily.DailyMissionManager
import com.diegoluan.stackfactory.game.GameScreen
import com.diegoluan.stackfactory.notifications.MissionReminderScheduler
import com.diegoluan.stackfactory.ui.theme.StackFactoryTheme
import kotlinx.coroutines.delay
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.android.ump.ConsentInformation

private const val LOG_TAG = "StackFactory"

class MainActivity : ComponentActivity() {
    private val stackAuthController by lazy { StackAuthController(this) }
    private lateinit var rewardedAdManager: RewardedAdManager
    private lateinit var adManager: AdManager
    private lateinit var audioController: GameAudioController
    private lateinit var playGamesController: PlayGamesController
    private val playGamesUiLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* A UI é gerenciada pelo Google Play Games. */ }
    private var adsReady by mutableStateOf(false)
    private var privacyRequired by mutableStateOf(false)
    private var initializingAds = false
    private var adsInitialized = false

    private val inAppUpdateManager by lazy { AppUpdateManagerFactory.create(this) }
    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Log.i(LOG_TAG, "Atualização não concluída: código ${result.resultCode}")
            // O Google Play pode permitir que o usuário recuse a atualização.
            // Não repetimos a solicitação em loop.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StackTelemetry.initialize(this)
        com.diegoluan.stackfactory.referral.ReferralAttribution.captureFromGooglePlay(this, intent)

        // O agendamento não deve impedir a inicialização da interface.
        try {
            MissionReminderScheduler.schedule(this)
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Falha ao agendar lembretes", exception)
        }

        audioController = GameAudioController(this)
        adManager = AdManager(
            this,
            onAdStarted = { audioController.pauseForAd() },
            onAdFinished = { audioController.resumeAfterAd() }
        )
        rewardedAdManager = RewardedAdManager(
            this,
            onAdStarted = { audioController.pauseForAd() },
            onAdFinished = { audioController.resumeAfterAd() }
        )
        playGamesController = PlayGamesController(this) { intent ->
            playGamesUiLauncher.launch(intent)
        }
        playGamesController.initialize()
        requestAdsConsent()
        checkForPlayUpdate()

        setContent {
            StackFactoryTheme {
                StackAuthGate(stackAuthController) {
                StackFactoryApp(
                    authController = stackAuthController,
                    adManager = adManager,
                    rewardedAdManager = rewardedAdManager,
                    audioController = audioController,
                    playGamesController = playGamesController,
                    adsReady = adsReady,
                    privacyRequired = privacyRequired,
                    onPrivacyOptions = { showPrivacyOptions() }
                )
                }
            }
        }
    }

    /**
     * Consulta a Play Store e inicia uma atualização imediata, quando disponível.
     * Aplica-se a instalações compatíveis com o Google Play; não força instalação
     * silenciosa nem impede que o jogador cancele.
     */
    private fun checkForPlayUpdate() {
        inAppUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    try {
                        inAppUpdateManager.startUpdateFlowForResult(
                            info,
                            updateLauncher,
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                        )
                    } catch (error: Exception) {
                        Log.w(LOG_TAG, "Não foi possível iniciar a atualização", error)
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.d(LOG_TAG, "Atualização pela Play Store indisponível", error)
            }
    }

    /** Retoma atualização imediata iniciada anteriormente, se interrompida. */
    override fun onResume() {
        super.onResume()
        if (::audioController.isInitialized) audioController.onResume()
        if (::playGamesController.isInitialized) playGamesController.refreshAuthentication()
        inAppUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() ==
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                ) {
                    try {
                        inAppUpdateManager.startUpdateFlowForResult(
                            info,
                            updateLauncher,
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                        )
                    } catch (error: Exception) {
                        Log.w(LOG_TAG, "Não foi possível retomar a atualização", error)
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.d(LOG_TAG, "Verificação de atualização em andamento indisponível", error)
            }
    }

    override fun onStart() {
        super.onStart()
        StackTelemetry.onForeground(this)
    }

    override fun onStop() {
        StackTelemetry.onBackground(this)
        super.onStop()
    }

    override fun onPause() {
        if (::audioController.isInitialized) audioController.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::adManager.isInitialized) adManager.clear()
        if (::rewardedAdManager.isInitialized) rewardedAdManager.clear()
        if (::audioController.isInitialized) audioController.release()
        super.onDestroy()
    }

    private fun requestAdsConsent() {
        val info = UserMessagingPlatform.getConsentInformation(this)
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            this,
            params,
            {
                updatePrivacyOptions()
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) {
                    updatePrivacyOptions()
                    updateAdsAvailability()
                }
                updateAdsAvailability()
            },
            { error ->
                Log.w(LOG_TAG, "UMP: ${error.message}")
                updatePrivacyOptions()
                updateAdsAvailability()
            }
        )
    }

    private fun updatePrivacyOptions() {
        val info = UserMessagingPlatform.getConsentInformation(this)
        privacyRequired = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(this) { error ->
            if (error != null) Log.w(LOG_TAG, "UMP: ${error.message}")
            // Descarta anúncios previamente carregados para que as próximas
            // solicitações respeitem as escolhas de privacidade mais recentes.
            adManager.clear()
            rewardedAdManager.clear()
            adsReady = false
            updatePrivacyOptions()
            updateAdsAvailability()
        }
    }

    private fun updateAdsAvailability() {
        val allowed = UserMessagingPlatform.getConsentInformation(this).canRequestAds()
        if (!allowed) {
            adsReady = false
            adManager.clear()
            rewardedAdManager.clear()
            return
        }
        if (!adsInitialized) {
            if (initializingAds) return
            initializingAds = true
            Thread {
                MobileAds.initialize(this) {
                    runOnUiThread {
                        initializingAds = false
                        adsInitialized = true
                        // Confere novamente o consentimento antes de solicitar anúncios.
                        updateAdsAvailability()
                    }
                }
            }.start()
            return
        }
        // Evita novas solicitações redundantes causadas por callbacks do UMP.
        if (adsReady) return
        adsReady = true
        adManager.loadInterstitial()
        rewardedAdManager.load()
    }
}

@Composable
private fun StackFactoryApp(
    authController: StackAuthController,
    adManager: AdManager,
    rewardedAdManager: RewardedAdManager,
    audioController: GameAudioController,
    playGamesController: PlayGamesController,
    adsReady: Boolean,
    privacyRequired: Boolean,
    onPrivacyOptions: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cloudProgress = remember(context) { PlayerProgressCloud(context) }
    val signedUid = authController.user?.uid
    // Backup and high-score recovery run in the background; the player is
    // never asked to start or monitor cloud synchronization manually.
    LaunchedEffect(signedUid) {
        signedUid?.let { uid -> com.diegoluan.stackfactory.referral.ReferralAttribution.onAuthenticated(context, uid) }
        signedUid?.let { uid ->
            runCatching { cloudProgress.schedule(uid) }
                .onFailure { Log.w("StackProgress", "Could not schedule cloud backup", it) }
            runCatching {
                cloudProgress.restoreHighScore(uid) { result ->
                    Log.d("StackProgress", "Automatic record recovery: $result")
                }
            }.onFailure { Log.w("StackProgress", "Could not request record recovery", it) }
        }
    }
    val missionManager = remember(context) { DailyMissionManager(context) }
    val checkInRepo = remember(context) { FiveDayCheckInRepository(context) }
    var checkInState by remember(checkInRepo) { mutableStateOf(checkInRepo.current()) }
    val lumisRepo = remember(context) { LumisRepository(context) }
    var lumisState by remember(lumisRepo) {
        mutableStateOf(lumisRepo.snapshot(checkInState.dayKey))
    }

    var playing by remember { mutableStateOf(false) }
    var telemetryEnabled by remember { mutableStateOf(StackTelemetry.isEnabled(context)) }
    var musicEnabled by remember { mutableStateOf(audioController.musicEnabled) }
    var effectsEnabled by remember { mutableStateOf(audioController.effectsEnabled) }
    var mission by remember(missionManager) { mutableStateOf(missionManager.today()) }
    var streak by remember(missionManager) { mutableIntStateOf(missionManager.streak()) }
    // O Android controla a permissão de notificações. A solicitação é feita
    // somente uma vez pelo aplicativo, ao tocar em JOGAR, sem bloquear o jogo.
    val permissionPrefs = remember(context) {
        context.getSharedPreferences("stack_factory_notification_permission", Context.MODE_PRIVATE)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(LOG_TAG, "Permissão de notificações concedida: $granted")
        // O WorkManager já agenda os lembretes; o Android controla a exibição.
    }

    fun refreshMission() {
        mission = missionManager.today()
        streak = missionManager.streak()
        checkInState = checkInRepo.current()
        lumisState = lumisRepo.snapshot(checkInState.dayKey)
    }

    fun startGame() {
        runCatching { cloudProgress.beginGame(signedUid) }
            .onFailure { Log.w(LOG_TAG, "Falha local ao iniciar backup", it) }
        StackTelemetry.gameStarted(context)
        playing = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            !permissionPrefs.getBoolean("requested", false)
        ) {
            permissionPrefs.edit().putBoolean("requested", true).apply()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Atualiza os dados quando o usuário volta ao aplicativo.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshMission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Garante a troca da missão se o aplicativo permanecer aberto à meia-noite.
    LaunchedEffect(missionManager) {
        while (true) {
            refreshMission()
            delay(60_000L)
        }
    }

    if (playing) {
        GameScreen(
            onMissionUpdated = { refreshMission() },
            onBackToMenu = {
                refreshMission()
                runCatching { cloudProgress.schedule(signedUid) }
                playing = false
            },
            onGameFinished = { finalScore ->
                runCatching { cloudProgress.finishGame(signedUid, finalScore) }
                    .onFailure { Log.w(LOG_TAG, "Falha local ao salvar partida", it) }
                StackTelemetry.gameFinished(context, finalScore)
                adManager.recordGameFinished()
                playGamesController.onGameFinished(finalScore)
            },
            onScoreChanged = { currentScore ->
                StackTelemetry.scoreChanged(context, currentScore)
                checkInState = checkInRepo.onScoreChanged(currentScore)
                // A tela do jogo continua intacta. Concede apenas nos marcos de
                // 40, 75 e 100 pontos, sempre com idempotencia no banco local.
                if (currentScore == 40 || currentScore == 75 || currentScore == 100) {
                    try {
                        val previousBalance = lumisState.balanceUnits
                        lumisState = lumisRepo.awardForProgress(checkInState)
                        val earnedUnits = (lumisState.balanceUnits - previousBalance)
                            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                        StackTelemetry.lumisGranted(context, earnedUnits)
                        cloudProgress.schedule(signedUid)
                    } catch (error: Exception) {
                        Log.e(LOG_TAG, "Falha ao registrar recompensa Lumis", error)
                    }
                }
            },
            onBlockPlaced = { audioController.playBlockImpact() },
            onGameRestarted = {
                runCatching { cloudProgress.beginGame(signedUid) }
                StackTelemetry.gameStarted(context)
            },
            onSkillSelected = { skill -> StackTelemetry.skillSelected(context, skill) },
            onRewardedRecovery = { earned ->
                if (adsReady) {
                    rewardedAdManager.show(
                        onEarned = {
                            StackTelemetry.revived(context)
                            earned()
                        },
                        onUnavailable = {
                            android.widget.Toast.makeText(
                                context, "Vídeo indisponível no momento. Tente novamente.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                } else {
                    android.widget.Toast.makeText(
                        context, "Vídeo indisponível no momento.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onProceedAfterGameOver = { action ->
                if (adsReady) adManager.showIfDue(action) else action()
            }
        )
    } else {
        StartScreen(
            accountLabel = when {
                authController.user?.isAnonymous == true -> "Visitante • dados locais preservados"
                authController.user != null -> authController.user?.email ?: "Conta Google conectada"
                else -> "Modo offline • sem backup"
            },
            accountBusy = authController.busy,
            accountError = authController.error,
            accountCollision = authController.accountCollision,
            referralUid = signedUid,
            guestAccount = authController.user?.isAnonymous == true,
            onGoogleAuth = { authController.signInWithGoogle() },
            onConnectAccount = { authController.showLogin() },
            onAccountSignOut = { authController.signOut() },
            onEnterExistingGoogle = { authController.enterExistingGoogleAccount() },
            onDismissCollision = { authController.dismissCollision() },
            mission = mission,
            streak = streak,
            checkInState = checkInState,
            lumisState = lumisState,
            playGamesAvailable = playGamesController.configured,
            playGamesConnected = playGamesController.authenticated,
            showLeaderboard = playGamesController.hasLeaderboard,
            showAchievements = playGamesController.hasAchievements,
            onPlayGamesSignIn = { playGamesController.requestSignIn() },
            onShowLeaderboard = { playGamesController.showLeaderboard() },
            onShowAchievements = { playGamesController.showAchievements() },
            onStartGame = { startGame() },
            telemetryEnabled = telemetryEnabled,
            onTelemetryToggle = {
                telemetryEnabled = !telemetryEnabled
                StackTelemetry.setEnabled(context, telemetryEnabled)
            },
            showBanner = adsReady,
            showPrivacyOptions = privacyRequired,
            onPrivacyOptions = onPrivacyOptions,
            musicEnabled = musicEnabled,
            effectsEnabled = effectsEnabled,
            onMusicToggle = {
                musicEnabled = !musicEnabled
                audioController.setMusicEnabled(musicEnabled)
            },
            onEffectsToggle = {
                effectsEnabled = !effectsEnabled
                audioController.setEffectsEnabled(effectsEnabled)
            }
        )
    }
}

@Composable
fun StartScreen(
    accountLabel: String = "Modo offline",
    accountBusy: Boolean = false,
    accountError: String? = null,
    accountCollision: Boolean = false,
    referralUid: String? = null,
    guestAccount: Boolean = false,
    onGoogleAuth: () -> Unit = {},
    onConnectAccount: () -> Unit = {},
    onAccountSignOut: () -> Unit = {},
    onEnterExistingGoogle: () -> Unit = {},
    onDismissCollision: () -> Unit = {},
    mission: DailyMission,
    streak: Int,
    onStartGame: () -> Unit,
    telemetryEnabled: Boolean = true,
    onTelemetryToggle: () -> Unit = {},
    checkInState: CheckInState? = null,
    lumisState: LumisSnapshot? = null,
    playGamesAvailable: Boolean = false,
    playGamesConnected: Boolean = false,
    showLeaderboard: Boolean = false,
    showAchievements: Boolean = false,
    onPlayGamesSignIn: () -> Unit = {},
    onShowLeaderboard: () -> Unit = {},
    onShowAchievements: () -> Unit = {},
    showBanner: Boolean = false,
    showPrivacyOptions: Boolean = false,
    onPrivacyOptions: () -> Unit = {},
    musicEnabled: Boolean = true,
    effectsEnabled: Boolean = true,
    onMusicToggle: () -> Unit = {},
    onEffectsToggle: () -> Unit = {}
) {
    val primaryColor = Color(0xFF2563EB)
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val bannerHeight = if (showBanner) bannerHeightDp(context, configuration.screenWidthDp) else 0
    // A conta fica no canto superior direito, sem ocupar o centro do menu.
    var accountMenuOpen by remember { mutableStateOf(false) }
    var statsInfoOpen by remember { mutableStateOf(false) }
    var referralOpen by remember { mutableStateOf(false) }
    var signOutConfirmOpen by remember { mutableStateOf(false) }
    val accountMenuWidth = (configuration.screenWidthDp - 32).coerceIn(220, 320).dp
    BackHandler(enabled = accountMenuOpen) {
        if (statsInfoOpen) statsInfoOpen = false else accountMenuOpen = false
    }
    val versionName = remember(context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (exception: Exception) {
            Log.w(LOG_TAG, "Não foi possível obter a versão do aplicativo", exception)
            "1.0"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.stack_factory_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Reserva espaço para o acesso ao perfil inclusive em telas compactas.
                .padding(horizontal = 24.dp)
                .padding(top = 86.dp, bottom = if (showBanner) (bannerHeight + 24).dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "STACK",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )
            Text(
                text = "FACTORY",
                color = primaryColor,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Construa. Equilibre. Supere seu recorde.",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(width = 220.dp, height = 58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(primaryColor)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.20f),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable(onClick = onStartGame),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "JOGAR",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Toque para começar",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(28.dp))

            DailyMissionCard(mission = mission, streak = streak)
            if (lumisState != null) {
                Spacer(Modifier.height(14.dp))
                LumisWalletCard(lumisState)
            }
            if (checkInState != null) {
                Spacer(Modifier.height(14.dp))
                CheckInDashboard(checkInState)
            }
            if (playGamesAvailable) {
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.80f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("GOOGLE PLAY GAMES", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    if (!playGamesConnected) {
                        Text(
                            "Sem conexão. Você pode jogar normalmente.",
                            color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp
                        )
                        PlayGamesMenuAction("Conectar ao Play Games", onPlayGamesSignIn)
                    } else {
                        if (showLeaderboard) PlayGamesMenuAction("Ranking global", onShowLeaderboard)
                        if (showAchievements) PlayGamesMenuAction("Conquistas", onShowAchievements)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            if (showPrivacyOptions) {
                Text(
                    text = "OPÇÕES DE PRIVACIDADE",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable(onClick = onPrivacyOptions)
                        .padding(10.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "STACK FACTORY • $versionName",
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
        }
        // Ao abrir o perfil, tocar fora fecha o painel sem acionar o menu atrás.
        if (accountMenuOpen) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { accountMenuOpen = false }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = Modifier.size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xE512203B))
                    .border(1.dp, Color(0xFF58E7FF).copy(alpha = 0.65f), CircleShape)
                    .clickable { accountMenuOpen = !accountMenuOpen }
                    .padding(5.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (accountMenuOpen) "Fechar menu da conta" else "Abrir menu da conta"
                    },
                contentAlignment = Alignment.Center
            ) {
                // A imagem usa o ícone real do app, incluindo ícones adaptativos Android.
                AndroidView(
                    factory = { viewContext ->
                        ImageView(viewContext).apply {
                            setImageDrawable(viewContext.packageManager.getApplicationIcon(
                                viewContext.applicationInfo
                            ))
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            isClickable = false
                            isFocusable = false
                        }
                    },
                    modifier = Modifier.size(42.dp)
                )
                // Indica o tipo de acesso; não representa estado da rede.
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(
                            if (accountLabel.startsWith("Modo offline")) Color(0xFFFFB84D)
                            else Color(0xFF4ADE80)
                        )
                        .border(2.dp, Color(0xFF101A30), CircleShape)
                )
            }

            if (accountMenuOpen) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.width(accountMenuWidth)
                        .heightIn(max = (configuration.screenHeightDp - 120).coerceAtLeast(190).dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xF7111D34))
                        .verticalScroll(rememberScrollState())
                        .border(
                            1.dp, Color(0xFF58E7FF).copy(alpha = 0.42f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    if (referralUid != null) {
                        ProfileMenuAction(
                            "Indicar amigo",
                            onClick = {
                                accountMenuOpen = false
                                referralOpen = true
                            },
                            prominent = true
                        )
                        ProfileMenuAction("Sair da conta", onClick = {
                            accountMenuOpen = false
                            signOutConfirmOpen = true
                        })
                    } else {
                        ProfileMenuAction("Iniciar sessão", onClick = {
                            accountMenuOpen = false
                            onConnectAccount()
                        }, prominent = true)
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().height(1.dp)
                            .background(Color.White.copy(alpha = 0.14f))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "PREFERÊNCIAS",
                        color = Color(0xFF87ABC6),
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    MenuPreferenceToggle(
                        symbol = "♫",
                        title = "Música",
                        enabled = musicEnabled,
                        onToggle = { if (it != musicEnabled) onMusicToggle() }
                    )
                    MenuPreferenceToggle(
                        symbol = "♪",
                        title = "Efeitos",
                        enabled = effectsEnabled,
                        onToggle = { if (it != effectsEnabled) onEffectsToggle() }
                    )
                    MenuPreferenceToggle(
                        symbol = "▤",
                        title = "Estatísticas",
                        enabled = telemetryEnabled,
                        onToggle = { if (it != telemetryEnabled) onTelemetryToggle() },
                        info = {
                            Box {
                                Box(
                                    modifier = Modifier.size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF293D5A))
                                        .border(1.dp, Color(0xFF5EC8E9).copy(alpha = 0.55f), CircleShape)
                                        .clickable { statsInfoOpen = !statsInfoOpen }
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = "Sobre as estatísticas e privacidade"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("?", color = Color(0xFFB7EDFF), fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold)
                                }
                                DropdownMenu(
                                    expanded = statsInfoOpen,
                                    onDismissRequest = { statsInfoOpen = false },
                                    modifier = Modifier.width(accountMenuWidth)
                                        .background(Color(0xFF14243F))
                                ) {
                                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                        Text("Sobre as estatísticas", color = Color.White,
                                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(7.dp))
                                        Text(
                                            "Quando ativadas, registram dados de uso, partidas e " +
                                                "informações do dispositivo para analisar e melhorar o jogo. " +
                                                "Você pode desativar a coleta a qualquer momento. " +
                                                "O backup do progresso é independente desta opção.",
                                            color = Color.White.copy(alpha = 0.83f),
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Entendi",
                                            color = Color(0xFF76E5FF),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.align(Alignment.End)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { statsInfoOpen = false }
                                                .padding(9.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        if (signOutConfirmOpen) {
            AlertDialog(
                onDismissRequest = { signOutConfirmOpen = false },
                title = { Text("Sair da conta?") },
                text = {
                    Text(
                        if (guestAccount) {
                            "Você está usando uma conta visitante. Sem vínculo com Google, " +
                                "poderá não conseguir recuperar esse perfil e seu convite depois. " +
                                "Seu progresso local não será apagado."
                        } else {
                            "Você poderá entrar novamente com Google. " +
                                "O progresso armazenado no aparelho não será apagado."
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        signOutConfirmOpen = false
                        onAccountSignOut()
                    }) { Text("Sair", color = Color(0xFFFFC48D)) }
                },
                dismissButton = {
                    TextButton(onClick = { signOutConfirmOpen = false }) {
                        Text("Cancelar", color = Color(0xFF68E6FF))
                    }
                },
                containerColor = Color(0xFF12203A),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.82f)
            )
        }

        if (referralOpen && referralUid != null) {
            com.diegoluan.stackfactory.referral.ReferralDialog(
                uid = referralUid,
                isGuest = guestAccount,
                busy = accountBusy,
                error = accountError,
                accountCollision = accountCollision,
                onGoogleLink = onGoogleAuth,
                onUseExistingGoogle = onEnterExistingGoogle,
                onKeepGuest = onDismissCollision,
                onDismiss = { referralOpen = false }
            )
        }

        if (showBanner) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().height(bannerHeight.dp)
                    .background(Color(0xFF101827)),
                contentAlignment = Alignment.BottomCenter
            ) {
                BannerAd(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DailyMissionCard(mission: DailyMission, streak: Int) {
    val progress = (
        mission.progress.toFloat() / mission.target.coerceAtLeast(1)
    ).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.82f))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.18f),
                RoundedCornerShape(18.dp)
            )
            .padding(18.dp)
    ) {
        Text(
            text = "🎯 MISSÃO DO DIA",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = mission.title,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 15.sp
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = Color(0xFF60A5FA),
            trackColor = Color.White.copy(alpha = 0.18f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (mission.completed) {
                "✅ Missão concluída!"
            } else {
                "${mission.progress}/${mission.target} concluídos"
            },
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "🔥 Sequência: $streak dia(s)",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
    }
}

/** Configuração compacta: a preferência é salva pelos controladores existentes. */
@Composable
private fun MenuPreferenceToggle(
    symbol: String,
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    info: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF243B56)),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = Color(0xFF8CE8FF), fontSize = 17.sp)
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (info != null) info()
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics {
                contentDescription = "$title: ${if (enabled) "ligado" else "desligado"}"
            },
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF32C6D7),
                checkedThumbColor = Color(0xFF102E43),
                uncheckedTrackColor = Color(0xFF334B65),
                uncheckedThumbColor = Color(0xFFD9E1EE),
                uncheckedBorderColor = Color(0xFF66859A)
            )
        )
    }
}

@Composable
private fun ProfileMenuAction(
    title: String,
    onClick: () -> Unit,
    prominent: Boolean = false
) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (prominent) Color(0xFF2459B2) else Color(0xFF213651))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        color = Color.White,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun PlayGamesMenuAction(title: String, onClick: () -> Unit) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF223C65))
            .clickable(onClick = onClick)
            .padding(12.dp),
        color = Color.White,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold
    )
}
