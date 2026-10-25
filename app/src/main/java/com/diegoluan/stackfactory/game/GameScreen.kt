package com.diegoluan.stackfactory.game

import android.content.Context
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

private data class TowerBlock(
    val x: Float,
    val width: Float
)

@Composable
fun GameScreen(
    onMissionUpdated: () -> Unit = {},
    onBackToMenu: () -> Unit,
    onGameFinished: () -> Unit = {},
    onProceedAfterGameOver: (() -> Unit) -> Unit = { proceed -> proceed() }
) {

    val context = LocalContext.current
    val missionManager = remember(context) { DailyMissionManager(context) }
    val density = LocalDensity.current

    /*
     * =========================================================
     * CONFIGURAÇÕES
     * =========================================================
     */

    val blockWidthDp = 180.dp
    val blockHeightDp = 50.dp

    val movingBlockYDp = 350.dp

    val movementSpeedDp = 6.dp

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

    var gameOver by remember {
        mutableStateOf(false)
    }

    var isPaused by remember {
        mutableStateOf(false)
    }

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

    val blockColor =
        Color(0xFF60A5FA)

    val movingBlockColor =
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

        gameOver = false

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
         * Largura real da área de jogo.
         */
        val gameWidthPx =
            totalWidthPx -
                    horizontalPaddingPx

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
         * Overlay escuro sutil.
         */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.10f
                    )
                )
        )

        /*
         * =====================================================
         * MOVIMENTO DO BLOCO
         * =====================================================
         */

        LaunchedEffect(
            gameOver,
            isPaused,
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
                !isPaused
            ) {

                delay(16)

                movingBlockX +=
                    with(density) {
                        movementSpeedDp.toPx()
                    } *
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
            if (isPaused) {
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

                gameOver = true
                missionManager.onGameFinished()
                onMissionUpdated()
                onGameFinished()

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

            towerBlocks =
                towerBlocks +
                        TowerBlock(
                            x = newX,
                            width = overlapWidth
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
                overlapWidth

            /*
             * =================================================
             * PONTUAÇÃO
             * =================================================
             */

            score++
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

                                restartGame(
                                    gameWidthPx
                                )
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
                    modifier =
                        Modifier.fillMaxSize()
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

                        val x =
                            centerX +
                                    block.x

                        drawRect(
                            color =
                                blockColor,
                            topLeft =
                                Offset(
                                    x =
                                        x -
                                                block.width /
                                                2f,
                                    y = y
                                ),
                            size =
                                androidx.compose.ui
                                    .geometry
                                    .Size(
                                        width =
                                            block.width,
                                        height =
                                            blockHeightPx
                                    )
                        )
                    }

                    /*
                     * =================================================
                     * BLOCO MÓVEL
                     * =================================================
                     *
                     * movingBlockX = lado esquerdo.
                     *
                     * movingBlockWidth = largura atual.
                     */

                    drawRect(
                        color =
                            movingBlockColor,
                        topLeft =
                            Offset(
                                x =
                                    movingBlockX,
                                y =
                                    movingBlockYDp
                                        .toPx()
                            ),
                        size =
                            androidx.compose.ui
                                .geometry
                                .Size(
                                    width =
                                        movingBlockWidth,
                                    height =
                                        blockHeightPx
                                )
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
                                text =
                                    "Toque em continuar para voltar",
                                color =
                                    Color.White.copy(
                                        alpha = 0.80f
                                    ),
                                fontSize = 14.sp
                            )
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

                            Button(
                                onClick = {
                                    onProceedAfterGameOver {
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
                                onClick = { onProceedAfterGameOver(onBackToMenu) },
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
    }
}
