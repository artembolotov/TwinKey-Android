package com.artembolotov.twinkey.domain

// Порт Generator из iOS (Generator.swift)
data class OtpGenerator(
    val secret: ByteArray,
    val factor: OtpFactor = OtpFactor.Timer(30),
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = 6
) {
    // ByteArray в data class требует ручного equals/hashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OtpGenerator) return false
        return secret.contentEquals(other.secret) &&
                factor == other.factor &&
                algorithm == other.algorithm &&
                digits == other.digits
    }

    override fun hashCode(): Int {
        var result = secret.contentHashCode()
        result = 31 * result + factor.hashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + digits
        return result
    }

    val periodOrDefault: Int get() = (factor as? OtpFactor.Timer)?.period ?: 30
}
