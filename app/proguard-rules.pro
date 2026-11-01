# =======================================================================
# ProGuard & R8 Rules for Ride Assistant Pro
# Hardens the app against decompilation while preserving critical SDKs
# =======================================================================

# 1. Anti-Decompilation & Stripping Debug Information
-renamesourcefileattribute SourceFile
-keepattributes !SourceFile,!LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# 2. Shizuku Privileged IPC Service (DO NOT OBFUSCATE BINDER/AIDL)
-keep class dev.rikka.shizuku.** { *; }
-keep interface dev.rikka.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-dontwarn dev.rikka.shizuku.**
-dontwarn rikka.shizuku.**
-keep class com.rideauto.assistant.privileged.** { *; }

# 3. Room Database & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.rideauto.assistant.data.** { *; }
-dontwarn androidx.room.paging.**

# 4. Firebase & Firestore Data Models
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
}
-keep class com.rideauto.assistant.models.** { *; }
-keep class com.rideauto.assistant.UserData { *; }
-keep class com.rideauto.assistant.SaleRecord { *; }
-keep class com.rideauto.assistant.PlanPricingConfig { *; }
-keep class com.rideauto.assistant.RazorpayConfig { *; }
-keep class com.rideauto.assistant.RejectionRecord { *; }
-keep class com.rideauto.assistant.AdminSessionStatus { *; }

# 5. Razorpay Payment Gateway
-dontwarn com.razorpay.**
-keep class com.razorpay.** { *; }
-optimizations !method/inlining/*

# 6. Gson Serialization
-keep class com.google.gson.** { *; }
-keepclassmembers enum * { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 7. Kotlin Coroutines & Compose UI
-dontwarn kotlinx.coroutines.**
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# 8. Core Ride Scanning & Accepting Engines (DO NOT OBFUSCATE OR STRIP)
-keep class com.rideauto.assistant.TouchInputService { *; }
-keep class com.rideauto.assistant.OverlayService { *; }
-keep class com.rideauto.assistant.FareFilterManager { *; }
-keep class com.rideauto.assistant.managers.** { *; }

# 9. Background Services & Receivers
-keep class com.rideauto.assistant.BootReceiver { *; }
-keep class com.rideauto.assistant.MyFirebaseMessagingService { *; }
-keep class com.rideauto.assistant.SubscriptionNotificationService { *; }
-keep class com.rideauto.assistant.AdminPaymentNotificationService { *; }

# 10. Complete Application & Interface Preservation (Prevent R8 stripping)
-keep class com.rideauto.assistant.** { *; }
-keep interface com.rideauto.assistant.** { *; }

