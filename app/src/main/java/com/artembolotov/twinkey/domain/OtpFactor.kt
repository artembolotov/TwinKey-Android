package com.artembolotov.twinkey.domain

// Порт Generator.Factor из iOS
sealed class OtpFactor {
    data class Timer(val period: Int = 30) : OtpFactor()
    data class Counter(val value: Long) : OtpFactor()
}
