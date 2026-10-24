package com.herbertindustries.alex311.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.herbertindustries.alex311.Alex311Application
import com.herbertindustries.alex311.MainActivity
import com.herbertindustries.alex311.R
import com.herbertindustries.alex311.data.*
import com.herbertindustries.alex311.domain.StatusChanges
import kotlinx.serialization.encodeToString
import java.util.concurrent.TimeUnit

class StatusWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as Alex311Application
        val requestToken = app.api.sessionToken ?: return Result.success()
        return try {
            val rows = app.api.get<MyLinks>("submit/api/my").links
            if (app.api.sessionToken != requestToken) return Result.success()
            val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val previous = runCatching { WireJson.decodeFromString<Map<String, String>>(prefs.getString("statusSnapshot", "{}").orEmpty()) }.getOrDefault(emptyMap())
            val changes = StatusChanges.changed(previous, rows)
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(app).areNotificationsEnabled()) {
                changes.forEach { row ->
                    val intent = Intent(app, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("https://alex311visibility.me/r/${row.service_request_id}"))
                    val pending = PendingIntent.getActivity(app, row.service_request_id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val notification = NotificationCompat.Builder(app, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification).setContentTitle("${row.service_name ?: "Request"} updated")
                        .setContentText("${row.service_request_id}: ${row.status ?: "Status changed"}")
                        .setContentIntent(pending).setAutoCancel(true).build()
                    NotificationManagerCompat.from(app).notify(row.service_request_id.hashCode(), notification)
                }
            }
            // Do not write an old account's results if sign-out happened during the request.
            if (app.api.sessionToken == requestToken) prefs.edit().putString("statusSnapshot", WireJson.encodeToString(StatusChanges.snapshot(rows))).apply()
            Result.success()
        } catch (_: ApiException) { if (app.session.signedIn.value) Result.retry() else Result.success() }
        catch (_: java.io.IOException) { Result.retry() }
    }
    companion object {
        private const val CHANNEL = "case-status"
        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Case status changes", NotificationManager.IMPORTANCE_DEFAULT))
        }
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StatusWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("status-check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
