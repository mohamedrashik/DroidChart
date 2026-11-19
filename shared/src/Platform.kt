package com.droid.bootchart

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
