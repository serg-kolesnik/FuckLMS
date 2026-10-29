package com.fucklms.app

import android.net.Uri

data class QrData(val sessionId: String, val qrPassword: String?)

fun parseQrUrl(qrUrl: String): QrData? {
    try {
        val uri = Uri.parse(qrUrl)
        val sessionId = uri.getQueryParameter("sessid") ?: return null

        // Ищем пароль под разными именами, которые может использовать Moodle
        val qrPassword = uri.getQueryParameter("qrpass")
            ?: uri.getQueryParameter("password")
            ?: uri.getQueryParameter("studentpassword")

        return QrData(sessionId, qrPassword)
    } catch (e: Exception) {
        return null
    }
}