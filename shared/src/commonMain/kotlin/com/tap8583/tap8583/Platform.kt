package com.tap8583.tap8583

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform