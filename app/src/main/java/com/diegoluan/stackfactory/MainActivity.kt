package com.diegoluan.stackfactory

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.diegoluan.stackfactory.ads.AdManager
import com.diegoluan.stackfactory.ads.BannerAd
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
    private lateinit var adManager: AdManager
    private var adsReady by mutableStateOf(false)
    private var privacyRequired by mutableStateOf(false)
    private var initializingAds = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // O agendamento não deve impedir a inicialização da interface.
        try {
            MissionReminderScheduler.schedule(this)
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Falha ao agendar lembretes", exception)
        }

        adManager = AdManager(this)
        requestAdsConsent()

        setContent {
            StackFactoryTheme {
                StackFactoryApp(
                    adManager = adManager,
                    adsReady = adsReady,
                    privacyRequired = privacyRequired,
                    onPrivacyOptions = { showPrivacyOptions() }
                )
            }
        }
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
            updatePrivacyOptions()
            updateAdsAvailability()
        }
    }

    private fun updateAdsAvailability() {
        val allowed = UserMessagingPlatform.getConsentInformation(this).canRequestAds()
        if (!allowed) {
            adsReady = false
            if (::adManager.isInitialized) adManager.clear()
        } else if (!initializingAds) {
            initializingAds = true
            Thread {
                MobileAds.initialize(this) {
                    runOnUiThread {
                        adsReady = true
                        adManager.loadInterstitial()
                    }
                }
            }.start()
        } else {
            adsReady = true
            adManager.loadInterstitial()
        }
    }
}

@Composable
private fun StackFactoryApp(
    adManager: AdManager,
    adsReady: Boolean,
    privacyRequired: Boolean,
    onPrivacyOptions: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val missionManager = remember(context) { DailyMissionManager(context) }

    var playing by remember { mutableStateOf(false) }
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
    }

    fun startGame() {
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
        BackHandler { /* Evita sair no meio de uma partida. */ }
        GameScreen(
            onMissionUpdated = { refreshMission() },
            onBackToMenu = {
                refreshMission()
                playing = false
            },
            onGameFinished = { adManager.recordGameFinished() },
            onProceedAfterGameOver = { action ->
                if (adsReady) adManager.showIfDue(action) else action()
            }
        )
    } else {
        StartScreen(
            mission = mission,
            streak = streak,
            onStartGame = { startGame() },
            showBanner = adsReady,
            showPrivacyOptions = privacyRequired,
            onPrivacyOptions = onPrivacyOptions
        )
    }
}

@Composable
fun StartScreen(
    mission: DailyMission,
    streak: Int,
    onStartGame: () -> Unit,
    showBanner: Boolean = false,
    showPrivacyOptions: Boolean = false,
    onPrivacyOptions: () -> Unit = {}
) {
    val primaryColor = Color(0xFF2563EB)
    val context = LocalContext.current
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
                .padding(horizontal = 28.dp, vertical = 36.dp)
                .padding(bottom = if (showBanner) 80.dp else 0.dp),
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
            Spacer(Modifier.height(32.dp))

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
        if (showBanner) {
            BannerAd(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
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
