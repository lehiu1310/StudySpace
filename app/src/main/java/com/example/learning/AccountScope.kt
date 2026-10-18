package com.example.learning

object AccountScope {
    fun localId(endpoint: String, remoteId: Long): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(endpoint.trim().trimEnd('/').toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "online:$digest:$remoteId"
    }
}
