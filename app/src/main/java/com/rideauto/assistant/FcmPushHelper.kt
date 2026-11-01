package com.rideauto.assistant

/**
 * Disabled FCM push helper.
 * All notification mechanisms have been completely removed as requested.
 */
object FcmPushHelper {
    const val TOPIC_ADMIN = "assistant_admin_notifications"

    fun subscribeAdminNotifications() {}

    fun subscribeUserNotifications(userEmail: String) {}

    fun syncFcmTokenToUser(userEmail: String) {}

    fun notifyAdminOfPayment(userEmail: String, planName: String, amount: String, utr: String) {}

    fun notifyUserOfActivation(userEmail: String, planName: String, addedDays: Int) {}
}
