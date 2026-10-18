package com.example.learning

enum class PersonalSyncDecision { CURRENT, UPLOAD, CHOOSE }

object PersonalSyncPolicy {
    fun decide(localHash: String, remoteHash: String, lastHash: String?, remoteVersion: Long, lastVersion: Long): PersonalSyncDecision {
        if (localHash == remoteHash) return PersonalSyncDecision.CURRENT
        if (remoteVersion == lastVersion && localHash == lastHash) return PersonalSyncDecision.CURRENT
        if (remoteVersion == lastVersion && (lastHash != null || remoteVersion == 0L)) return PersonalSyncDecision.UPLOAD
        return PersonalSyncDecision.CHOOSE
    }
}
