package com.nexuzy.samvixo.workers

import android.content.Context
import androidx.work.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * MessageCleanupWorker
 *
 * Runs once per day via WorkManager. Deletes Firestore messages whose
 * `expireAt` (Timestamp) is in the past.
 *
 * Message auto-expiry works in two layers:
 *  1. Every outgoing message gets `expireAt = now + retention` set in
 *     ChatDetailScreen when delivery is confirmed (status == "delivered").
 *  2. This worker hard-deletes them on the client side after expiry.
 *  3. On the server side: set a Firestore TTL policy on `messages.expireAt`
 *     in the Firebase console (Firestore > Indexes > TTL) — this deletes
 *     docs server-side for free without Cloud Function charges.
 *
 * Default retention = 30 days (configurable from admin_config/settings doc).
 */
class MessageCleanupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = FirebaseFirestore.getInstance()
            val now = Timestamp.now()

            // Query ALL messages where expireAt <= now
            val expired = db.collectionGroup("messages")
                .whereLessThanOrEqualTo("expireAt", now)
                .get().await()

            // Batch-delete in groups of 500
            expired.documents.chunked(500).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "MessageCleanupWork"

        /**
         * Call once from Application.onCreate() or MainActivity.onCreate()
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MessageCleanupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
