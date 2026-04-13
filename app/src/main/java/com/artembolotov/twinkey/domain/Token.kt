package com.artembolotov.twinkey.domain

import java.util.UUID

// Порт Token из iOS (Token.swift)
data class Token(
    val id: String = UUID.randomUUID().toString(),
    val name: String,       // username / email
    val issuer: String = "", // service name (Google, GitHub, etc.)
    val generator: OtpGenerator
)
