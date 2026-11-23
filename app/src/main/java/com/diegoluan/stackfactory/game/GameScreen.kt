package com.diegoluan.stackfactory.game

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diegoluan.stackfactory.R
import com.diegoluan.stackfactory.daily.DailyMissionManager
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import com.diegoluan.stackfactory.game.skills.SkillCard
import com.diegoluan.stackfactory.game.skills.SkillDeck
import com.diegoluan.stackfactory.game.skills.SkillType

// A paleta segue o ícone do StackFactory (de baixo para cima).
// Apenas o desenho dos blocos utiliza estas cores; nenhuma regra do jogo depende delas.
private val neonTowerColors = listOf(
    Color(0xFF22F5A1), // Verde neon
    Color(0xFF1DDCFF), // Ciano / azul
    Color(0xFFFFC12B), // Dourado / laranja
    Color(0xFFFF44D4)  // Magenta
)

/**
 * Renderizador de blocos com aparência de vidro/neon, sem alterar a hitbox.
 * Usa a mesma posição e largura calculadas pela mecânica já existente.
 * O brilho, os relevos e o topo pseudo-3D são desenhados DENTRO do retângulo.
 */
private fun DrawScope.drawNeonStackBlock(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    accent: Color,
    isMoving: Boolean
) {
    if (width <= 0f || height <= 0f) return

    val topLeft = Offset(x, y)
    val bounds = Size(width, height)

    // Blocos extremamente finos não devem perder visibilidade por causa do chanfro.
    if (width < 4.dp.toPx()) {
        drawRect(color = accent, topLeft = topLeft, size = bounds)
        return
    }

    val radius = minOf(10.dp.toPx(), width * 0.18f, height * 0.22f)
    val corner = CornerRadius(radius, radius)
    val stroke = minOf(2.5.dp.toPx(), width * 0.13f, height * 0.12f)
    val glow = minOf(5.dp.toPx(), width * 0.18f, height * 0.18f)
    val bevel = minOf(9.dp.toPx(), width * 0.10f, height * 0.16f)
    val topFaceHeight = minOf(height * 0.30f, 14.dp.toPx())

    // Camada principal: corpo escuro com reflexo da cor neon.
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                accent.copy(alpha = if (isMoving) 0.94f else 0.84f),
                Color(0xFF12243D),
                accent.copy(alpha = 0.62f)
            ),
            start = Offset(x, y),
            end = Offset(x + width, y + height)
        ),
        topLeft = topLeft,
        size = bounds,
        cornerRadius = corner
    )

    // Volume e profundidade: área frontal inferior com leve sombra.
    val frontTop = y + topFaceHeight
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF071022).copy(alpha = 0.10f), Color(0xFF071022).copy(alpha = 0.52f)),
            startY = frontTop,
            endY = y + height
        ),
        topLeft = Offset(x + bevel, frontTop),
        size = Size((width - 2f * bevel).coerceAtLeast(0f), (height - topFaceHeight - bevel).coerceAtLeast(0f))
    )

    // Faceta superior semelhante aos quatro blocos da arte do aplicativo.
    val topFacet = Path().apply {
        moveTo(x + bevel, y + bevel)
        lineTo(x + width - bevel, y + bevel)
        lineTo(x + width - bevel * 0.7f, y + topFaceHeight)
        lineTo(x + bevel * 0.7f, y + topFaceHeight)
        close()
    }
    drawPath(
        path = topFacet,
        brush = Brush.verticalGradient(
            colors = listOf(accent.copy(alpha = 0.76f), accent.copy(alpha = 0.16f)),
            startY = y,
            endY = y + topFaceHeight
        )
    )

    // Faceta lateral que dá leitura de bloco tridimensional.
    if (width > 18.dp.toPx()) {
        val sideWidth = minOf(10.dp.toPx(), width * 0.15f)
        val sideFace = Path().apply {
            moveTo(x + width - sideWidth, y + topFaceHeight)
            lineTo(x + width - bevel * 0.5f, y + bevel)
            lineTo(x + width - bevel * 0.5f, y + height - bevel)
            lineTo(x + width - sideWidth, y + height - bevel)
            close()
        }
        drawPath(sideFace, color = Color(0xFF061125).copy(alpha = 0.33f))
    }

    // Mantém o contorno inteiro DENTRO da hitbox: nas bordas do Canvas,
    // nenhum lado perde metade do neon por recorte visual.
    val glowCornerRadius = (radius - glow / 2f).coerceAtLeast(0f)
    val strokeCornerRadius = (radius - stroke / 2f).coerceAtLeast(0f)
    drawRoundRect(
        color = accent.copy(alpha = if (isMoving) 0.32f else 0.21f),
        topLeft = Offset(x + glow / 2f, y + glow / 2f),
        size = Size((width - glow).coerceAtLeast(0f), (height - glow).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(glowCornerRadius, glowCornerRadius),
        style = Stroke(width = glow)
    )
    drawRoundRect(
        color = accent.copy(alpha = if (isMoving) 1f else 0.93f),
        topLeft = Offset(x + stroke / 2f, y + stroke / 2f),
        size = Size((width - stroke).coerceAtLeast(0f), (height - stroke).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(strokeCornerRadius, strokeCornerRadius),
        style = Stroke(width = stroke)
    )

    // Friso luminoso no topo e detalhe de luz no canto, sem ampliar a geometria.
    if (width > 22.dp.toPx()) {
        drawLine(
            color = Color.White.copy(alpha = if (isMoving) 0.90f else 0.64f),
            start = Offset(x + bevel * 1.6f, y + bevel),
            end = Offset(x + width - bevel * 1.6f, y + bevel),
            strokeWidth = minOf(1.4.dp.toPx(), stroke)
        )
    }
}

/**
 * Cenários progressivos — apenas visuais.
 * A pontuação continua sendo calculada exclusivamente pela mecânica do jogo.
 * Cada encaixe bem-sucedido vale um ponto e um novo bloco na torre.
 */
private enum class BackdropStage {
    DEFAULT,
    BRONZE,
    SILVER,
    GOLD;

    companion object {
        fun fromScore(score: Int): BackdropStage = when {
            score >= 100 -> GOLD
            score >= 75 -> SILVER
            score >= 40 -> BRONZE
            else -> DEFAULT
        }
    }
}

private data class BackdropPalette(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val glow: Color
)

/** Paletas escuras para preservar a leitura do HUD branco sobre o cenário. */
private fun backdropPalette(stage: BackdropStage): BackdropPalette = when (stage) {
    BackdropStage.DEFAULT -> BackdropPalette(
        Color.Transparent,
        Color.Transparent,
        Color.Transparent,
        Color.Transparent
    )
    BackdropStage.BRONZE -> BackdropPalette(
        Color(0xFFA35B30).copy(alpha = 0.71f),
        Color(0xFF5A331F).copy(alpha = 0.63f),
        Color(0xFF2D1D19).copy(alpha = 0.83f),
        Color(0xFFF7A65B).copy(alpha = 0.33f)
    )
    BackdropStage.SILVER -> BackdropPalette(
        Color(0xFF9BB6CE).copy(alpha = 0.57f),
        Color(0xFF58738E).copy(alpha = 0.64f),
        Color(0xFF1C2F46).copy(alpha = 0.83f),
        Color(0xFFD9EBFF).copy(alpha = 0.29f)
    )
    BackdropStage.GOLD -> BackdropPalette(
        Color(0xFFE3A83D).copy(alpha = 0.72f),
        Color(0xFF997022).copy(alpha = 0.69f),
        Color(0xFF392609).copy(alpha = 0.87f),
        Color(0xFFFFE18A).copy(alpha = 0.38f)
    )
}

private data class TowerBlock(
    val x: Float,
    val width: Float
)

@Composable
fun GameScreen(
    onMissionUpdated: () -> Unit = {},
    onBackToMenu: () -> Unit,
    onGameFinished: (Int) -> Unit = {},
    onScoreChanged: (Int) -> Unit = {},
    onProceedAfterGameOver: (() -> Unit) -> Unit = { proceed -> proceed() },
    onRewardedRecovery: (onEarned: () -> Unit) -> Unit = {},
    onBlockPlaced: () -> Unit = {},
    onGameRestarted: () -> Unit = {},
    onSkillSelected: (String) -> Unit = {}
) {

    val context = LocalContext.current
    val missionManager = remember(context) { DailyMissionManager(context) }
    val density = LocalDensity.current
    // A largura real do Canvas é a referência única para as bordas e colisões.
    // O cálculo por padding serve apenas até a primeira medição do layout.
    var measuredCanvasWidthPx by remember { mutableFloatStateOf(0f) }

    /*
     * =========================================================
     * CONFIGURAÇÕES
     * =========================================================
     */

    val blockWidthDp = 180.dp
    val blockHeightDp = 50.dp

    // Posição intermediária: mais baixa que a versão anterior (150.dp),
    // porém ainda acima da posição original (350.dp).
    // Em telas compactas, mantém distância da torre quando há espaço.
    val preferredMovingBlockYDp = 250.dp
    val movingBlockTowerGapDp = 24.dp
    val movingBlockTopMarginDp = 12.dp

    val cameraStartBlock = 5
    val cameraStepDp = 50.dp

    /*
     * =========================================================
     * PREFERÊNCIAS
     * =========================================================
     */

    val preferences = remember {
        context.getSharedPreferences(
            "stack_factory",
            Context.MODE_PRIVATE
        )
    }

    var highScore by remember {
        mutableIntStateOf(
            preferences.getInt(
                "high_score",
                0
            )
        )
    }

    /*
     * =========================================================
     * ESTADO DO JOGO
     * =========================================================
     */

    var score by remember {
        mutableIntStateOf(0)
    }

    // Velocidade inicial reduzida para permitir escolhas de cartas e progressão.
    // A cada ponto, aumenta 0,06 dp/frame até o máximo de 5,8 dp/frame.
    val movementSpeedDp = (2.2f + score.coerceAtMost(60) * 0.06f).dp

    // A troca de cenário só reage à pontuação. Não modifica movimento ou hitbox.
    val backdropStage = BackdropStage.fromScore(score)
    val backdrop = backdropPalette(backdropStage)
    val backdropTop by animateColorAsState(
        targetValue = backdrop.top,
        animationSpec = tween(durationMillis = 1100),
        label = "stackfactory-backdrop-top"
    )
    val backdropMiddle by animateColorAsState(
        targetValue = backdrop.middle,
        animationSpec = tween(durationMillis = 1100),
        label = "stackfactory-backdrop-middle"
    )
    val backdropBottom by animateColorAsState(
        targetValue = backdrop.bottom,
        animationSpec = tween(durationMillis = 1100),
        label = "stackfactory-backdrop-bottom"
    )
    val backdropGlow by animateColorAsState(
        targetValue = backdrop.glow,
        animationSpec = tween(durationMillis = 1100),
        label = "stackfactory-backdrop-glow"
    )

    var gameOver by remember {
        mutableStateOf(false)
    }

    // O fim temporário com opção de anúncio premiado NÃO conclui uma partida.
    // A contagem ocorre somente na transição definitiva para menu/reinício.
    val gameSessionCompletion = remember { GameSessionCompletion() }
    var exitPending by remember { mutableStateOf(false) }

    var isPaused by remember {
        mutableStateOf(false)
    }

    // Progressão e poderes: sempre zerados quando uma nova partida começa.
    var lives by remember { mutableIntStateOf(3) }
    var shield by remember { mutableIntStateOf(0) }
    var xp by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var xpBonus by remember { mutableIntStateOf(0) }
    var slowBlocks by remember { mutableIntStateOf(0) }
    var widthRecoveryCharges by remember { mutableIntStateOf(0) }
    var rewardedContinueUsed by remember { mutableStateOf(false) }
    var pickingSkill by remember { mutableStateOf(false) }
    var cardChoices by remember { mutableStateOf<List<SkillCard>>(emptyList()) }
    val xpGoal = 80 + (level - 1) * 35

    /*
     * =========================================================
     * BLOCO MÓVEL
     * =========================================================
     *
     * movingBlockX:
     *
     * posição do lado esquerdo do bloco
     * dentro da área de jogo.
     *
     * 0 = borda esquerda.
     */
    var movingBlockX by remember {
        mutableFloatStateOf(0f)
    }

    /*
     * Largura atual do bloco móvel.
     *
     * Começa com 180dp.
     *
     * Depois de cada encaixe:
     *
     * 180 -> 150 -> 120 -> 90...
     */
    var movingBlockWidth by remember {
        mutableFloatStateOf(
            with(density) {
                blockWidthDp.toPx()
            }
        )
    }

    /*
     * Direção:
     *
     * 1  = direita
     * -1 = esquerda
     */
    var movingDirection by remember {
        mutableFloatStateOf(1f)
    }

    /*
     * =========================================================
     * TORRE
     * =========================================================
     *
     * x = posição relativa ao centro da área.
     */
    var towerBlocks by remember {
        mutableStateOf(
            listOf(
                TowerBlock(
                    x = 0f,
                    width = with(density) {
                        blockWidthDp.toPx()
                    }
                )
            )
        )
    }

    /*
     * =========================================================
     * CÂMERA
     * =========================================================
     */

    var cameraOffset by remember {
        mutableFloatStateOf(0f)
    }

    /*
     * =========================================================
     * CORES
     * =========================================================
     */

    val primaryColor =
        Color(0xFF2563EB)

    val textPrimary =
        Color(0xFF0F172A)

    val textSecondary =
        Color(0xFF64748B)

    /*
     * =========================================================
     * REINICIAR JOGO
     * =========================================================
     */

    fun restartGame(
        gameWidthPx: Float
    ) {

        score = 0
        lives = 3
        shield = 0
        xp = 0
        level = 1
        xpBonus = 0
        slowBlocks = 0
        widthRecoveryCharges = 0
        rewardedContinueUsed = false
        pickingSkill = false
        cardChoices = emptyList()

        gameOver = false
        gameSessionCompletion.reset()
        exitPending = false

        isPaused = false

        /*
         * Começa novamente indo para a direita.
         */
        movingDirection = 1f

        /*
         * Restaura a largura original.
         */
        movingBlockWidth =
            with(density) {
                blockWidthDp.toPx()
            }

        /*
         * Começa na borda esquerda.
         */
        movingBlockX = 0f

        cameraOffset = 0f

        /*
         * Reinicia a torre.
         */
        towerBlocks = listOf(
            TowerBlock(
                x = 0f,
                width = with(density) {
                    blockWidthDp.toPx()
                }
            )
        )
        onGameRestarted()
    }

    /*
     * =========================================================
     * LAYOUT PRINCIPAL
     * =========================================================
     */

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {

        /*
         * =====================================================
         * DIMENSÕES
         * =====================================================
         */

        /*
         * Largura total da tela.
         */
        val totalWidthPx =
            with(density) {
                maxWidth.toPx()
            }

        /*
         * O Column possui:
         *
         * 16dp esquerda
         * 16dp direita
         *
         * Portanto:
         *
         * 32dp no total.
         */
        val horizontalPaddingPx =
            with(density) {
                32.dp.toPx()
            }

        /*
         * Limites horizontais: 0 até a borda direita real do Canvas.
         * Medir o Canvas remove discrepâncias de arredondamento e insets
         * entre a largura estimada do Box e a área efetivamente desenhada.
         */
        val gameWidthPx = if (measuredCanvasWidthPx > 0f) {
            measuredCanvasWidthPx
        } else {
            (totalWidthPx - horizontalPaddingPx).coerceAtLeast(0f)
        }

        /*
         * Largura original do bloco.
         */
        val blockWidthPx =
            with(density) {
                blockWidthDp.toPx()
            }

        /*
         * Altura do bloco.
         */
        val blockHeightPx =
            with(density) {
                blockHeightDp.toPx()
            }

        /*
         * =====================================================
         * BACKGROUND
         * =====================================================
         */

        Image(
            painter = painterResource(
                id = R.drawable.stack_factory_background
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        /*
         * CENÁRIOS POR PONTUAÇÃO
         * Padrão (0–39), Bronze (40–74), Prata (75–99), Ouro (100+).
         *
         * As duas camadas são desenhadas ATRÁS do HUD e do Canvas.
         * A imagem de fundo original permanece a mesma; o gradiente muda
         * apenas a ambientação, com transição de 1,1 segundo.
         */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(backdropTop, backdropMiddle, backdropBottom)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(backdropGlow, Color.Transparent),
                        center = Offset(totalWidthPx * 0.5f, 0f),
                        radius = totalWidthPx.coerceAtLeast(1f)
                    )
                )
        )

        // Contraste mínimo do cenário para os textos e os blocos neon.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.10f))
        )

        /*
         * =====================================================
         * MOVIMENTO DO BLOCO
         * =====================================================
         */

        LaunchedEffect(
            gameOver,
            isPaused,
            pickingSkill,
            slowBlocks,
            score,
            gameWidthPx,
            movingBlockWidth
        ) {

            /*
             * Limite máximo do lado esquerdo
             * do bloco.
             *
             * Exemplo:
             *
             * área = 1000
             * bloco = 180
             *
             * máximo = 820
             */
            val maxX =
                max(
                    0f,
                    gameWidthPx -
                            movingBlockWidth
                )

            /*
             * Garante que o bloco esteja
             * dentro da área.
             */
            movingBlockX =
                movingBlockX.coerceIn(
                    0f,
                    maxX
                )

            /*
             * Loop do movimento.
             */
            while (
                !gameOver &&
                !isPaused &&
                !pickingSkill
            ) {

                delay(16)

                movingBlockX +=
                    with(density) {
                        movementSpeedDp.toPx()
                    } *
                            (if (slowBlocks > 0) 0.60f else 1f) *
                            movingDirection

                /*
                 * =================================================
                 * BORDA ESQUERDA
                 * =================================================
                 */

                if (
                    movingBlockX <= 0f
                ) {

                    movingBlockX = 0f

                    movingDirection = 1f
                }

                /*
                 * =================================================
                 * BORDA DIREITA
                 * =================================================
                 */

                if (
                    movingBlockX >= maxX
                ) {

                    movingBlockX = maxX

                    movingDirection = -1f
                }
            }
        }

        /*
         * =====================================================
         * COLOCAR BLOCO
         * =====================================================
         */

        // Uma unica conclusao logica por partida (inclusive quando o jogador
        // volta ao menu ou reinicia antes de perder). Mantem metas e telemetria.
        fun completeGameOnce() {
            gameSessionCompletion.completeOnce {
                missionManager.onGameFinished()
                onMissionUpdated()
                onGameFinished(score)
            }
        }

        fun leaveActiveGame() {
            if (gameOver || exitPending) return
            exitPending = true
            completeGameOnce()
            onBackToMenu()
        }

        fun leaveFinishedGame(action: () -> Unit) {
            if (!gameOver || exitPending) return
            exitPending = true
            completeGameOnce()
            onProceedAfterGameOver(action)
        }

        // Voltar do Android abre a pausa; o usuario decide se sai.
        BackHandler(enabled = !exitPending) {
            if (gameOver) leaveFinishedGame(onBackToMenu)
            else isPaused = true
        }

        fun placeBlock() {

            /*
             * Se acabou o jogo,
             * tocar reinicia.
             */
            if (gameOver) return

            /*
             * Se estiver pausado,
             * não faz nada.
             */
            if (isPaused || pickingSkill) {
                return
            }

            /*
             * Último bloco da torre.
             */
            val previousBlock =
                towerBlocks.last()

            /*
             * =================================================
             * BLOCO MÓVEL
             * =================================================
             */

            val movingLeft =
                movingBlockX

            val movingRight =
                movingBlockX +
                        movingBlockWidth

            /*
             * =================================================
             * BLOCO DA TORRE
             * =================================================
             *
             * A torre utiliza coordenadas relativas
             * ao centro da área.
             */
            val centerX =
                gameWidthPx / 2f

            val previousLeft =
                centerX +
                        previousBlock.x -
                        previousBlock.width / 2f

            val previousRight =
                centerX +
                        previousBlock.x +
                        previousBlock.width / 2f

            /*
             * =================================================
             * SOBREPOSIÇÃO
             * =================================================
             */

            val overlapLeft =
                max(
                    movingLeft,
                    previousLeft
                )

            val overlapRight =
                min(
                    movingRight,
                    previousRight
                )

            val overlapWidth =
                overlapRight -
                        overlapLeft

            /*
             * =================================================
             * ERRO
             * =================================================
             *
             * Se não houver sobreposição,
             * o jogador perdeu.
             */
            if (overlapWidth <= 0f) {
                if (shield > 0) {
                    shield--
                } else {
                    lives--
                }
                if (lives <= 0) {
                    // Ainda é possível continuar pelo anúncio premiado.
                    // Não registrar partida concluída neste ponto.
                    gameOver = true
                } else {
                    // Errou, mas a partida não acaba: bloco volta ao início.
                    movingBlockX = 0f
                    movingDirection = 1f
                }
                return
            }

            /*
             * =================================================
             * NOVA POSIÇÃO
             * =================================================
             */

            val overlapCenter =
                (
                        overlapLeft +
                                overlapRight
                        ) / 2f

            /*
             * Converte para coordenada
             * relativa ao centro.
             */
            val newX =
                overlapCenter -
                        centerX

            /*
             * =================================================
             * ADICIONA BLOCO
             * =================================================
             */

            // Uma carga de seguro impede uma redução excessiva na largura.
            val adjustedWidth = if (widthRecoveryCharges > 0 &&
                overlapWidth < previousBlock.width * 0.70f) {
                widthRecoveryCharges--
                previousBlock.width.coerceAtMost(blockWidthPx)
            } else overlapWidth
            towerBlocks =
                towerBlocks +
                        TowerBlock(
                            x = newX,
                            width = adjustedWidth
                        )

            /*
             * =================================================
             * DIMINUI O PRÓXIMO BLOCO
             * =================================================
             *
             * Essa é a parte principal:
             *
             * Se o encaixe teve 140px,
             * o próximo bloco terá 140px.
             */
            movingBlockWidth =
                adjustedWidth

            /*
             * =================================================
             * PONTUAÇÃO
             * =================================================
             */

            score++
            // Observadores de ranking e check-in; não alteram a pontuação.
            onScoreChanged(score)
            // Apenas feedback sonoro após o encaixe válido.
            // Não altera física, colisão, velocidade, pontuação ou câmera.
            onBlockPlaced()
            if (slowBlocks > 0) slowBlocks--
            // Encaixe próximo da largura anterior recebe bônus de precisão.
            val earnedXp = 10 + xpBonus +
                (if (overlapWidth >= previousBlock.width * 0.97f) 5 else 0)
            xp += earnedXp
            if (xp >= xpGoal) {
                xp -= xpGoal
                level++
                cardChoices = SkillDeck.draw(3)
                pickingSkill = true
            }
            missionManager.onBlockPlaced()
            missionManager.onScoreChanged(score)
            onMissionUpdated()

            /*
             * =================================================
             * RECORDE
             * =================================================
             */

            if (
                score >
                highScore
            ) {

                highScore = score

                preferences
                    .edit()
                    .putInt(
                        "high_score",
                        highScore
                    )
                    .apply()
            }

            /*
             * =================================================
             * PRÓXIMO MOVIMENTO
             * =================================================
             */

            movingDirection *= -1f

            /*
             * Volta para a esquerda.
             */
            movingBlockX = 0f

            /*
             * =================================================
             * CÂMERA
             * =================================================
             */

            if (
                towerBlocks.size >
                cameraStartBlock
            ) {

                cameraOffset =
                    (
                            towerBlocks.size -
                                    cameraStartBlock
                            ) *
                            with(density) {
                                cameraStepDp.toPx()
                            }
            }
        }

        // Escolher uma carta aplica o efeito na partida atual.
        fun activateSkill(card: SkillCard) {
            when (card.type) {
                SkillType.RESTORE_WIDTH -> {
                    val target = blockWidthPx.coerceAtMost(gameWidthPx)
                    val top = towerBlocks.last()
                    towerBlocks = towerBlocks.dropLast(1) + top.copy(width = target)
                    movingBlockWidth = target
                    movingBlockX = movingBlockX.coerceIn(0f, (gameWidthPx - target).coerceAtLeast(0f))
                }
                SkillType.EXTRA_LIFE -> lives = (lives + 1).coerceAtMost(5)
                SkillType.SHIELD -> shield++
                SkillType.SLOW_TIME -> slowBlocks += 5
                SkillType.XP_BOOST -> xpBonus += 3
                SkillType.WIDTH_INSURANCE -> widthRecoveryCharges++
            }
            pickingSkill = false
            onSkillSelected(card.type.name)
        }

        /*
         * =====================================================
         * INTERFACE
         * =====================================================
         */

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 12.dp
                )
        ) {

            /*
             * =================================================
             * HUD
             * =================================================
             */

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                /*
                 * PONTOS
                 */
                Column {

                    Text(
                        text = "PONTOS",
                        color =
                            Color.White.copy(
                                alpha = 0.75f
                            ),
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            score.toString(),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                /*
                 * RECORDE
                 */
                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "RECORDE",
                        color =
                            Color.White.copy(
                                alpha = 0.75f
                            ),
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            highScore.toString(),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                /*
                 * BOTÕES
                 */
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    /*
                     * =================================================
                     * PAUSA
                     * =================================================
                     */

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(
                                CircleShape
                            )
                            .background(
                                Color.White.copy(
                                    alpha = 0.16f
                                )
                            )
                            .clickable {

                                if (!gameOver) {

                                    isPaused =
                                        !isPaused
                                }
                            },
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text =
                                if (isPaused) {
                                    "▶"
                                } else {
                                    "Ⅱ"
                                },
                            color =
                                Color.White,
                            fontSize = 18.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    /*
                     * =================================================
                     * REINICIAR
                     * =================================================
                     */

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(
                                CircleShape
                            )
                            .background(
                                Color.White.copy(
                                    alpha = 0.16f
                                )
                            )
                            .clickable {

                                if (!exitPending) {
                                    if (gameOver) {
                                        leaveFinishedGame { restartGame(gameWidthPx) }
                                    } else {
                                        // Reiniciar tambem encerra a partida anterior.
                                        completeGameOnce()
                                        restartGame(gameWidthPx)
                                    }
                                }
                            },
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text = "↻",
                            color =
                                Color.White,
                            fontSize = 22.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            Text(
                text = "❤ $lives    🛡 $shield    NÍVEL $level  •  $xp / $xpGoal XP",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (xp.toFloat() / xpGoal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                color = Color(0xFF60A5FA),
                trackColor = Color.White.copy(alpha = 0.25f)
            )
            Spacer(Modifier.height(10.dp))

            /*
             * =================================================
             * ÁREA DO JOGO
             * =================================================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(
                        RoundedCornerShape(
                            24.dp
                        )
                    )
                    .background(
                        Color.White.copy(
                            alpha = 0.08f
                        )
                    )
                    .border(
                        width = 1.dp,
                        color =
                            Color.White.copy(
                                alpha = 0.20f
                            ),
                        shape =
                            RoundedCornerShape(
                                24.dp
                            )
                    )
                    .clickable {
                        placeBlock()
                    }
            ) {

                /*
                 * =================================================
                 * TORRE + BLOCO MÓVEL
                 * =================================================
                 */

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize ->
                            // Mesma largura usada para mover, posicionar e colidir.
                            val actualWidth = canvasSize.width.toFloat()
                            if (actualWidth > 0f && actualWidth != measuredCanvasWidthPx) {
                                measuredCanvasWidthPx = actualWidth
                            }
                        }
                ) {

                    val centerX =
                        size.width / 2f

                    /*
                     * =================================================
                     * TORRE
                     * =================================================
                     */

                    towerBlocks.forEachIndexed {
                            index,
                            block
                        ->

                        val y =
                            size.height -
                                    blockHeightPx -
                                    (
                                            index *
                                                    blockHeightPx
                                            ) +
                                    cameraOffset

                        // Evita renderizar blocos fora do viewport; a torre,
                        // a hitbox, o score e a camera permanecem intactos.
                        if (y + blockHeightPx < 0f || y > size.height) {
                            return@forEachIndexed
                        }

                        val x =
                            centerX +
                                    block.x

                        drawNeonStackBlock(
                            x = x - block.width / 2f,
                            y = y,
                            width = block.width,
                            height = blockHeightPx,
                            accent = neonTowerColors[index % neonTowerColors.size],
                            isMoving = false
                        )
                    }

                    /*
                     * =================================================
                     * BLOCO MÓVEL
                     * =================================================
                     *
                     * A altura da barra é apenas visual: a lógica de
                     * posicionamento horizontal e colisão permanece igual.
                     * No espaço disponível, evita que a barra seja desenhada
                     * sobre o último bloco da torre em telas menores.
                     */
                    val towerTopY = size.height -
                        towerBlocks.size * blockHeightPx + cameraOffset
                    val movingBlockY = min(
                        preferredMovingBlockYDp.toPx(),
                        towerTopY - blockHeightPx - movingBlockTowerGapDp.toPx()
                    ).coerceAtLeast(movingBlockTopMarginDp.toPx())

                    drawNeonStackBlock(
                        x = movingBlockX,
                        y = movingBlockY,
                        width = movingBlockWidth,
                        height = blockHeightPx,
                        accent = neonTowerColors[towerBlocks.size % neonTowerColors.size],
                        isMoving = true
                    )
                }

                /*
                 * =================================================
                 * PAUSA
                 * =================================================
                 */

                if (
                    isPaused &&
                    !gameOver
                ) {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Black.copy(
                                        alpha = 0.42f
                                    )
                                ),
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Text(
                                text =
                                    "JOGO PAUSADO",
                                color =
                                    Color.White,
                                fontSize = 26.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        8.dp
                                    )
                            )

                            Text(
                                text = "A partida fica pausada ate voce continuar.",
                                color = Color.White.copy(alpha = 0.80f),
                                fontSize = 14.sp
                            )

                            Spacer(Modifier.height(22.dp))
                            Button(
                                enabled = !exitPending,
                                onClick = { isPaused = false }
                            ) {
                                Text("CONTINUAR", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                enabled = !exitPending,
                                onClick = { leaveActiveGame() },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.70f))
                            ) {
                                Text("VOLTAR AO MENU", color = Color.White,
                                    fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Sair encerra a partida atual.",
                                color = Color.White.copy(alpha = 0.68f),
                                fontSize = 12.sp)
                        }
                    }
                }

                /*
                 * =================================================
                 * GAME OVER
                 * =================================================
                 */

                if (gameOver) {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Black.copy(
                                        alpha = 0.48f
                                    )
                                ),
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Column(
                            modifier =
                                Modifier
                                    .padding(
                                        24.dp
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            24.dp
                                        )
                                    )
                                    .background(
                                        Color.White.copy(
                                            alpha = 0.96f
                                        )
                                    )
                                    .padding(
                                        horizontal = 28.dp,
                                        vertical = 30.dp
                                    ),
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Text(
                                text =
                                    "Fim de jogo",
                                color =
                                    textPrimary,
                                fontSize = 26.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        8.dp
                                    )
                            )

                            Text(
                                text =
                                    "Sua torre caiu.",
                                color =
                                    textSecondary,
                                fontSize = 15.sp
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        20.dp
                                    )
                            )

                            Text(
                                text =
                                    score.toString(),
                                color =
                                    primaryColor,
                                fontSize = 42.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                text =
                                    "pontos",
                                color =
                                    textSecondary,
                                fontSize = 13.sp
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        22.dp
                                    )
                            )

                            if (!rewardedContinueUsed) {
                                OutlinedButton(
                                    enabled = !exitPending,
                                    onClick = {
                                        onRewardedRecovery {
                                            rewardedContinueUsed = true
                                            gameOver = false
                                            lives = 1
                                            val top = towerBlocks.last()
                                            val restored = blockWidthPx.coerceAtMost(gameWidthPx)
                                            towerBlocks = towerBlocks.dropLast(1) + top.copy(width = restored)
                                            movingBlockWidth = restored
                                            movingBlockX = 0f
                                            movingDirection = 1f
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("REVIVER COM ANÚNCIO", fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            Button(
                                enabled = !exitPending,
                                onClick = {
                                    leaveFinishedGame {
                                        restartGame(gameWidthPx)
                                    }
                                },
                                colors =
                                    ButtonDefaults
                                        .buttonColors(
                                            containerColor =
                                                primaryColor
                                        ),
                                shape =
                                    RoundedCornerShape(
                                        14.dp
                                    )
                            ) {

                                Text(
                                    text =
                                        "JOGAR NOVAMENTE",
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }

                            Spacer(
                                modifier = Modifier.height(12.dp)
                            )

                            OutlinedButton(
                                enabled = !exitPending,
                                onClick = { leaveFinishedGame(onBackToMenu) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = primaryColor
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = primaryColor
                                )
                            ) {
                                Text(
                                    text = "VOLTAR AO MENU",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            /*
             * =================================================
             * INSTRUÇÃO
             * =================================================
             */

            Text(
                text =
                    "Toque na tela para posicionar o bloco",
                modifier =
                    Modifier.fillMaxWidth(),
                color =
                    Color.White.copy(
                        alpha = 0.82f
                    ),
                fontSize = 13.sp,
                fontWeight =
                    FontWeight.Medium,
                textAlign =
                    TextAlign.Center
            )
        }
        if (pickingSkill && !gameOver) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF16233B)).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("NÍVEL $level ALCANÇADO!", color = Color(0xFFFBBF24),
                        fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Escolha uma habilidade gratuita", color = Color.White, fontSize = 14.sp)
                    cardChoices.forEach { card ->
                        Box(modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF293E60))
                            .clickable { activateSkill(card) }.padding(15.dp)) {
                            Column {
                                Text(card.name, color = Color(0xFFBFDBFE),
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(card.description, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
