package com.doubao.mobilesub

import kotlin.math.sqrt

/**
 * 语音分段器：按静音/最大时长把连续音频切成短句交给识别。
 * 与桌面版同款逻辑。输入 16k 单声道 float(-1..1)。
 */
class Segmenter(
    private val sampleRate: Int = 16000,
    private val maxSec: Float = 5.0f,
    private val silenceSec: Float = 1.0f,
    private val threshold: Float = 0.012f
) {
    private var buf = FloatArray(0)
    private var silence = 0.0f
    private val tail = (0.35f * sampleRate).toInt()
    private val minSamples = (0.8f * sampleRate).toInt()
    private val maxSamples = (maxSec * sampleRate).toInt()
    private val silenceSamples = (silenceSec * sampleRate).toInt()

    fun feed(block: FloatArray): List<FloatArray> {
        val oldSize = buf.size
        val newSize = oldSize + block.size
        val nb = buf.copyOf(newSize)
        System.arraycopy(block, 0, nb, oldSize, block.size)
        buf = nb

        var sum = 0.0
        for (v in block) sum += v * v
        val rms = if (block.isEmpty()) 0.0 else sqrt(sum / block.size)
        silence = if (rms < threshold) silence + block.size.toFloat() / sampleRate else 0.0f

        val out = ArrayList<FloatArray>(1)
        val dur = buf.size.toFloat() / sampleRate
        if (buf.size < minSamples) return out

        // 静音达到阈值 或 缓冲超过最大时长（单位均为采样数）时切段
        if (silence * sampleRate >= silenceSamples || buf.size >= maxSamples) {
            if (buf.size > tail) {
                val chunk = buf.copyOfRange(0, buf.size - tail)
                buf = buf.copyOfRange(buf.size - tail, buf.size)
                out.add(chunk)
            } else {
                out.add(buf.copyOf())
                buf = FloatArray(0)
            }
            silence = 0.0f
        }
        return out
    }
}
