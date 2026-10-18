package com.example.learning

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.StudyDatabase
import com.example.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class PersonalStudySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val state = applicationContext.getSharedPreferences("online_account_state", Context.MODE_PRIVATE)
        val owner = state.getString("last_owner", null) ?: return@withContext Result.success()
        val encrypted = applicationContext.getSharedPreferences("online_session_vault", Context.MODE_PRIVATE)
            .getString(owner, null) ?: return@withContext Result.success()
        val session = runCatching { JSONObject(SecurityUtils.decryptAes(encrypted)) }.getOrNull() ?: return@withContext Result.success()
        val endpoint = session.optString("endpoint").trimEnd('/')
        val token = session.optString("token")
        val accountId = session.optLong("accountId")
        if (endpoint.isBlank() || token.isBlank() || accountId <= 0 || session.optLong("expiresAt") <= System.currentTimeMillis()) return@withContext Result.success()

        val scope = AccountScope.localId(endpoint, accountId) + ":" + owner
        val syncPrefs = applicationContext.getSharedPreferences("personal_study_sync", Context.MODE_PRIVATE)
        try {
            val local = PersonalStudyStore(StudyDatabase.getDatabase(applicationContext)).capture(owner)
            val localHash = PersonalStudyStore.hash(local)
            val remote = JSONObject(call(endpoint, token, "GET", "personal-study", null))
            val version = remote.getLong("version")
            val cloud = PersonalStudyStore.normalize(remote.getJSONObject("data"))
            val decision = PersonalSyncPolicy.decide(
                localHash, PersonalStudyStore.hash(cloud), syncPrefs.getString("$scope:hash", null),
                version, syncPrefs.getLong("$scope:version", 0L)
            )
            when (decision) {
                PersonalSyncDecision.CURRENT -> syncPrefs.edit().putLong("$scope:version", version).putString("$scope:hash", localHash).remove("$scope:conflict").apply()
                PersonalSyncDecision.UPLOAD -> {
                    val body = JSONObject().put("expectedVersion", version)
                        .put("requestId", UUID.randomUUID().toString()).put("data", local)
                    val uploaded = JSONObject(call(endpoint, token, "PUT", "personal-study", body))
                    syncPrefs.edit().putLong("$scope:version", uploaded.getLong("version"))
                        .putString("$scope:hash", localHash).remove("$scope:conflict").apply()
                }
                PersonalSyncDecision.CHOOSE -> syncPrefs.edit().putBoolean("$scope:conflict", true).apply()
            }
            Result.success()
        } catch (e: HttpFailure) {
            if (e.status == 409) syncPrefs.edit().putBoolean("$scope:conflict", true).apply()
            if (e.status in 400..499) Result.success() else Result.retry()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun call(endpoint: String, token: String, method: String, path: String, body: JSONObject?): String {
        val builder = Request.Builder().url("$endpoint/api/$path").header("Authorization", "Bearer $token")
        if (method == "GET") builder.get() else builder.method(method, (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpFailure(response.code)
            return text
        }
    }

    private class HttpFailure(val status: Int) : Exception()

    companion object {
        private const val ONE_TIME = "personal_study_auto_sync"
        private const val PERIODIC = "personal_study_periodic_sync"
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun scheduleChanged(context: Context) {
            val request = OneTimeWorkRequestBuilder<PersonalStudySyncWorker>()
                .setConstraints(constraints).setInitialDelay(12, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, request)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<PersonalStudySyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
