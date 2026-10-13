package com.doubao.mobilesub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Segmenter（静音分段器）单元测试：分段时机、最长时长、尾部保留。
 * 纯 JVM 测试，无需设备。
 */
class SegmenterTest {

    private val sr = 16000

    private fun voice(sec: Float): FloatArray = FloatArray((sec * sr).toInt()) { 0.1f }

    private fun silence(sec: Float): FloatArray = FloatArray((sec * sr).toInt()) { 0f }

    @Test
    fun `短于最短时长不输出`() {
        val s = Segmenter(sampleRate = sr)
        val out = s.feed(voice(0.5f))
        assertTrue("0.5s 音频不应触发分段", out.isEmpty())
    }

    @Test
    fun `静音达到阈值时输出分段并保留尾部`() {
        val s = Segmenter(sampleRate = sr)
        // 2s 语音 + 1.2s 静音（阈值 1.0s）
        assertTrue(s.feed(voice(2f)).isEmpty())
        val out = s.feed(silence(1.2f))
        assertEquals("应输出 1 段", 1, out.size)
        // 段长 = (2+1.2)s*16k - tail(0.35s*16k=5600)
        val tail = (0.35f * sr).toInt()
        assertEquals(32000 + 19200 - tail, out[0].size)
    }

    @Test
    fun `超过最大时长时输出分段`() {
        val s = Segmenter(sampleRate = sr)
        assertTrue(s.feed(voice(2f)).isEmpty())
        assertTrue(s.feed(voice(2f)).isEmpty())
        val out = s.feed(voice(2f)) // 累计 6s > 5s 上限
        assertEquals("应输出 1 段", 1, out.size)
        val tail = (0.35f * sr).toInt()
        assertEquals(6 * sr - tail, out[0].size)
    }

    @Test
    fun `分段后缓冲区保留尾部用于下句衔接`() {
        val s = Segmenter(sampleRate = sr)
        s.feed(voice(2f))
        val out = s.feed(silence(1.2f))
        assertEquals(1, out.size)
        // 分段后再次喂入数据，下一段仍可正常触发
        assertTrue(s.feed(voice(2f)).isEmpty())
        val out2 = s.feed(silence(1.2f))
        assertEquals("第二段应正常输出", 1, out2.size)
    }
}
