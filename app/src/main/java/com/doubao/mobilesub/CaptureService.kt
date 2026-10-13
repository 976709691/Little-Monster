package com.doubao.mobilesub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/**
 * 前台采集服务：
 * MediaProjection 抓取正在播放的声音 → 分段 → Whisper 识别 → 翻译 → 悬浮字幕。
 */
class CaptureService : Service() {

    companion object {
        private const val TAG = "MovieSub"
        private const val CHANNEL_ID = "capture_ch"
        private const val NOTIF_ID = 1
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
    }

    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private var captureThread: Thread? = null
    private var running = false
    private val handler = Handler(Looper.getMainLooper())
    private val trExec = Executors.newSingleThreadExecutor()
    private var whisper: WhisperEngine? = null
    private var translator: Translator? = null
    private var overlay: SubtitleOverlay? = null
    private val segmenter = Segmenter(16000)
    private var lastTrans = ""
    private var lastOrig = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()

        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null) {
            stopEverything()
            return START_NOT_STICKY
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)

        val cfg = AppConfig.load(this)
        overlay = SubtitleOverlay(this)
        overlay?.show()

        whisper = WhisperEngine(this, cfg) { text ->
            trExec.execute {
                val translated = translator?.translate(text) ?: text
                handler.post { showLine(text, translated) }
            }
        }
        val err = whisper?.init()
        if (err != null) {
            Log.e(TAG, "model init failed: $err")
            overlay?.update("", "模型加载失败：$err")
            stopEverything()
            return START_NOT_STICKY
        }
        translator = Translator(cfg)

        if (!startCapture()) {
            overlay?.update("", "音频捕获启动失败，请检查系统权限")
            stopEverything()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    // ------------------------------------------------------------- 启动/停止
    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID, "同声传译", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
        val notif = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("影视同声传译")
            .setContentText("正在监听系统声音并翻译…")
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    private fun startCapture(): Boolean {
        val mp = projection ?: return false
        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mp)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(16000)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val rec = try {
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(32000)
                .setAudioPlaybackCaptureConfig(playbackConfig)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord build failed", e)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized")
            return false
        }
        record = rec
        rec.startRecording()
        running = true
        captureThread = Thread { captureLoop(rec) }.apply { start() }
        return true
    }

    private fun captureLoop(rec: AudioRecord) {
        val buf = ShortArray(1600) // 100ms @ 16k
        while (running && !Thread.currentThread().isInterrupted) {
            val n = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
            if (n <= 0) continue
            val f = FloatArray(n)
            for (i in 0 until n) f[i] = buf[i] / 32768.0f
            for (chunk in segmenter.feed(f)) {
                whisper?.decode(chunk)
            }
        }
    }

    private fun showLine(orig: String, trans: String) {
        if (orig == lastOrig && trans == lastTrans) return
        lastOrig = orig
        lastTrans = trans
        overlay?.update(orig, trans)
    }

    private fun stopEverything() {
        running = false
        captureThread?.interrupt()
        captureThread = null
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        runCatching { projection?.stop() }
        projection = null
        whisper?.shutdown()
        whisper = null
        trExec.shutdown()
        overlay?.dismiss()
        overlay = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
