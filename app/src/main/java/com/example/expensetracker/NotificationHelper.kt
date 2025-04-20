package com.example.expensetracker

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

object NotificationHelper {

    private const val CHANNEL_ID = "budget_channel"
    private const val CHANNEL_NAME = "Budget Alerts"
    private const val CHANNEL_DESCRIPTION = "Notifications for budget alerts"

    private const val APPROACHING_BUDGET_NOTIFICATION_ID = 1
    private const val BUDGET_EXCEEDED_NOTIFICATION_ID = 2

    fun createNotificationChannel(application: Application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }

            val notificationManager: NotificationManager =
                application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    fun showApproachingBudgetAlert(
        application: Application,
        totalExpenses: Double,
        budget: Double,
        dateRangeText: String
    ) {
        val formattedTotal = String.format(Locale.US, "%.2f", totalExpenses)
        val formattedBudget = String.format(Locale.US, "%.2f", budget)

        val builder = NotificationCompat.Builder(application, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Approaching Budget Alert")
            .setContentText("You’re approaching your budget! Total expenses ($$formattedTotal)$dateRangeText are close to your budget ($$formattedBudget).")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(application)) {
            notify(APPROACHING_BUDGET_NOTIFICATION_ID, builder.build())
        }
    }

    @SuppressLint("MissingPermission")
    fun showBudgetAlert(
        application: Application,
        totalExpenses: Double,
        budget: Double,
        dateRangeText: String
    ) {
        val formattedTotal = String.format(Locale.US, "%.2f", totalExpenses)
        val formattedBudget = String.format(Locale.US, "%.2f", budget)

        val builder = NotificationCompat.Builder(application, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Budget Exceeded Alert")
            .setContentText("Budget exceeded! Total expenses ($$formattedTotal)$dateRangeText exceed your budget ($$formattedBudget).")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(application)) {
            notify(BUDGET_EXCEEDED_NOTIFICATION_ID, builder.build())
        }
    }
}