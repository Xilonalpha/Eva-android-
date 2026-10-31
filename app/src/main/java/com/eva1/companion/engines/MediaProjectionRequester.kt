package com.eva1.companion.engines

import android.content.Intent

/**
 * Punte globală pentru solicitarea permisiunii MediaProjection API din orice componentă sau ecran al aplicației.
 * Permite declanșarea prompt-ului de sistem Android pentru captură ecran în timp real (30-60 FPS)
 * și lansarea automată a jocului țintă imediat după acordarea permisiunii.
 */
object MediaProjectionRequester {
    @Volatile
    var pendingPackageToLaunch: String? = null

    var onRequestMediaProjection: (() -> Unit)? = null
    var onProjectionResult: ((resultCode: Int, data: Intent) -> Unit)? = null

    fun requestMediaProjection(launchPackageAfterGrant: String? = null): Boolean {
        if (!launchPackageAfterGrant.isNullOrBlank()) {
            pendingPackageToLaunch = launchPackageAfterGrant
        }
        val action = onRequestMediaProjection ?: return false
        action.invoke()
        return true
    }

    fun consumePendingPackageToLaunch(): String? {
        val pkg = pendingPackageToLaunch
        pendingPackageToLaunch = null
        return pkg
    }

    val isAvailable: Boolean
        get() = onRequestMediaProjection != null
}
