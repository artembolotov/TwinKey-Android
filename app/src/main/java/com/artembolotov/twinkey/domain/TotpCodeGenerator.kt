package com.artembolotov.twinkey.domain

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Порт Generator.swift (OneTimePassword/Generator.swift).
 * Реализует RFC 4226 (HOTP) и RFC 6238 (TOTP).
 *
 * Алгоритм (строка за строкой как в iOS):
 * 1. counter = timestamp_seconds / period  (для TOTP)
 * 2. hash = HMAC-SHA(secret, counter_as_big_endian_8_bytes)
 * 3. offset = последние 4 бита последнего байта хэша
 * 4. truncated = 4 байта от offset, big-endian UInt32, MSB обнулён
 * 5. code = (truncated % 10^digits), дополненный нулями до digits символов
 */
object TotpCodeGenerator {

    // Таблица степеней 10 для digits 6..8 (без Float-арифметики для точности)
    private val POWERS = intArrayOf(1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000)

    fun generateCode(generator: OtpGenerator, timestampMs: Long = System.currentTimeMillis()): String {
        val counter = counterValue(generator.factor, timestampMs)
        val hash = generateHmac(generator.secret, counter, generator.algorithm)
        val code = truncate(hash, generator.digits)
        return code.toString().padStart(generator.digits, '0')
    }

    /**
     * Секунды до обновления кода.
     * Например: при period=30 и текущей секунде=45 → остаток = 15
     */
    fun secondsRemaining(period: Int, timestampMs: Long = System.currentTimeMillis()): Int {
        val seconds = timestampMs / 1000
        return period - (seconds % period).toInt()
    }

    // --- Внутренние функции ---

    private fun counterValue(factor: OtpFactor, timestampMs: Long): Long {
        return when (factor) {
            is OtpFactor.Timer -> (timestampMs / 1000) / factor.period
            is OtpFactor.Counter -> factor.value
        }
    }

    private fun generateHmac(secret: ByteArray, counter: Long, algorithm: OtpAlgorithm): ByteArray {
        val macAlgorithm = when (algorithm) {
            OtpAlgorithm.SHA1 -> "HmacSHA1"
            OtpAlgorithm.SHA256 -> "HmacSHA256"
            OtpAlgorithm.SHA512 -> "HmacSHA512"
        }
        val mac = Mac.getInstance(macAlgorithm)
        mac.init(SecretKeySpec(secret, macAlgorithm))

        // Counter как big-endian 8-байтовый массив (как в iOS: counter.bigEndian)
        val counterBytes = ByteArray(8)
        var c = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (c and 0xFF).toByte()
            c = c ushr 8
        }
        return mac.doFinal(counterBytes)
    }

    private fun truncate(hash: ByteArray, digits: Int): Int {
        // offset = последние 4 бита последнего байта (iOS: ptr[hash.count - 1] & 0x0f)
        val offset = (hash.last().toInt() and 0x0f)

        // 4 байта от offset в big-endian, MSB обнулён (iOS: UInt32(bigEndian: ...) & 0x7fffffff)
        val truncated = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)

        return truncated % POWERS[digits]
    }
}
