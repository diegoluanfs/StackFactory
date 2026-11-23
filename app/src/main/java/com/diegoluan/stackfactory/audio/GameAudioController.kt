package com.diegoluan.stackfactory.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.diegoluan.stackfactory.R

/** Áudio local do jogo: música original em loop e impacto de bloco, sem rede. */
class GameAudioController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("stackfactory_audio", Context.MODE_PRIVATE)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    var musicEnabled: Boolean = prefs.getBoolean("music_enabled", true)
        private set
    var effectsEnabled: Boolean = prefs.getBoolean("effects_enabled", true)
        private set

    private var resumed = false
    private var adInFront = false
    private var released = false
    private var focusHeld = false

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_GAME)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        mainHandler.post {
            if (released) return@post
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    focusHeld = true
                    player?.setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
                    startMusicIfAppropriate()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    player?.setVolume(0.04f, 0.04f)
                }
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    focusHeld = false
                    pauseMusic()
                }
            }
        }
    }

    private val focusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= 26) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    } else null

    private val player: MediaPlayer? = try {
        MediaPlayer.create(appContext, R.raw.stackfactory_ambient)?.apply {
            isLooping = true
            setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
        }
    } catch (exception: Exception) {
        Log.w(TAG, "Música não pôde ser preparada", exception)
        null
    }

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var impactId: Int = 0
    private var impactReady = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (sampleId == impactId && status == 0) impactReady = true
        }
        impactId = soundPool.load(appContext, R.raw.stack_impact, 1)
    }

    fun onResume() {
        resumed = true
        startMusicIfAppropriate()
    }

    fun onPause() {
        resumed = false
        pauseMusic()
        abandonFocus()
    }

    fun pauseForAd() {
        adInFront = true
        pauseMusic()
        abandonFocus()
    }

    fun resumeAfterAd() {
        adInFront = false
        startMusicIfAppropriate()
    }

    fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        prefs.edit().putBoolean("music_enabled", enabled).apply()
        if (enabled) startMusicIfAppropriate() else {
            pauseMusic()
            abandonFocus()
        }
    }

    fun setEffectsEnabled(enabled: Boolean) {
        effectsEnabled = enabled
        prefs.edit().putBoolean("effects_enabled", enabled).apply()
    }

    /** Chamado uma vez após um encaixe válido; não influencia a física. */
    fun playBlockImpact() {
        if (released || !effectsEnabled || !resumed || adInFront || !impactReady) return
        soundPool.play(impactId, EFFECT_VOLUME, EFFECT_VOLUME, 1, 0, 1.0f)
    }

    private fun startMusicIfAppropriate() {
        if (released || !resumed || adInFront || !musicEnabled || player == null) return
        if (!focusHeld && !requestFocus()) return
        try {
            player.setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
            if (!player.isPlaying) player.start()
        } catch (exception: IllegalStateException) {
            Log.w(TAG, "Áudio indisponível durante retomada", exception)
        }
    }

    private fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= 26) {
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focusHeld
    }

    private fun pauseMusic() {
        try {
            if (player?.isPlaying == true) player.pause()
        } catch (_: IllegalStateException) {
            // A interface permanece funcional mesmo que o áudio falhe.
        }
    }

    private fun abandonFocus() {
        if (!focusHeld) return
        if (Build.VERSION.SDK_INT >= 26) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        focusHeld = false
    }

    fun release() {
        if (released) return
        pauseMusic()
        abandonFocus()
        released = true
        player?.release()
        soundPool.release()
    }

    private companion object {
        const val TAG = "StackFactoryAudio"
        const val MUSIC_VOLUME = 0.24f
        const val EFFECT_VOLUME = 0.48f
    }
}
