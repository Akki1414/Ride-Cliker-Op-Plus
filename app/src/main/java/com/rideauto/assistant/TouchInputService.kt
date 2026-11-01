package com.rideauto.assistant

import android.media.AudioManager

import android.media.ToneGenerator

import android.accessibilityservice.AccessibilityService

import android.accessibilityservice.AccessibilityServiceInfo

import android.accessibilityservice.GestureDescription

import android.app.KeyguardManager

import android.content.BroadcastReceiver

import android.content.Context

import android.content.Intent

import android.content.IntentFilter

import android.content.pm.PackageManager

import android.graphics.Bitmap

import android.graphics.Path

import android.graphics.Rect

import android.os.Build

import android.os.Handler

import android.os.Looper

import android.os.PowerManager

import android.os.SystemClock

import android.view.Display

import android.speech.tts.TextToSpeech

import android.util.Log

import android.view.accessibility.AccessibilityEvent

import android.view.accessibility.AccessibilityNodeInfo

import android.view.accessibility.AccessibilityWindowInfo

import com.rideauto.assistant.data.RideHistoryDatabase

import com.rideauto.assistant.data.RideHistoryEntity

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.launch

import rikka.shizuku.Shizuku

import java.io.BufferedReader

import java.io.DataOutputStream

import java.io.InputStreamReader

import java.lang.reflect.Method

import java.util.Locale

import java.util.concurrent.Executors

import java.util.regex.Pattern

/**

 * Enterprise Auto-Accept Service for RideAccepterPro.

 * Incorporates battle-tested node detection and post-acceptance verification from My Ride Accepter and Ride Accepter-v6,

 * with direct Pari privileged shell execution and dual-distance filtering.

 */

class TouchInputService : AccessibilityService() {

    companion object {

        private const val TAG = "TouchInputService"

        var isRunning = false

            private set

        val isServiceRunning: Boolean

            get() = isRunning

        private val KNOWN_LAUNCHER_PACKAGES = setOf(

            "com.android.systemui", "com.google.android.apps.nexuslauncher", "com.android.launcher3",

            "com.android.launcher", "com.sec.android.app.launcher", "com.miui.home",

            "com.mi.android.globallauncher", "com.oppo.launcher", "com.coloros.launcher",

            "com.bbk.launcher2", "com.vivo.launcher", "com.iqoo.launcher", "com.oneplus.launcher",

            "com.huawei.android.launcher", "com.transsion.hilauncher", "com.transsion.xoslauncher",

            "com.transsion.itel.launcher", "com.motorola.launcher3", "com.zui.launcher",

            "com.nothing.launcher", "com.asus.launcher", "com.teslacoilsw.launcher",

            "com.actionlauncher.playstore", "com.microsoft.launcher", "com.lge.launcher3",

            "com.sonymobile.home", "com.realme.launcher"

        )

        private val ACCEPT_TEXTS = arrayOf(

            "accept", "confirm", "match", "take", "take ride", "take order",

            "reserve", "advance", "advance booking", "accept advance", "scheduled", "schedule",

            "accept scheduled", "confirm reservation", "match reserve",

            "एक्सेप्ट", "कन्फर्म", "स्वीकार", "स्वीकारें", "स्वीकारा", "मान्य करा", "स्वीकार करा", "रिजर्व", "शेड्यूल",

            "tap to accept", "delivery", "accept order", "accept delivery", "auto accept", "tap to match"

        )

        private val OLA_COUNTDOWN_REGEX = Regex("""(?i)^accept\s+in\s+\d+.*$""")

        private val OLA_REJECT_TOKENS = setOf(

            "decline", "reject", "cancel", "pass", "skip", "close",

            "cancel booking", "cancel ride", "cancel order", "cancel trip",

            "confirm arrival", "confirm otp", "confirm drop", "confirm toll", "confirm location",

            "collect cash", "arrived", "reached", "start trip", "start ride", "end trip",

            "call customer", "message customer", "contact customer", "waiting", "wait for", "client located", "navigate",

            "go off duty", "go to", "go offline",

            "re-center", "recenter", "re_center", "re-centre", "recentre", "re_centre", "re center", "re centre", "री-सेंटर", "री सेंटर", "रिसेंटर", "फिर से केंद्रित",

            "अस्वीकार", "रद्द", "छोड़ें"

        )

        private val OLA_ACCEPT_IDS = listOf(

            "btn_accept", "button_accept", "driver_action_accept", "accept_btn", "accept_button",

            "accept_booking_btn", "btn_accept_booking", "acceptButton", "accept_ride_button",

            "acceptRideButton", "booking_accept_btn", "action_accept", "accept_ride",

            "btn_accept_advance", "accept_advance_btn", "btn_advance_accept", "advance_btn",

            "btn_advance", "btn_scheduled_accept", "accept_schedule_btn", "booking_accept_button",

            "ola_accept_button", "btn_ride_accept", "ride_accept_btn", "accept_container",

            "accept_layout", "motivator_overlay_container", "btn_confirm", "btn_take_ride", "take_ride_btn"

        )

        private val OLA_EXACT_BUTTON_TEXTS = listOf(

            "accept", "accept ride", "accept booking", "accept order", "accept offer", "ride accept",

            "confirm", "book now", "tap to accept", "accept advance", "advance booking", "accept scheduled",

            "accept in", "accept in 15s", "accept in 14s", "accept in 13s", "accept in 12s", "accept in 11s",

            "accept in 10s", "accept in 9s", "accept in 8s", "accept in 7s", "accept in 6s", "accept in 5s",

            "accept in 4s", "accept in 3s", "accept in 2s", "accept in 1s",

            "accept in 15", "accept in 14", "accept in 13", "accept in 12", "accept in 11",

            "accept in 10", "accept in 9", "accept in 8", "accept in 7", "accept in 6", "accept in 5",

            "accept in 4", "accept in 3", "accept in 2", "accept in 1",

            "स्वीकार", "स्वीकार करें", "एक्सेप्ट", "कन्फर्म", "राइड स्वीकारें"

        )

        private val RAPIDO_ACCEPT_IDS = listOf(

            "btn_accept", "accept_btn", "accept_button", "button_accept", "accept",

            "accept_order", "action_accept", "btn_accept_order", "btn_order_accept",

            "order_accept_btn", "btn_confirm", "btn_accept_delivery", "item_accept",

            "order_item_accept", "btn_accept_advance", "advance_accept_btn",

            "btn_advance_accept", "btn_accept_scheduled", "scheduled_accept_btn",

            "btn_schedule_accept", "advance_booking_accept", "btn_take_ride",

            "tv_accept", "tv_accept_order", "tv_accept_advance", "btn_advance", "btn_accept_next_ride",

            "btn_pick", "pick_btn", "pick_order", "btn_pick_order", "btn_order_match",

            "btn_order_pick", "order_pick_btn"

        )

        private val RAPIDO_ACCEPT_TEXTS = listOf(

            "accept", "accept order", "accept ride", "accept delivery",

            "confirm order", "tap to accept", "accept advance",

            "accept scheduled", "confirm advance", "take ride",

            "take order", "accept next ride", "pick order",

            "order match", "swipe to accept", "slide to accept",

            "स्वीकार", "स्वीकार करें", "स्वीकारा", "मान्य करा", "स्वीकार करा",

            "एक्सेप्ट", "कन्फर्म", "राइड स्वीकारें",

            // Regional language variants (South & East India)

            "ಸ್ವೀಕರಿಸಿ", "ಖಚಿತಪಡಿಸಿ", // Kannada

            "అంగీకరించు", "రైడ్ స్వీకరించండి", // Telugu

            "ஏற்றுக்கொள்", "ஏற்கவும்", // Tamil

            "গ্রহণ করুন", // Bengali

            "સ્વીકારો" // Gujarati

        )

        // Essential C++ substring containment roots for Rapido fallback search

        private val RAPIDO_FAST_TEXT_KEYS = arrayOf(

            "accept", "confirm", "take ride", "take order", "match",

            "स्वीकार", "स्वीकार करें", "एक्सेप्ट", "कन्फर्म",

            "ಸ್ವೀಕರಿಸಿ", "అంగీకరించు", "ஏற்றுக்கொள்", "গ্রহণ"

        )

        private val UBER_ACCEPT_IDS = listOf(

            "ub__trip_match_button", "trip_match_button", "btn_match", "match_btn",

            "btn_accept", "accept_btn", "accept_button", "ub__accept_button",

            "ub__action_button", "ub__offer_accept",

            "dispatch_accept_tap", "job_board_offer_accept_tap",

            "6e4e9f88-fb9c", "6e4e9f88",

            "9ab7a07a-5e61", "10f0a3a5-05e9", "9ab7a07a", "10f0a3a5",

            "upfront_offer_configurable_details_accept_button",

            "upfront_offer_configurable_details_auditable_accept_button",

            "uda_details_accept_button", "upfront_offer_view_holder_button",

            "ub_btn_accept_offer", "ub__preview_accept_button",

            "rider_offer_accept_button", "ub__basic_preview_accept",

            "ub__self_driving_match_accept_button", "ub__request_button",

            "ub__reserve_accept_button", "reserve_accept_button", "btn_reserve",

            "ub__scheduled_accept_button", "ub__trip_radar_accept", "trip_radar_button",

            "ub__trip_radar_match_button", "ub__trip_radar_offer_button",

            "btn_accept_reserve", "accept_reserve_btn", "ub__reserve_button"

        )

        private val UBER_REJECT_TAGS = setOf("32e5d85e-7c76", "32e5d85e", "dispatch_reject_tap")

        private val UBER_ACCEPT_TEXTS = listOf(

            "match", "tap to match", "tap to accept",

            "accept", "confirm match", "confirm", "accept trip",

            "accept offer", "trip radar", "reserve", "accept reserve",

            "accept reservation", "confirm reservation", "match reserve",

            "accept scheduled", "confirm reserve", "reserve trip",

            "मैच", "मैच करें", "स्वीकार", "रिजर्व"

        )

        private val NAMMA_ACCEPT_IDS = listOf(

            "btn_accept", "accept_btn", "accept_button", "btn_confirm",

            "accept_ride", "offer_btn", "action_accept", "btn_accept_ride", "accept_ride_btn",

            "accept", "accept_order", "btn_accept_order", "btn_accept_advance",

            "advance_accept_btn", "btn_scheduled_accept"

        )

        private val NAMMA_ACCEPT_TEXTS = listOf(

            "accept", "accept ride", "accept offer", "confirm", "accept advance",

            "advance booking", "schedule", "reserve",

            "एक्सेप्ट", "स्वीकार", "स्वीकार करें", "ಸ್ವೀಕರಿಸಿ", "ಖಚಿತಪಡಿಸಿ", "ஏற்கவும்", "அಂಗೀಕರ"

        )

        private val NAMMA_REJECT_TOKENS = setOf("dismiss", "metro", "close", "cancel")

        private val NAMMA_PAYMENT_BANNER_REGEX = Regex(

            """\b(?:pay\s+now|pay\s+dues?|pending\s+payment|clear\s+dues|clear\s+your\s+dues|subscription\s+dues)\b""",

            RegexOption.IGNORE_CASE

        )

        // ── Porter (DutyAccepter-v27) ──

        private val PORTER_ACCEPT_IDS = listOf(

            "btn_accept", "accept_btn", "accept_button", "btn_confirm", "order_accept",

            "btn_slide_accept", "slide_to_accept", "accept_order_btn", "tv_accept"

        )

        private val PORTER_ACCEPT_TEXTS = listOf(

            "accept", "accept order", "accept pickup", "tap to accept", "confirm",

            "slide to accept", "swipe to accept", "take order"

        )

        private val PORTER_SLIDE_REGEXES = listOf(

            Regex("""(?:slide|swipe|drag)\b.{0,50}\baccept""", RegexOption.IGNORE_CASE),

            Regex("""\baccept\b.{0,50}(?:slide|swipe|drag)""", RegexOption.IGNORE_CASE)

        )

        private val PORTER_ACCEPT_ID_SET = PORTER_ACCEPT_IDS.toHashSet()

        private val PORTER_ACCEPT_TEXT_SET = PORTER_ACCEPT_TEXTS.map { it.lowercase(Locale.ROOT) }.toHashSet()

        // ── Red Taxi (DHP ep.java mode 2 & dw2.java) ──

        private val RED_TAXI_ACCEPT_IDS = listOf(

            "btn_accept", "accept_btn", "accept_button", "btn_confirm", "accept_ride", "button_accept"

        )

        private val RED_TAXI_ACCEPT_TEXTS = listOf(

            "accept", "confirm", "match", "ஏற்கவும்", "ஏற்றுக்கொள்", "ஏற்றுக்கொள்ளவும்", "உறுதிப்படுத்து", "பொருந்து"

        )

        private val RED_TAXI_REJECT_TOKENS = setOf(

            "local", "உள்ளூர் பயணம்", "cash", "பணம்", "red taxi", "redtaxi",

            "services", "கோடு", "close", "dismiss", "cancel", "skip", "தவிர்க்கவும்"

        )

        private val RED_TAXI_ORDERS_REGEX = Regex("""^\d+\s*(?:orders?|ஆர்டர்கள்?)""", RegexOption.IGNORE_CASE)

        // ── Bharat Taxi (DHP ep.java mode 0 & dp.java) ──

        private val BHARAT_TAXI_ACCEPT_IDS = listOf(

            "btn_accept", "accept_btn", "accept_button", "btn_confirm", "accept_ride"

        )

        private val BHARAT_TAXI_ACCEPT_TEXTS = listOf(

            "accept", "confirm", "match", "स्वीकार", "स्वीकार करें", "एक्सेप्ट"

        )

        private val BHARAT_TAXI_VEHICLE_REGEX = Regex("""^(?:cab|auto|bike|taxi)(?:\s+[a-z]+)?$""", RegexOption.IGNORE_CASE)

        private val GENERAL_ACCEPT_IDS = listOf(

            "btn_accept", "accept_btn", "accept_button", "button_accept", "accept",

            "accept_order", "action_accept", "btn_accept_order", "accept_card_view",

            "btn_order_accept", "accept_layout", "order_accept_btn", "ll_accept",

            "rl_accept", "accept_container", "btn_accept_delivery", "btn_confirm",

            "btn_accept_advance", "advance_accept_btn", "btn_scheduled_accept"

        )

        // Pre-indexed in-memory HashSets for instant O(1) matching with zero Binder IPC calls

        private val OLA_ACCEPT_ID_SET = OLA_ACCEPT_IDS.toHashSet()

        private val RAPIDO_ACCEPT_ID_SET = RAPIDO_ACCEPT_IDS.toHashSet()

        private val UBER_ACCEPT_ID_SET = UBER_ACCEPT_IDS.toHashSet()

        private val NAMMA_ACCEPT_ID_SET = NAMMA_ACCEPT_IDS.toHashSet()

        private val RED_TAXI_ACCEPT_ID_SET = RED_TAXI_ACCEPT_IDS.toHashSet()

        private val BHARAT_TAXI_ACCEPT_ID_SET = BHARAT_TAXI_ACCEPT_IDS.toHashSet()

        private val GENERAL_ACCEPT_ID_SET = GENERAL_ACCEPT_IDS.toHashSet()

        private val OLA_EXACT_BUTTON_TEXT_SET = OLA_EXACT_BUTTON_TEXTS.map { it.lowercase(Locale.ROOT) }.toHashSet()

        private val RAPIDO_ACCEPT_TEXT_SET = RAPIDO_ACCEPT_TEXTS.map { it.lowercase(Locale.ROOT) }.toHashSet()

        private val UBER_ACCEPT_TEXT_SET = UBER_ACCEPT_TEXTS.map { it.lowercase(Locale.ROOT) }.toHashSet()

        private val NAMMA_ACCEPT_TEXT_SET = NAMMA_ACCEPT_TEXTS.map { it.lowercase(Locale.ROOT) }.toHashSet()

        private val RED_TAXI_ACCEPT_TEXT_SET = RED_TAXI_ACCEPT_TEXTS.map { it.lowercase(Locale.ROOT) }.toHashSet()

        private val BHARAT_TAXI_ACCEPT_TEXT_SET = BHARAT_TAXI_ACCEPT_TEXTS.map { it.lowercase(Locale.ROOT) }.toHashSet()

        private val ACCEPT_TEXT_SET = ACCEPT_TEXTS.map { it.lowercase(Locale.ROOT) }.toHashSet()

        private val CATEGORY_ICON_REGEX = Regex("""(?i)^(?:category\s+icon|icon)\s*,\s*""")

        private val DIST_ETA_PREFIX_REGEX = Regex("""(?i)^\d+(?:\.\d+)?\s*(?:km|kms|k\.m|m|meters)\s*[•·,]\s*\d+\s*(?:mins?|min|minutes?)\s*,\s*""")

        private val DIST_ONLY_PREFIX_REGEX = Regex("""(?i)^\d+(?:\.\d+)?\s*(?:km|kms|k\.m|m|meters)\s*,\s*""")

        private val ETA_ONLY_PREFIX_REGEX = Regex("""(?i)^\d+\s*(?:mins?|min|minutes?)\s*,\s*""")

        private val DIGIT_TOKEN_REGEX = Regex("""^\d{2,4}$""")

        private val NUMBER_OR_DECIMAL_REGEX = Regex("""^\d+(\.\d+)?$""")

        private val DIST_SPEED_UNIT_REGEX = Regex("""(?i)^(\d+(\.\d+)?\s*(km|kms|k\.m|kilometer|m|meters|km/h|/km|per km|/hr|per hr)(\s*away)?)+$""")

        private val RATE_PER_KM_LINE_REGEX = Regex("""(?i)^.*?₹?\s*\d+(\.\d+)?\s*(?:/|per)\s*(?:km|kms|hr|min).*$""")

        private val DURATION_ETA_TOKEN_REGEX = Regex("""(?i)^\d+\s*(min|mins|minute|minutes|sec|secs|second|seconds|s)\b(?:\s*away)?$""")

        private val TIME_FORMAT_REGEX = Regex("""^\d+:\d+\s*(am|pm)?$""")

        private val FARE_PATTERN: Pattern = Pattern.compile(

            """(?:(?:₹|rs\.?|inr|\u20b9)\s*(\d+(?:\.\d+)?))|(?:(\d+(?:\.\d+)?)\s*(?:₹|rs\.?|inr|\u20b9))""",

            Pattern.CASE_INSENSITIVE

        )

        private val FARE_BONUS_PATTERN: Pattern = Pattern.compile(

            """(?:\u20b9|rs\.?|inr|₹)?\s*(\d+(?:\.\d+)?)\s*\+\s*(?:\u20b9|rs\.?|inr|₹)?\s*(\d+(?:\.\d+)?)""",

            Pattern.CASE_INSENSITIVE

        )

        private val DIST_PATTERN: Pattern = Pattern.compile(

            """([0-9]*\.?[0-9]+)\s*(km|k\.m|kms|kilometer|m|meters|kms?|கி\.?மீ\.?)\b""",

            Pattern.CASE_INSENSITIVE

        )

        private val UBER_TIME_DIST_REGEX = Pattern.compile("""([0-9]+)\s*mins?\s*\(([0-9]+(?:\.[0-9]+)?)\s*km\)""", Pattern.CASE_INSENSITIVE)

        private val SPEEDOMETER_REGEX = Regex("""(?i)\b\d+\s*(?:km/h|kmph|km/hr|kph|mph)\b""")

        private val RATE_PER_KM_REGEX = Regex("""(?i)(?:\|)?\s*(?:₹|rs\.?|inr|\u20b9)?\s*\d+(?:\.\d+)?\s*(?:/|per)\s*(?:km|kms|k\.m|kilometer|hr|min|hour|minute|kms?|கி\.?மீ\.?)\b""")

        private val CUSTOMER_EXTRA_REGEX = Regex("""(?i)customer added.*?₹?\s*\d+(?:\.\d+)?.*?extra""")

        private val FARE_DIGIT_PATTERN = Pattern.compile("(\\d+)")

        private val DIST_KM_PATTERN = Pattern.compile("""(\d+(?:\.\d+)?)\s*(?:km|KM|Km|kms|k\.m|kilometer|kms?|கி\.?மீ\.?)\b""", Pattern.CASE_INSENSITIVE)

        private val DIST_METER_PATTERN = Pattern.compile("""(\d+)\s*(?:m|meters)\b""", Pattern.CASE_INSENSITIVE)

        // ── Platform-Specific Active Trip & Advance Confirmation Verification Keys ──

        private val OLA_SUCCESS_KEYS = arrayOf(

            "reach pickup", "go to pickup", "client located", "start otp", "enter otp",

            "arrived at pickup", "drop customer", "collect cash", "track route", "start trip",

            "booking confirmed", "advance booking accepted", "advance booking confirmed",

            "scheduled ride confirmed", "upcoming bookings", "advance bookings", "ride queued",

            "queued booking", "next trip added", "booking added", "advance confirmed",

            "scheduled confirmed", "assigned", "ride assigned", "navigate", "navigate to pickup",

            "call customer", "pickup customer", "contact customer", "बुकिंग कन्फर्म", "स्वीकार किया गया",

            "crn", "crn:", "otp", "pin", "pickup", "pick up", "drop", "drop off",

            "call rider", "customer", "arrived", "reached", "directions"

        )

        private val RAPIDO_SUCCESS_KEYS = arrayOf(

            "go to pickup", "arrived at pickup", "reach pickup", "send message to customer",

            "start ride", "slide to arrive", "swipe to arrive", "reached pickup location", "calling customer",

            "booking confirmed", "advance booking accepted", "advance booking confirmed", "scheduled ride confirmed",

            "upcoming bookings", "advance bookings", "ride queued", "queued booking", "next trip added", "booking added",

            "order confirmed", "advance confirmed", "scheduled confirmed", "assigned", "बुकिंग कन्फर्म", "स्वीकार किया गया",

            "otp", "enter otp", "navigate", "call", "arrived", "message customer", "customer verified location",

            "order #", "order id", "order no", "pickup", "pick up", "drop", "directions"

        )

        private val UBER_SUCCESS_KEYS = arrayOf(

            "picking up", "head to", "navigate to pickup", "arrived at pickup", "start uber", "confirm dropoff",

            "reservation confirmed", "reserve confirmed", "trip confirmed", "scheduled trip confirmed",

            "upcoming trips", "scheduled trips", "upcoming", "trip queued", "next trip added", "queued",

            "assigned", "reservation assigned", "scheduled offer confirmed",

            // 🏆 Lapak 100% Identical Verification Tokens:

            "matched to trip", "start navigation", "navigating to", "you matched", "you're matched", "you are matched"

        )

        private val UBER_PENDING_KEYS = arrayOf(

            "matching in progress", "request matching", "finding best route"

        )

        private val UBER_IDLE_KEYS = arrayOf(

            "finding trips", "you're online", "you are online", "you’re online"

        )

        private val UBER_STRICT_ACCEPT_REGEX = Regex("""(?i)^\s*(match|accept|confirm|exclusive|मैच|स्वीकार|कन्फर्म)\s*$""")

        private val NAMMA_SUCCESS_KEYS = arrayOf(

            "head to pickup", "start trip", "reached pickup", "arrived at pickup", "go to pickup",

            "booking confirmed", "advance booking accepted", "trip confirmed", "upcoming bookings",

            "advance bookings", "scheduled confirmed", "assigned", "बुकिंग कन्फर्म"

        )

        private val BHARAT_SUCCESS_KEYS = arrayOf(

            "go to pickup", "start ride", "arrived at pickup", "reached pickup",

            "booking confirmed", "advance booking accepted", "trip confirmed", "upcoming"

        )

        private val JUGNOO_SUCCESS_KEYS = arrayOf(

            "ride started", "go to pickup", "arrived at pickup", "booking confirmed", "advance booking"

        )

        private val GENERAL_SUCCESS_KEYS = arrayOf(

            "go to pickup", "reach pickup", "arrived at pickup", "start ride", "start trip", "slide to arrive",

            "booking confirmed", "advance booking accepted", "scheduled confirmed", "trip confirmed",

            "upcoming bookings", "advance confirmed", "order confirmed", "assigned"

        )

        // ── Failure & Rejection Keys (Tokens indicating the offer failed/expired/taken) ──

        private val FAILURE_KEYS = arrayOf(

            "order expired", "offer expired", "booking expired", "ride expired", "trip expired",

            "another driver accepted", "another captain accepted", "another driver matched",

            "matched to another", "another driver matched to this request", "matched to another driver",

            "trip no longer available", "order no longer available", "missed offer", "missed trip",

            "missed ride", "ride missed", "order taken", "better luck next time",

            "ride no longer available", "trip cancelled", "order cancelled", "someone else",

            "already assigned", "taken by another", "order missed", "trip missed",

            "ride passed", "request expired"

        )

        // ── History Screen Exclusion Tokens (Avoid false cancellation triggers when opening history) ──

        private val HISTORY_SCREEN_TOKENS = arrayOf(

            "ride history", "trip history", "order history", "past trips", "past orders",

            "completed trips", "completed orders", "earnings history", "all trips", "all orders", "today's earnings"

        )

        // ── Active Ride Customer Cancellation Keys ──

        private val CANCELLATION_KEYS = arrayOf(

            "ride cancelled by customer", "customer cancelled the ride", "customer cancelled this order",

            "order cancelled by customer", "rider cancelled", "trip cancelled by customer", "the rider cancelled",

            "booking cancelled by customer", "captain, the customer cancelled", "customer has cancelled",

            "rider cancelled the trip", "customer cancelled the trip"

        )

        // ── UI Token Blacklist for Clean Address Extraction ──

        private val NON_ADDRESS_TOKENS = setOf(

            "pickup", "drop", "dropoff", "pick", "pickupaddress", "ride", "price", "sorting",

            "category", "ola", "rapido", "uber", "jugnoo", "location", "cash", "card", "payment",

            "auto", "mini", "prime", "sedan", "suv", "bike", "mins", "min", "minute", "sec", "seconds",

            "rental", "outstation", "pool", "share", "now", "later", "book", "schedule", "coupon",

            "offer", "discount", "pass", "safety", "otp", "verify", "wallet", "money", "history",

            "earning", "earnings", "wait", "info", "detail", "view", "more", "back", "close",

            "cancel", "decline", "reject", "captain", "something went wrong", "try again", "accept",

            "confirm", "match", "high to low", "low to high", "old to new", "new to old",

            "price: high to low", "pickup distance: low to high", "trip distance: high to low",

            "trip distance: low to high", "rate per km: low to high", "recommended for you",

            "1 ride", "see 1 more orders", "customer added", "ola_logo", "accepting booking",

            "please wait", "premium", "surge", "tax", "toll", "tip", "view all", "subscription",

            "plan", "buy", "renew", "uber driver", "play store", "locating...", "namma yatri",

            "nammayatri", "bharat taxi", "start ride", "map", "arrived", "go to pickup", "online",

            "upi", "pay", "paid", "today", "today's earnings", "wallet balance", "rating",

            "completed", "orders", "eligible bookings", "price:", "per km", "/km", "rs.", "inr",

            "inc.", "add-on", "add on", "paused: high cancellation", "high cancellation rate",

            "safety of you", "see 1 more", "more orders", "cancel often", "get fewer rides",

            "how can i lower my cancellation rate", "cancellation rate matter", "auto priority",

            "ola auto", "ola mini", "ola prime", "auto accept", "wait for", "wait for passenger",

            "waiting for", "waiting for passenger", "enter otp", "start otp", "client located",

            "customer", "passenger", "driver", "partner", "collect cash", "confirm arrival", "confirm otp",

            "google map", "google maps", "navigation", "navigate", "reach pickup", "slide to arrive",

            "swipe to arrive", "slide to start", "swipe to start", "calling customer", "call customer",

            "send message to customer", "message customer", "contact customer", "arrived at pickup", "reached pickup"

        )

    }

    private lateinit var prefs: PreferencesHelper

    private lateinit var fareFilter: FareFilterManager

    private lateinit var shizukuManager: ShizukuManager

    private val handler = Handler(Looper.getMainLooper())

    private var scanThread: android.os.HandlerThread? = null

    private var scanHandler: Handler? = null

    private var tts: TextToSpeech? = null

    private var wakeLock: PowerManager.WakeLock? = null

    private var isProcessing = false

    private var lastEventTime: Long = 0

    private var globalCooldownUntil: Long = 0

    private val packageBusyUntilMs = mutableMapOf<String, Long>()

    private var shizukuShell: Process? = null

    private var shizukuOut: DataOutputStream? = null

    private val shellLock = Any()

    private val tapExecutor = Executors.newSingleThreadExecutor()

    private val reusableRect = Rect()

    private var lastShellCheckTime: Long = 0L

    private var toneGenerator: ToneGenerator? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {

        Log.i(TAG, "⚡ Shizuku binder received, reconnecting shell...")

        Thread { ensurePariShell() }.start()

    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {

        Log.w(TAG, "⚠️ Shizuku binder died, resetting shell...")

        resetPariShell()

    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val db by lazy { RideHistoryDatabase.getDatabase(applicationContext) }

    private var lastSpokenText: String = ""

    private var lastSpokenTime: Long = 0

    // In-memory cached filter primitives for 0.0ms instant evaluation

    private var cachedMinFare: Int = 0

    private var cachedMaxFare: Int = Int.MAX_VALUE

    private var cachedMinDist: Float = 0f

    private var cachedMaxDist: Float = Float.MAX_VALUE

    private var cachedIsFareActive: Boolean = false

    private var cachedIsDistActive: Boolean = false

    private var cachedNonFavZones: List<String> = emptyList()

    private var cachedFavZones: List<String> = emptyList()

    private val filterUpdateReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            reloadCachedFilters()

            Log.d(TAG, "🔄 Filters reloaded via broadcast: FareActive=$cachedIsFareActive [$cachedMinFare..$cachedMaxFare], DistActive=$cachedIsDistActive [Max=${cachedMaxDist}km]")

        }

    }

    // Per-order debounce for Uber Driver (prevents dropping rapid consecutive Trip Radar offers)

    private var lastUberOrderSignature: String = ""

    private var lastUberOrderTime: Long = 0L

    private val packageLastContentTime = mutableMapOf<String, Long>()

    private var lastLauncherOverlayScanTime: Long = 0L

    // ── Ola Pre-Arming State Machine (<0.0ms Instant Strike upon Countdown Expiry) ──

    private var olaPreArmed: Boolean = false

    private var olaPreArmTime: Long = 0L

    private var cachedOlaPrice: Int = -1

    private var cachedOlaPickupKm: Float = -1f

    private var cachedOlaDropKm: Float = -1f

    private var cachedOlaPickupLoc: String? = null

    private var cachedOlaDropLoc: String? = null

    private val cachedOlaTexts = mutableListOf<String>()

    private var cachedOlaX: Int = -1

    private var cachedOlaY: Int = -1

    private var cachedOlaCountdownDurationMs: Long = 1000L

    private var lastOlaClickTime: Long = 0L

    private val OLA_CLICK_DEBOUNCE = 2000L

    // ── In-Memory Pending Ride Acceptance Tracker ──

    private var pendingPlatform: String? = null

    private var pendingPrice: Int = 0

    private var pendingPickupKm: Float = 0f

    private var pendingDropKm: Float = 0f

    private var pendingRawTexts: List<String> = emptyList()

    private var pendingPickupLoc: String? = null

    private var pendingDropLoc: String? = null

    private var pendingClickTime: Long = 0L

    private var pendingMethod: String = "accessibility"

    private var pendingIsAdvance: Boolean = false

    private val pendingLock = Any()

    // ── In-Memory Active Trip Tracker for Real-time Cancellation ──

    private var activeRideId: Long? = null

    private var activePlatform: String? = null

    private var activeRideTime: Long = 0L

    private var lastCancellationCheckTime: Long = 0L

    private var lastFullScanTime: Long = 0L

    data class RideDetails(

        val fare: Int? = null,

        val baseFare: Int? = null,

        val surgeBonus: Int? = null,

        val distance: Float? = null, // pickup distance in km

        val tripDistance: Float? = null, // drop/trip distance in km

        val pickupLocation: String? = null,

        val dropLocation: String? = null,

        val fullText: String = "",

        val rawTexts: List<String> = emptyList(),

        val isAdvanceRide: Boolean = false

    )

    // ── Lifecycle ──

    override fun onCreate() {

        super.onCreate()

        prefs = PreferencesHelper(this)

        fareFilter = FareFilterManager(this)

        shizukuManager = ShizukuManager(this)

        reloadCachedFilters()

        initTextToSpeech()

        try {

            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

        } catch (e: Exception) {}

        try {

            Shizuku.addBinderReceivedListener(binderReceivedListener)

            Shizuku.addBinderDeadListener(binderDeadListener)

        } catch (e: Exception) {}

        val thread = android.os.HandlerThread("ScanThread")

        thread.start()

        scanThread = thread

        scanHandler = Handler(thread.looper)

        try {

            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager

            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "App::InputWakeLock")

        } catch (e: Exception) {

            Log.w(TAG, "Failed to initialize wake lock", e)

        }

        Thread { ensurePariShell() }.start()

        // Register dynamic filter update receiver

        try {

            val filter = IntentFilter(OverlayService.ACTION_FILTER_UPDATED)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                registerReceiver(filterUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

            } else {

                registerReceiver(filterUpdateReceiver, filter)

            }

        } catch (e: Exception) {

            Log.w(TAG, "Failed to register filterUpdateReceiver", e)

        }

        // Auto-purge any legacy dirty systemui and corrupted records from database

        serviceScope.launch {

            try {

                db.rideHistoryDao().deleteDirtySystemUiEntries()

                db.rideHistoryDao().deleteCorruptedHistoryEntries()

            } catch (e: Exception) {

                Log.w(TAG, "Failed to clean dirty/corrupted entries", e)

            }

        }

        Log.d(TAG, "TouchInputService created")

    }

    private fun reloadCachedFilters() {

        try {

            if (!::fareFilter.isInitialized || !::prefs.isInitialized) return

            cachedIsFareActive = fareFilter.isFilterEnabled

            cachedMinFare = fareFilter.minFare

            cachedMaxFare = if (fareFilter.maxFare > 0) fareFilter.maxFare else Int.MAX_VALUE

            cachedIsDistActive = fareFilter.isDistanceFilterEnabled

            cachedMinDist = fareFilter.minDistance

            cachedMaxDist = if (fareFilter.maxDistance > 0f) fareFilter.maxDistance else Float.MAX_VALUE

            cachedNonFavZones = prefs.getNonFavZonesList().map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }

            cachedFavZones = prefs.getFavZonesList().map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }

        } catch (e: Exception) {

            // Safe fallback

        }

    }

    private fun isSelfPackage(pkg: String): Boolean {

        if (pkg.isEmpty()) return false

        val lower = pkg.lowercase(Locale.ROOT)

        return lower == packageName.lowercase(Locale.ROOT) ||

                lower == "com.rideauto.accepter" ||

                lower == "com.rideauto.accepter.admin" ||

                lower == "com.game.fishcatcher" ||

                lower == "com.ride.accepter.direct" ||

                lower == "com.my.ride.accepter" ||

                lower == "com.my.ride.accepter.direct" ||

                lower == "com.rideauto.assistant" ||

                lower == "com.rideauto.assistant.admin" ||

                lower == "com.rideclicker.op" ||

                lower == "com.rideclicker.op.admin" ||

                lower == "com.rideclicker.proplus" ||

                lower == "com.rideclicker.proplus.admin" ||

                lower == "com.devtools.screenguard"

    }

    private fun isLoginOrAuthScreen(rootNode: AccessibilityNodeInfo?): Boolean {

        if (rootNode == null) return false

        val texts = mutableListOf<String>()

        collectLeafTexts(rootNode, texts)

        if (texts.isEmpty()) return false

        val combined = texts.joinToString(" ").lowercase(Locale.ROOT)

        return combined.contains("enter mobile number") ||

                combined.contains("enter phone number") ||

                combined.contains("enter registered mobile") ||

                combined.contains("verify otp") ||

                combined.contains("enter otp") ||

                combined.contains("login with otp") ||

                combined.contains("register as captain") ||

                combined.contains("welcome to rapido") ||

                combined.contains("welcome to captain") ||

                combined.contains("sign in") ||

                combined.contains("log in") ||

                combined.contains("get started") ||

                combined.contains("terms of service") ||

                combined.contains("privacy policy") ||

                combined.contains("enter password") ||

                combined.contains("login to your account") ||

                combined.contains("create an account") ||

                combined.contains("new captain registration") ||

                combined.contains("onboarding")

    }

    private fun isRideHailingPackage(pkg: String): Boolean {

        if (pkg.isEmpty() || isIgnoredPackage(pkg)) return false

        val lower = pkg.lowercase(Locale.ROOT)

        if (lower.contains("ola") || lower.contains("rapido") || lower.contains("captain") ||

            lower.contains("uber") || lower.contains("porter") || lower.contains("namma") || lower.contains("yatri") ||

            lower.contains("juspay") ||

            lower.contains("swiggy") || lower.contains("zomato") || lower.contains("rr.android") ||

            lower.contains("mobility") || lower.contains("jugnoo") || lower.contains("borzo") ||

            lower.contains("shadowfax") || lower.contains("zepto") || lower.contains("blinkit") ||

            lower.contains("loadshare")

        ) {

            return true

        }

        return SupportedApps.allPackageNames.any {

            (lower.contains(it.lowercase(Locale.ROOT)) || it.lowercase(Locale.ROOT).contains(lower)) && !it.contains("android")

        }

    }

    private fun isSystemOrLauncherPackage(pkg: String): Boolean {

        if (pkg.isEmpty()) return false

        val lower = pkg.lowercase(Locale.ROOT)

        if (lower == "android" || lower.contains("systemui") || lower.contains("launcher") ||

            lower.contains("quickstep") || lower.contains("recents") || lower.contains("taskswitcher") ||

            lower.startsWith("com.android.internal") || lower == "com.android.settings") {

            return true

        }

        return lower in KNOWN_LAUNCHER_PACKAGES

    }

    private fun isRecentsOrLauncherOverview(node: AccessibilityNodeInfo?): Boolean {

        if (node == null) return false

        val pkg = node.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

        if (!isSystemOrLauncherPackage(pkg)) return false

        // Check view ID resource names for recents/overview/task switcher indicators

        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

        if (viewId.contains("recents") || viewId.contains("overview") ||

            viewId.contains("task_view") || viewId.contains("snapshot") ||

            viewId.contains("clear_all") || viewId.contains("recent_apps") ||

            viewId.contains("task_panel") || viewId.contains("recent_task")) {

            return true

        }

        for (i in 0 until node.childCount.coerceAtMost(10)) {

            val child = node.getChild(i) ?: continue

            val cId = child.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

            if (cId.contains("recents") || cId.contains("overview") ||

                cId.contains("task_view") || cId.contains("snapshot") ||

                cId.contains("clear_all") || cId.contains("recent_apps") ||

                cId.contains("task_panel") || cId.contains("recent_task") ||

                cId.contains("workspace") || cId.contains("hotseat") || cId.contains("nav_bar")) {

                return true

            }

        }

        return false

    }

    private fun isIgnoredPackage(pkg: String): Boolean {

        if (pkg.isEmpty()) return true

        if (isSelfPackage(pkg)) return true

        val lower = pkg.lowercase(Locale.ROOT)

        if (lower == "android" || lower.contains("systemui") || lower.startsWith("com.android.internal") ||

            lower == "com.android.settings" || lower.startsWith("com.android.settings") ||

            lower == "com.google.android.settings" || lower.startsWith("com.google.android.settings") ||

            lower.contains("permissioncontroller") || lower.contains("packageinstaller") ||

            lower.contains("securitycenter") || lower.contains("safecenter") ||

            lower.contains("permissionmanager") || lower.contains("phonemaster") ||

            lower.contains("systemmanager") || lower.contains("inputmethod") ||

            lower.contains("googlequicksearchbox") || lower == "com.android.vending" ||

            lower == "com.google.android.gms" || lower == "com.google.android.gsf" ||

            lower.contains("maps") || lower.contains("navigation") || lower.contains("navlite") || lower.contains("waze") ||

            lower.contains("gmm") || lower.startsWith("com.google.android.apps.maps") || lower.startsWith("com.google.android.maps") ||

            lower.contains("samsung.accessibility") || lower.contains("samsung.android.app.settings") ||

            lower.contains("samsung.android.settings") || lower.contains("bbk.launcher") ||

            lower.contains("coloros.security") || lower.contains("iqoo.secure")

        ) {

            return true

        }

        return false

    }

    /**

     * Recursively extracts all text and content descriptions from a node and its descendants

     * up to depth 6 to ensure compound buttons (e.g. FrameLayout -> LinearLayout -> TextView)

     * are fully inspected.

     */

    private fun extractNodeAndDescendantText(node: AccessibilityNodeInfo?, depth: Int = 0): String {

        if (node == null || depth > 6) return ""

        val sb = StringBuilder()

        val t = node.text?.toString()?.trim()

        val d = node.contentDescription?.toString()?.trim()

        if (!t.isNullOrEmpty()) sb.append(t).append(" ")

        if (!d.isNullOrEmpty() && d != t) sb.append(d).append(" ")

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            val childText = extractNodeAndDescendantText(child, depth + 1)

            if (childText.isNotEmpty()) sb.append(childText).append(" ")

        }

        return sb.toString()

    }

    /**

     * Normalizes Unicode hyphens, dashes, and whitespace across languages into standard ASCII.

     */

    private fun normalizeTextHyphens(input: String): String {

        return input

            .replace('\u2010', '-')

            .replace('\u2011', '-')

            .replace('\u2012', '-')

            .replace('\u2013', '-')

            .replace('\u2014', '-')

            .replace('\u2212', '-')

            .replace('\uFE63', '-')

            .replace('\uFF0D', '-')

    }

    /**

     * Rejects widgets that must NEVER be clicked by the auto-accepter (e.g. Accessibility checkboxes,

     * settings switches, radio buttons, input fields, progress bars).

     * Strictly evaluates only widget types — never inspects descendant text during tree traversal.

     */

    private fun isForbiddenWidget(node: AccessibilityNodeInfo?): Boolean {

        if (node == null) return false

        val className = node.className?.toString() ?: ""

        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

        val isSlide = viewId.contains("slide") || viewId.contains("accept") || viewId.contains("swipe")

        if (className.contains("CheckBox", ignoreCase = true) ||

            className.contains("Switch", ignoreCase = true) ||

            className.contains("RadioButton", ignoreCase = true) ||

            className.contains("CompoundButton", ignoreCase = true) ||

            className.contains("ToggleButton", ignoreCase = true) ||

            className.contains("EditText", ignoreCase = true) ||

            (className.contains("SeekBar", ignoreCase = true) && !isSlide) ||

            (className.contains("ProgressBar", ignoreCase = true) && !node.isClickable)

        ) {

            return true

        }

        if (viewId.contains("checkbox") || viewId.contains("switch_widget") ||

            viewId.contains("preference_switch") || viewId.contains("restricted_switch") ||

            viewId.contains("shortcut_checkbox")

        ) {

            return true

        }

        return false

    }

    /**

     * Safety Firewall evaluated strictly on the final resolved candidate button at click time.

     * Prevents clicking standalone Re-centre buttons on Google Maps overlays or active trip cancellation buttons.

     */

    private fun isForbiddenTargetButton(node: AccessibilityNodeInfo?): Boolean {

        if (node == null) return false

        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

        val text = normalizeTextHyphens("${node.text ?: ""} ${node.contentDescription ?: ""}".lowercase(Locale.ROOT))

        // Reject explicit Re-centre navigation buttons

        if (text.contains("re-center") || text.contains("recenter") || text.contains("re-centre") ||

            text.contains("recentre") || text.contains("re center") || text.contains("re centre") ||

            text.contains("re_center") || text.contains("re_centre") || text.contains("री-सेंटर") || text.contains("फिर से केंद्रित") ||

            text.contains("route options") || text.contains("exit navigation") || text.contains("stop navigation") ||

            viewId.contains("recenter") || viewId.contains("recentre") || viewId.contains("re_center") ||

            viewId.contains("re_centre") || viewId.contains("navigation_action_button") ||

            (viewId.endsWith(":id/action_button") && !text.contains("accept") && !text.contains("match") && !text.contains("confirm"))

        ) {

            return true

        }

        // Reject explicit active trip cancellation or off-duty buttons

        if (text.contains("cancel booking") || text.contains("cancel ride") || text.contains("cancel order") ||

            text.contains("cancel trip") || text.contains("contact customer") || text.contains("call customer") ||

            text.contains("go off duty") || text.contains("go offline") || text.contains("start trip") ||

            text.contains("end trip") || text.contains("confirm arrival") || text.contains("confirm otp") ||

            text.contains("confirm drop") || text.contains("collect cash")

        ) {

            return true

        }

        // Reject video, tutorial, training, guide, player cards and buttons

        if (text.contains("video") || text.contains("watch") || text.contains("tutorial") ||

            text.contains("training") || text.contains("how to") || text.contains("learn") ||

            text.contains("guide") || text.contains("player") || text.contains("thumbnail") ||

            viewId.contains("video") || viewId.contains("player") || viewId.contains("tutorial") ||

            viewId.contains("training")

        ) {

            return true

        }

        return false

    }

    /**

     * Determines whether the given text tokens represent an active turn-by-turn navigation HUD

     * with no genuine incoming ride order offer.

     */

    private fun isActiveNavigationTexts(texts: List<String>): Boolean {

        if (texts.isEmpty()) return false

        val joined = texts.joinToString(" ").lowercase(Locale.ROOT)

        val normJoined = normalizeTextHyphens(joined)

        val hasRecentering = normJoined.contains("re-center") || normJoined.contains("recenter") ||

                normJoined.contains("re-centre") || normJoined.contains("recentre") ||

                normJoined.contains("re center") || normJoined.contains("re centre") ||

                normJoined.contains("re_center") || normJoined.contains("re_centre") ||

                normJoined.contains("री-सेंटर") || normJoined.contains("फिर से केंद्रित") ||

                normJoined.contains("route options") || normJoined.contains("exit navigation") ||

                normJoined.contains("stop navigation")

        val hasNavManeuver = normJoined.contains("head north") || normJoined.contains("head south") ||

                normJoined.contains("head east") || normJoined.contains("head west")

        val hasEtaPanel = (normJoined.contains("min") && (normJoined.contains("am") || normJoined.contains("pm")) && normJoined.contains("km"))

        val isNavActive = hasRecentering || hasNavManeuver || (hasEtaPanel && (normJoined.contains("km/h") || normJoined.contains("kmph")))

        if (!isNavActive) return false

        // Check if there is an actual genuine incoming ride offer banner overlaid on top

        val hasExplicitOfferAction = normJoined.contains("tap to match") || normJoined.contains("tap to accept") ||

                normJoined.contains("trip radar") || normJoined.contains("exclusive") ||

                normJoined.contains("swipe to accept") || normJoined.contains("slide to accept") ||

                normJoined.contains("new request") || normJoined.contains("incoming ride") ||

                normJoined.contains("accept") || normJoined.contains("स्वीकार") || normJoined.contains("मैच")

        val hasCurrencyFare = normJoined.contains("₹") || normJoined.contains("rs.") || normJoined.contains("inr") || normJoined.contains("\u20b9")

        // Pure navigation screen with no overlay offer

        if (!hasExplicitOfferAction || !hasCurrencyFare) {

            return true

        }

        return false

    }

    /**

     * Firewall check against active turn-by-turn navigation screens.

     */

    private fun isActiveNavigationScreen(rootNode: AccessibilityNodeInfo?): Boolean {

        if (rootNode == null) return false

        val allTexts = mutableListOf<String>()

        collectLeafTexts(rootNode, allTexts)

        return isActiveNavigationTexts(allTexts)

    }

    /**

     * Validation guaranteeing that a candidate card or UI screen represents a genuine incoming ride order

     * before any click can execute (Strict v3.3.4 baseline).

     */

    private fun hasValidRideOrderContext(details: RideDetails, cardTexts: List<String>, pkg: String): Boolean {

        val joined = cardTexts.joinToString(" ").lowercase(Locale.ROOT)

        // 0. Immediate rejection of media, tutorial, and help videos

        if (joined.contains("video") || joined.contains("watch") || joined.contains("tutorial") ||

            joined.contains("training") || joined.contains("how to") || joined.contains("learn") ||

            joined.contains("guide") || joined.contains("player")

        ) {

            return false

        }

        // 0b. Platform specific rejection tokens (Driver Hero Pro dw2.h and n92)

        if (pkg.contains("redtaxi") || pkg.contains("red_taxi")) {

            if (RED_TAXI_REJECT_TOKENS.any { joined.contains(it) }) return false

        }

        if (pkg.contains("namma") || pkg.contains("yatri") || pkg.contains("juspay")) {

            if (NAMMA_REJECT_TOKENS.any { joined.contains(it) }) return false

        }

        if (pkg.contains("uber")) {

            if (joined.contains("another driver matched") || joined.contains("matched to this request") ||

                joined.contains("matching in progress") || joined.contains("finding matches") ||

                joined.contains("matches near you")

            ) return false

        }

        // 1. Definite match if valid positive fare is extracted

        if (details.fare != null && details.fare in 10..50000) {

            return true

        }

        // 2. Definite match if valid pickup distance is extracted

        if (details.distance != null && details.distance in 0.0f..150f) {

            return true

        }

        // 3. Definite match if Advance / Scheduled / Queued ride indicator is found

        if (details.isAdvanceRide) {

            return true

        }

        val hasAction = joined.contains("accept") || joined.contains("match") || joined.contains("take") ||

                joined.contains("book") || joined.contains("confirm") || joined.contains("स्वीकार") || joined.contains("एक्सेप्ट") ||

                joined.contains("ஏற்கவும்") || joined.contains("ஏற்றுக்கொள்") || joined.contains("உறுதிப்படுத்து") || joined.contains("பொருந்து")

        val hasCurrency = joined.contains("₹") || joined.contains("rs.") || joined.contains("inr") || joined.contains("\u20b9")

        val hasDistanceUnit = joined.contains("km") || joined.contains("kmph") || joined.contains("meter") || joined.contains("min away") ||

                joined.contains("கி.மீ") || joined.contains("கிமீ")

        val hasTripDirection = joined.contains("pickup") || joined.contains("drop") || joined.contains("destination") || joined.contains("passenger") || joined.contains("customer")

        val hasRideType = joined.contains("auto") || joined.contains("bike") || joined.contains("prime") || joined.contains("mini") || joined.contains("cab") || joined.contains("parcel") || joined.contains("delivery") ||

                joined.contains("taxi") || joined.contains("டாக்ஸி")

        return (hasAction || hasCurrency) && (hasDistanceUnit || hasTripDirection || hasRideType)

    }

    private val scanTicker = object : Runnable {

        override fun run() {

            if (isRunning && ::prefs.isInitialized && prefs.isEnabled && !prefs.isBanned) {

                try {

                    val now = System.currentTimeMillis()

                    // Auto-reconnect & pre-warm shell stream if terminated during device sleep (throttled, non-blocking on tapExecutor)

                    if (now - lastShellCheckTime > 5000L) {

                        lastShellCheckTime = now

                        if (shizukuShell == null || shizukuOut == null) {

                            tapExecutor.execute {

                                try { ensurePariShell() } catch (e: Exception) {}

                            }

                        }

                    }

                    if (pendingPlatform != null) {

                        checkPendingVerification(now)

                    }

                    val elapsedSinceEvent = SystemClock.elapsedRealtime() - lastEventTime

                    // IDLE-ONLY WATCHDOG: Only poll windows and root if no accessibility events have occurred for >= 2000ms

                    // This eliminates all Binder lock contention and CPU clashing when incoming rides are firing active events!

                    if (elapsedSinceEvent >= 2000L && now >= globalCooldownUntil && (prefs.subscriptionExpiryTimestamp <= 0 || prefs.subscriptionExpiryTimestamp > now)) {

                        val activeRoot = rootInActiveWindow

                        val activePkg = activeRoot?.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

                        val isForegroundIgnored = isSelfPackage(activePkg) || isRecentsOrLauncherOverview(activeRoot)

                        // 1. Check all interactive windows for topmost floating ride overlays / popups (cross-app & minimized)

                        // Always inspect windows: floating overlay popups (Rapido/Ola/Uber) float above launcher/home screen!

                        val winList = try { windows } catch (e: Exception) { null }

                        var processedOverlay = false

                        if (!winList.isNullOrEmpty()) {

                            for (window in winList.reversed()) {

                                val winRoot = window.root ?: continue

                                val winPkg = winRoot.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

                                if (winPkg.isNotEmpty() && !isSelfPackage(winPkg) && !isIgnoredPackage(winPkg) && !isSystemOrLauncherPackage(winPkg)) {

                                    if (isRideHailingPackage(winPkg)) {

                                        checkAndProcessRoot(winRoot)

                                        processedOverlay = true

                                        break

                                    } else {

                                        checkAndProcessOverlayRoot(winRoot)

                                    }

                                }

                            }

                        }

                        // 2. Scan active foreground root if no overlay was processed

                        if (!processedOverlay && activeRoot != null && !isForegroundIgnored) {

                            if (activePkg.isNotEmpty() && !isIgnoredPackage(activePkg) && !isSystemOrLauncherPackage(activePkg)) {

                                if (isRideHailingPackage(activePkg)) {

                                    checkAndProcessRoot(activeRoot)

                                }

                            }

                        }

                    }

                } catch (e: Exception) {

                    // Periodic scan exception safely caught

                }

            }

            if (isRunning) {

                scanHandler?.postDelayed(this, 1000L) // 1000ms idle watchdog loop

            }

        }

    }

    override fun onServiceConnected() {

        super.onServiceConnected()

        isRunning = true

        try {

            val currentInfo = serviceInfo ?: AccessibilityServiceInfo()

            currentInfo.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or

                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or

                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or

                    AccessibilityEvent.TYPE_WINDOWS_CHANGED

            currentInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            currentInfo.flags = (currentInfo.flags and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()) or

                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or

                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS

            currentInfo.notificationTimeout = 2L

            serviceInfo = currentInfo

        } catch (e: Exception) {

            Log.w(TAG, "Failed to update AccessibilityServiceInfo", e)

        }

        try {

            if (wakeLock?.isHeld == false) {

                wakeLock?.acquire(24 * 60 * 60 * 1000L) // Safe 24h CPU wake lock while active

            }

        } catch (e: Exception) {}

        Thread { ensurePariShell() }.start()

        scanHandler?.removeCallbacks(scanTicker)

        scanHandler?.post(scanTicker)

        Log.i(TAG, "⚡ TouchInputService Connected (Clean Flags, Idle-Only Watchdog, 2ms notificationTimeout)")

    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) return

        lastEventTime = SystemClock.elapsedRealtime()

        if (!::prefs.isInitialized) return

        if (!prefs.isEnabled || prefs.isBanned) return

        try {

            val rawEventPkg = event.packageName?.toString() ?: ""

            val eventPkg = rawEventPkg.lowercase(Locale.ROOT)

        // ── Phase 0: Lockscreen Wake & Minimized Notification Interception ──

        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {

            val notification = event.parcelableData as? android.app.Notification

            val extras = notification?.extras

            val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""

            val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""

            val bigText = extras?.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

            val subText = extras?.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

            val rawNotifText = event.text.joinToString(" ")

            val notifCombined = "$rawNotifText $title $text $bigText $subText".lowercase(Locale.ROOT)

            // Reject ongoing foreground service notifications (e.g. "Looking for orders", "You are online", "Finding trips")

            val isOngoing = notification != null && (

                (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0) ||

                (notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE != 0) ||

                (notification.flags and android.app.Notification.FLAG_NO_CLEAR != 0)

            )

            val isStatusNotification = notifCombined.contains("you are online") ||

                    notifCombined.contains("you're online") ||

                    notifCombined.contains("looking for orders") ||

                    notifCombined.contains("searching for orders") ||

                    notifCombined.contains("finding trips") ||

                    notifCombined.contains("finding orders") ||

                    notifCombined.contains("waiting for orders") ||

                    notifCombined.contains("on duty") ||

                    notifCombined.contains("you are on duty") ||

                    notifCombined.contains("offline") ||

                    notifCombined.contains("go online") ||

                    notifCombined.contains("gps searching") ||

                    notifCombined.contains("background location") ||

                    notifCombined.contains("app is running in background")

            if (isOngoing && isStatusNotification) {

                return

            }

            // Determine target package: either from event or from notification text

            val targetPkg = when {

                isRideHailingPackage(eventPkg) -> rawEventPkg

                notifCombined.contains("ola") -> "com.olacabs.oladriver"

                notifCombined.contains("rapido") || notifCombined.contains("captain") -> "com.rapido.captain"

                notifCombined.contains("uber") -> "com.ubercab.driver"

                notifCombined.contains("porter") -> "com.theporter.android.driverapp"

                notifCombined.contains("namma") || notifCombined.contains("yatri") -> "in.juspay.nammayatripartner"

                else -> ""

            }

            if (targetPkg.isNotEmpty()) {

                val isExplicitRideOffer = notifCombined.contains("new ride") ||

                        notifCombined.contains("new order") ||

                        notifCombined.contains("booking incoming") ||

                        notifCombined.contains("incoming booking") ||

                        notifCombined.contains("incoming ride") ||

                        notifCombined.contains("incoming order") ||

                        notifCombined.contains("accept in") ||

                        notifCombined.contains("tap to accept") ||

                        notifCombined.contains("tap to match") ||

                        notifCombined.contains("ride request") ||

                        notifCombined.contains("trip request") ||

                        notifCombined.contains("order request") ||

                        notifCombined.contains("advance booking") ||

                        notifCombined.contains("scheduled ride") ||

                        ((notifCombined.contains("₹") || notifCombined.contains("rs.") || notifCombined.contains("inr")) &&

                         (notifCombined.contains("pickup") || notifCombined.contains("drop") || notifCombined.contains("km") || notifCombined.contains("trip")))

                if (isExplicitRideOffer) {

                    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager

                    val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

                    val isScreenOn = pm?.isInteractive ?: true

                    val isLocked = km?.isKeyguardLocked ?: false

                    if ((!isScreenOn || isLocked) && prefs.autoWakeLockscreen) {

                        Log.i(TAG, "🌙 Incoming ride notification while screen is LOCKED/OFF from $targetPkg! Waking screen...")

                        wakeScreenAndBringToForeground(targetPkg)

                    } else if (prefs.acceptWhenMinimized) {

                        val activeRoot = rootInActiveWindow

                        val activePkg = activeRoot?.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

                        if (activePkg != targetPkg.lowercase(Locale.ROOT)) {

                            val isRapidoTarget = targetPkg.contains("rapido", ignoreCase = true) ||

                                    targetPkg.contains("captain", ignoreCase = true) ||

                                    targetPkg.contains("rider", ignoreCase = true)

                            // Check if an overlay/floating window from targetPkg is already visible on screen

                            val hasActiveTargetWindow = try {

                                windows?.any { win ->

                                    val winPkg = win.root?.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

                                    winPkg.contains("rapido", ignoreCase = true) ||

                                            winPkg.contains("captain", ignoreCase = true) ||

                                            winPkg.contains("rider", ignoreCase = true)

                                } ?: false

                            } catch (e: Exception) { false }

                            // If screen is interactive & unlocked, Rapido displays a floating overlay popup.

                            // DO NOT force-launch the full app with startActivity(launchIntent), as that destroys the popup and opens the full app!

                            if (isRapidoTarget && (hasActiveTargetWindow || (isScreenOn && !isLocked))) {

                                Log.i(TAG, "⚡ Rapido incoming offer detected — keeping floating overlay popup active (no auto-open)")

                            } else {

                                Log.i(TAG, "📱 Incoming ride notification while app is MINIMIZED from $targetPkg! Bringing to foreground...")

                                wakeScreenAndBringToForeground(targetPkg)

                            }

                        }

                    }

                }

            }

            return

        }

        // Instant exit for our own app's UI and explicitly ignored packages

        if (isSelfPackage(eventPkg) || isIgnoredPackage(eventPkg)) {

            return

        }

        // When event originates from Launcher/SystemUI, inspect interactive windows for floating ride overlays before discarding

        if (isSystemOrLauncherPackage(eventPkg)) {

            val now = System.currentTimeMillis()

            if (now >= globalCooldownUntil && (prefs.subscriptionExpiryTimestamp <= 0 || prefs.subscriptionExpiryTimestamp > now)) {

                val winList = try { windows } catch (e: Exception) { null }

                if (!winList.isNullOrEmpty()) {

                    for (window in winList.reversed()) {

                        val winRoot = window.root ?: continue

                        val winPkg = winRoot.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

                        if (winPkg.isNotEmpty() && !isSelfPackage(winPkg) && !isIgnoredPackage(winPkg) && !isSystemOrLauncherPackage(winPkg)) {

                            if (isRideHailingPackage(winPkg)) {

                                checkAndProcessRoot(winRoot)

                                return

                            }

                        }

                    }

                }

            }

            return

        }

        val now = System.currentTimeMillis()

        if (prefs.subscriptionExpiryTimestamp in 1..now) return

        // ── Active Ride Customer Cancellation Watcher (Lightweight, rate-limited to 5s) ──

        if (activeRideId != null && now - lastCancellationCheckTime >= 5000L) {

            val rootNodeForCancel = event.source ?: rootInActiveWindow

            if (rootNodeForCancel != null) {

                val rawPkg = rootNodeForCancel.packageName?.toString() ?: ""

                val pkg = rawPkg.lowercase(Locale.ROOT)

                if (pkg.isNotEmpty() && !isIgnoredPackage(pkg) && !isSystemOrLauncherPackage(pkg)) {

                    checkAndHandleCancellation(rootNodeForCancel, pkg)

                }

            }

        }

        // ── Phase 1: Post-Acceptance Verification Engine (Only if pending) ──

        if (pendingPlatform != null) {

            checkPendingVerification(now, event.source)

        }

        if (now < globalCooldownUntil) return

        // ── Phase 2: Active Incoming Offers Scanning ──

        // 1. Direct event.source processing for instant zero-latency strike (<0.1ms)

        val srcNode = event.source

        if (srcNode != null) {

            val srcPkg = srcNode.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

            if (srcPkg.isNotEmpty() && !isSelfPackage(srcPkg) && !isIgnoredPackage(srcPkg) && !isSystemOrLauncherPackage(srcPkg)) {

                val targetRoot = if (srcNode.window?.root != null) {

                    srcNode.window!!.root!!

                } else if (isRideHailingPackage(srcPkg)) {

                    // In floating overlay windows, climb srcNode to find its window root (never fall back to focused launcher)

                    var top = srcNode

                    var p = srcNode.parent

                    while (p != null) {

                        top = p

                        p = p.parent

                    }

                    top

                } else {

                    rootInActiveWindow ?: srcNode

                }

                if (isRideHailingPackage(srcPkg)) {

                    checkAndProcessRoot(targetRoot)

                    return

                } else {

                    val targetPkg = targetRoot.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

                    if (targetPkg.isNotEmpty() && isIgnoredPackage(targetPkg)) {

                        return

                    }

                    checkAndProcessOverlayRoot(targetRoot)

                }

            }

        }

        // 2. Process active floating and dialog windows in reverse z-order (topmost first)

        // Query windows on window state changes, OR whenever the event is from a ride hailing package (e.g. Rapido countdown/content updates)

        val isWindowStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||

                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||

                isRideHailingPackage(eventPkg)

        if (isWindowStateChange) {

            val winList = try { windows } catch (e: Exception) { null }

            if (!winList.isNullOrEmpty()) {

                for (window in winList.reversed()) {

                    val winRoot = window.root ?: continue

                    val winPkg = winRoot.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

                    if (winPkg.isNotEmpty() && !isSelfPackage(winPkg) && !isIgnoredPackage(winPkg) && !isSystemOrLauncherPackage(winPkg)) {

                        if (isRideHailingPackage(winPkg)) {

                            checkAndProcessRoot(winRoot)

                            return // Exit immediately after processing ride window to prevent redundant root re-scans

                        } else {

                            checkAndProcessOverlayRoot(winRoot)

                        }

                    }

                }

            }

        }

        // 3. Active foreground root scan (0.0ms fallback)

        val activeRoot = rootInActiveWindow

        if (activeRoot != null) {

            val actPkg = activeRoot.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

            if (actPkg.isNotEmpty() && !isSelfPackage(actPkg) && !isIgnoredPackage(actPkg) && !isSystemOrLauncherPackage(actPkg)) {

                if (isRideHailingPackage(actPkg)) {

                    checkAndProcessRoot(activeRoot)

                }

            }

        }

    } catch (t: Throwable) {

        Log.e(TAG, "Protected onAccessibilityEvent caught error", t)

    }

}

    override fun onUnbind(intent: Intent?): Boolean {

        isRunning = false

        scanHandler?.removeCallbacks(scanTicker)

        return super.onUnbind(intent)

    }

    override fun onInterrupt() {

        Log.w(TAG, "Accessibility service interrupted")

    }

    override fun onDestroy() {

        super.onDestroy()

        isRunning = false

        scanHandler?.removeCallbacks(scanTicker)

        scanThread?.quitSafely()

        scanThread = null

        scanHandler = null

        tts?.shutdown()

        try { Shizuku.removeBinderReceivedListener(binderReceivedListener) } catch (e: Exception) {}

        try { Shizuku.removeBinderDeadListener(binderDeadListener) } catch (e: Exception) {}

        try { toneGenerator?.release() } catch (e: Exception) {}

        toneGenerator = null

        try { tapExecutor.shutdownNow() } catch (e: Exception) {}

        try { unregisterReceiver(filterUpdateReceiver) } catch (e: Exception) {}

        synchronized(shellLock) {

            try { shizukuOut?.close() } catch (e: Exception) {}

            try { shizukuShell?.destroy() } catch (e: Exception) {}

            shizukuOut = null

            shizukuShell = null

        }

        Log.d(TAG, "TouchInputService destroyed")

    }

    private fun checkAndProcessRoot(rootNode: AccessibilityNodeInfo) {

        val rawPkg = rootNode.packageName?.toString() ?: ""

        val pkg = rawPkg.lowercase(Locale.ROOT)

        // Instant 0.0ms rejection of our own app, system launcher roots, and ignored packages

        if (isSelfPackage(pkg) || isSystemOrLauncherPackage(pkg) || isIgnoredPackage(pkg)) {

            return

        }

        if (!isPlatformEnabled(pkg)) return

        if (pkg.contains("ola")) {

            processOla(rootNode, rawPkg)

        } else if (pkg.contains("rapido") || pkg.contains("captain") || pkg.contains("rider") || pkg.contains("rr.android")) {

            processRapido(rootNode, rawPkg)

        } else if (pkg.contains("uber")) {

            processUber(rootNode, rawPkg)

        } else if (pkg.contains("porter")) {

            processPorter(rootNode, rawPkg)

        } else if (pkg.contains("redtaxi") || pkg.contains("red_taxi")) {

            processRedTaxi(rootNode, rawPkg)

        } else if (pkg.contains("bharattaxi") || pkg.contains("bharat_taxi") || (pkg.contains("mobility") && !pkg.contains("juspay"))) {

            processBharatTaxi(rootNode, rawPkg)

        } else if (pkg.contains("namma") || pkg.contains("yatri") || pkg.contains("juspay")) {

            processNammaYatri(rootNode, rawPkg)

        } else {

            processFastPlatforms(rootNode, rawPkg)

        }

    }

    private fun checkAndProcessOverlayRoot(rootNode: AccessibilityNodeInfo) {

        val rawPkg = rootNode.packageName?.toString() ?: ""

        val pkg = rawPkg.lowercase(Locale.ROOT)

        if (isSelfPackage(pkg) || isSystemOrLauncherPackage(pkg) || isIgnoredPackage(pkg)) return

        val textList = mutableListOf<String>()

        collectLeafTexts(rootNode, textList)

        if (textList.isEmpty()) return

        // 🛡️ Global Active Navigation Overlay Firewall:

        if (isActiveNavigationTexts(textList)) return

        val combined = normalizeTextHyphens(textList.joinToString(" ").lowercase(Locale.ROOT))

        if (combined.contains("accessibility") || combined.contains("restricted setting") ||

            combined.contains("danger") || combined.contains("risk") || combined.contains("permission") ||

            combined.contains("shortcut") || combined.contains("turn on") || combined.contains("stop service") ||

            combined.contains("re-center") || combined.contains("recenter") || combined.contains("re-centre") ||

            combined.contains("recentre") || combined.contains("re center") || combined.contains("re centre") ||

            combined.contains("re_center") || combined.contains("re_centre") ||

            combined.contains("री-सेंटर") || combined.contains("री सेंटर") || combined.contains("रिसेंटर") ||

            combined.contains("फिर से केंद्रित") ||

            combined.contains("route options") || combined.contains("exit navigation") || combined.contains("stop navigation") ||

            combined.contains("head north") || combined.contains("head south") || combined.contains("head east") ||

            combined.contains("head west") || combined.contains("google maps")

        ) {

            return

        }

        val hasUber = hasUberOverlayIndicatorsText(combined)

        val hasNamma = if (!hasUber) hasNammaYatriOverlayIndicatorsText(combined) else false

        val hasOla = if (!hasUber && !hasNamma) hasOlaOverlayIndicatorsText(combined) else false

        val hasRapido = if (!hasUber && !hasNamma && !hasOla) hasRapidoOverlayIndicatorsText(combined) else false

        if (hasOla) {

            processOla(rootNode, "com.olacabs.oladriver")

        } else if (hasRapido) {

            processRapido(rootNode, "com.rapido.captain")

        } else if (hasUber) {

            processUber(rootNode, "com.ubercab.driver")

        } else if (hasNamma) {

            processNammaYatri(rootNode, "in.juspay.nammayatripartner")

        }

    }

    private fun hasUberOverlayIndicatorsText(combined: String): Boolean {

        if (!::prefs.isInitialized || !prefs.isUberEnabled) return false

        val norm = normalizeTextHyphens(combined.lowercase(Locale.ROOT))

        if (norm.contains("re-center") || norm.contains("recenter") || norm.contains("re-centre") ||

            norm.contains("recentre") || norm.contains("re center") || norm.contains("re centre") ||

            norm.contains("re_center") || norm.contains("re_centre") || norm.contains("री-सेंटर") || norm.contains("exit navigation") || norm.contains("route options") ||

            norm.contains("cancel trip") || norm.contains("cancel ride") || norm.contains("cancel booking") ||

            norm.contains("contact rider") || norm.contains("contact customer") || norm.contains("go offline")) return false

        val hasMatch = norm.contains("match") || norm.contains("tap to match") ||

                norm.contains("exclusive") || norm.contains("tap to accept") ||

                norm.contains("see all requests") || norm.contains("trip radar") ||

                norm.contains("accept trip") || norm.contains("accept offer") ||

                norm.contains("मैच")

        val hasUberContext = (norm.contains("₹") || norm.contains("rs.") || norm.contains("inr") || norm.contains("\u20b9")) &&

                (norm.contains("uber") || norm.contains("min") || norm.contains("km") || norm.contains("trip"))

        return hasMatch && hasUberContext

    }

    private fun hasNammaYatriOverlayIndicatorsText(combined: String): Boolean {

        if (!::prefs.isInitialized || !prefs.isNammaYatriEnabled) return false

        val norm = normalizeTextHyphens(combined.lowercase(Locale.ROOT))

        if (norm.contains("re-center") || norm.contains("recenter") || norm.contains("re-centre") ||

            norm.contains("recentre") || norm.contains("re center") || norm.contains("re centre") ||

            norm.contains("re_center") || norm.contains("re_centre") || norm.contains("री-सेंटर") || norm.contains("exit navigation") || norm.contains("route options") ||

            norm.contains("cancel ride") || norm.contains("cancel order") || norm.contains("cancel booking") ||

            norm.contains("contact customer") || norm.contains("go offline")) return false

        val hasAccept = norm.contains("accept") || norm.contains("स्वीकार") || norm.contains("एक्सेप्ट") || norm.contains("ಸ್ವೀಕರಿಸಿ")

        val hasNammaYatriContext = (norm.contains("/km") || norm.contains("km ·") || norm.contains("assistance") ||

                norm.contains("cash") || norm.contains("namma") || norm.contains("yatri") || norm.contains("juspay") ||

                norm.contains("mana yatri") || norm.contains("yatri sathi") || norm.contains("auto") || norm.contains("cab")) &&

                (norm.contains("₹") || norm.contains("rs.") || norm.contains("inr") || norm.contains("\u20b9"))

        return hasAccept && hasNammaYatriContext

    }

    private fun hasOlaOverlayIndicatorsText(combined: String): Boolean {

        if (!::prefs.isInitialized || !prefs.isOlaEnabled) return false

        val hasAccept = combined.contains("accept") || combined.contains("स्वीकार") || combined.contains("एक्सेप्ट") || combined.contains("ride accept")

        val hasOlaContext = (combined.contains("ola") || combined.contains("prime") || combined.contains("mini") ||

                combined.contains("booking id") || combined.contains("crn") || combined.contains("pickup") || combined.contains("drop")) &&

                (combined.contains("₹") || combined.contains("rs.") || combined.contains("km") || combined.contains("auto"))

        return hasAccept && hasOlaContext

    }

    private fun hasRapidoOverlayIndicatorsText(combined: String): Boolean {

        if (!::prefs.isInitialized || !prefs.isRapidoEnabled) return false

        val hasAccept = combined.contains("accept") || combined.contains("स्वीकार") || combined.contains("order accept") || combined.contains("accept order")

        val hasRapidoContext = (combined.contains("rapido") || combined.contains("captain") || combined.contains("order id") ||

                combined.contains("pickup") || combined.contains("drop") || combined.contains("delivery")) &&

                (combined.contains("₹") || combined.contains("rs.") || combined.contains("km") || combined.contains("bike"))

        return hasAccept && hasRapidoContext

    }

    private fun hasUberOverlayIndicators(node: AccessibilityNodeInfo?): Boolean {

        if (node == null || !::prefs.isInitialized || !prefs.isUberEnabled) return false

        val pkg = node.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

        if (isIgnoredPackage(pkg)) return false

        val textList = mutableListOf<String>()

        collectLeafTexts(node, textList)

        val combined = textList.joinToString(" ").lowercase(Locale.ROOT)

        if (combined.contains("accessibility") || combined.contains("restricted setting") ||

            combined.contains("danger") || combined.contains("risk") || combined.contains("permission") ||

            combined.contains("shortcut") || combined.contains("turn on") || combined.contains("stop service")

        ) return false

        return hasUberOverlayIndicatorsText(combined)

    }

    private fun hasNammaYatriOverlayIndicators(node: AccessibilityNodeInfo?): Boolean {

        if (node == null || !::prefs.isInitialized || !prefs.isNammaYatriEnabled) return false

        val pkg = node.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

        if (isIgnoredPackage(pkg)) return false

        val textList = mutableListOf<String>()

        collectLeafTexts(node, textList)

        val combined = textList.joinToString(" ").lowercase(Locale.ROOT)

        if (combined.contains("accessibility") || combined.contains("restricted setting") ||

            combined.contains("danger") || combined.contains("risk") || combined.contains("permission") ||

            combined.contains("shortcut") || combined.contains("turn on") || combined.contains("stop service")

        ) return false

        return hasNammaYatriOverlayIndicatorsText(combined)

    }

    private fun hasOlaOverlayIndicators(node: AccessibilityNodeInfo?): Boolean {

        if (node == null || !::prefs.isInitialized || !prefs.isOlaEnabled) return false

        val pkg = node.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

        if (isIgnoredPackage(pkg)) return false

        val textList = mutableListOf<String>()

        collectLeafTexts(node, textList)

        val combined = textList.joinToString(" ").lowercase(Locale.ROOT)

        if (combined.contains("accessibility") || combined.contains("restricted setting") ||

            combined.contains("danger") || combined.contains("risk") || combined.contains("permission") ||

            combined.contains("shortcut") || combined.contains("turn on") || combined.contains("stop service")

        ) return false

        return hasOlaOverlayIndicatorsText(combined)

    }

    private fun hasRapidoOverlayIndicators(node: AccessibilityNodeInfo?): Boolean {

        if (node == null || !::prefs.isInitialized || !prefs.isRapidoEnabled) return false

        val pkg = node.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

        if (isIgnoredPackage(pkg)) return false

        val textList = mutableListOf<String>()

        collectLeafTexts(node, textList)

        val combined = textList.joinToString(" ").lowercase(Locale.ROOT)

        if (combined.contains("accessibility") || combined.contains("restricted setting") ||

            combined.contains("danger") || combined.contains("risk") || combined.contains("permission") ||

            combined.contains("shortcut") || combined.contains("turn on") || combined.contains("stop service")

        ) return false

        return hasRapidoOverlayIndicatorsText(combined)

    }

    private fun isPlatformEnabled(pkg: String): Boolean {

        // Strict exclusion of Android SystemUI, system packages, and ignored packages

        if (pkg.isEmpty() || isIgnoredPackage(pkg)) return false

        if (pkg.contains("ola")) return prefs.isOlaEnabled

        if (pkg.contains("rapido") || pkg.contains("captain") || pkg.contains("rider") || pkg.contains("rr.android")) return prefs.isRapidoEnabled

        if (pkg.contains("uber")) return prefs.isUberEnabled

        if (pkg.contains("porter")) return prefs.isPorterEnabled

        if (pkg.contains("redtaxi") || pkg.contains("red_taxi")) return prefs.isRedTaxiEnabled

        if (pkg.contains("bharattaxi") || pkg.contains("bharat_taxi") || (pkg.contains("mobility") && !pkg.contains("juspay"))) return prefs.isBharatTaxiEnabled

        if (pkg.contains("namma") || pkg.contains("yatri") || pkg.contains("juspay")) return prefs.isNammaYatriEnabled

        if (pkg.contains("swiggy") || pkg.contains("zomato")) return prefs.isAppEnabled(pkg)

        return SupportedApps.allPackageNames.any {

            (pkg.contains(it, ignoreCase = true) || it.contains(pkg, ignoreCase = true)) && !it.contains("android")

        } && prefs.isAppEnabled(pkg)

    }

    private fun getNodeDirectOrChildText(btn: AccessibilityNodeInfo?): String {

        if (btn == null) return ""

        val directTxt = "${btn.text ?: ""} ${btn.contentDescription ?: ""}".trim()

        if (directTxt.isNotEmpty()) return directTxt

        // Fast inspection of first 2-3 direct children if container button has empty direct text

        val childCount = btn.childCount

        if (childCount > 0) {

            val sb = StringBuilder()

            val maxChildren = minOf(childCount, 3)

            for (i in 0 until maxChildren) {

                try {

                    val child = btn.getChild(i)

                    if (child != null) {

                        val cTxt = child.text?.toString() ?: ""

                        val cDesc = child.contentDescription?.toString() ?: ""

                        if (cTxt.isNotEmpty()) sb.append(cTxt).append(" ")

                        if (cDesc.isNotEmpty()) sb.append(cDesc).append(" ")

                    }

                } catch (_: Exception) {}

            }

            return sb.toString().trim()

        }

        return ""

    }

    private fun isOlaCountdownActive(btn: AccessibilityNodeInfo?): Boolean {

        if (btn == null) return false

        val combined = getNodeDirectOrChildText(btn).lowercase(Locale.ROOT)

        if (combined.isEmpty()) return false

        // Check ONLY the button (or its direct child label) for explicit countdown syntax (e.g. "accept in 5s", "accept in 5")

        // Never check parent card text to avoid matching trip ETA strings like "Pickup: 45 sec"

        return combined.startsWith("accept in") || combined.contains("accept in ") ||

                combined.startsWith("एक्सेप्ट इन") || combined.contains("एक्सेप्ट इन ") ||

                OLA_COUNTDOWN_REGEX.matches(combined)

    }

    private fun parseCountdownRemainingMs(btn: AccessibilityNodeInfo?): Long {

        if (btn == null) return 1000L

        val txt = getNodeDirectOrChildText(btn).lowercase(Locale.ROOT)

        val match = Regex("""\d+""").find(txt)

        val sec = match?.value?.toIntOrNull() ?: 1

        return (sec * 1000L).coerceIn(200L, 15000L)

    }

    private fun isButtonLocked(btn: AccessibilityNodeInfo?): Boolean {

        return isOlaCountdownActive(btn)

    }

    /**

     * Dedicated Ola Driver Scanner with Pure Pre-Warmed 3-Burst Shell Strike (<0.0ms Instant Strike).

     * Fires pure pre-warmed Shizuku shell taps with natural jitter (0ms, 50ms, 100ms).

     * If locked countdown timer is detected, pre-evaluates filters, pre-arms state, and arms 0s precision timer.

     */

    private fun processOla(rootNode: AccessibilityNodeInfo, packageName: String) {

        val now = System.currentTimeMillis()

        // 1. Fast-Path: If Ola is Pre-Armed, strike immediately upon countdown unlock (0ms delay)

        if (olaPreArmed) {

            if (now - olaPreArmTime > 16000L) {

                olaPreArmed = false

            } else {

                var unlocked = false

                // Single-IPC Direct Text Lookup (<0.2ms) - bypasses 34 view ID queries (English + Hindi fast-path)

                val fastTextNodes = try {

                    val n = rootNode.findAccessibilityNodeInfosByText("accept")

                    if (!n.isNullOrEmpty()) n else {

                        val hindiNodes = rootNode.findAccessibilityNodeInfosByText("स्वीकार")

                        if (!hindiNodes.isNullOrEmpty()) hindiNodes else rootNode.findAccessibilityNodeInfosByText("एक्सेप्ट")

                    }

                } catch (e: Exception) { null }

                val candidateBtn = fastTextNodes?.firstOrNull { n ->

                    n.isVisibleToUser && !isForbiddenWidget(n)

                }

                if (candidateBtn != null) {

                    val (_, targetBounds) = getClickTargetNodeAndBounds(candidateBtn)

                    if (targetBounds.centerX() > 0 && targetBounds.centerY() > 0) {

                        cachedOlaX = targetBounds.centerX()

                        cachedOlaY = targetBounds.centerY()

                    }

                    if (!isOlaCountdownActive(candidateBtn)) {

                        unlocked = true

                    }

                } else {

                    // Fallback to fast search

                    val acceptButtons = mutableListOf<AccessibilityNodeInfo>()

                    findOlaAcceptButtons(rootNode, acceptButtons)

                    val fastBtn = acceptButtons.firstOrNull()

                    if (fastBtn != null) {

                        val (_, targetBounds) = getClickTargetNodeAndBounds(fastBtn)

                        if (targetBounds.centerX() > 0 && targetBounds.centerY() > 0) {

                            cachedOlaX = targetBounds.centerX()

                            cachedOlaY = targetBounds.centerY()

                        }

                        if (!isOlaCountdownActive(fastBtn)) {

                            unlocked = true

                        }

                    } else {

                        // Candidate and fastBtn both null: Check if screen returned to idle home dashboard

                        val rootTextSummary = try {

                            val textList = mutableListOf<String>()

                            collectLeafTexts(rootNode, textList)

                            textList.joinToString(" ").lowercase(Locale.ROOT)

                        } catch (e: Exception) { "" }

                        if (rootTextSummary.contains("today's earning") || rootTextSummary.contains("todays earning") ||

                            rootTextSummary.contains("daily goal") || rootTextSummary.contains("search radius") ||

                            rootTextSummary.contains("you are online") || rootTextSummary.contains("looking for rides")

                        ) {

                            Log.i(TAG, "⚡ [OLA DISARMED] Order card vanished; screen returned to idle dashboard.")

                            olaPreArmed = false

                            return

                        }

                        // Instant decoupled fallback: If button temporarily decoupled during UI re-render, strike pre-cached coordinates

                        if (cachedOlaX > 0 && cachedOlaY > 0 && (now - olaPreArmTime >= cachedOlaCountdownDurationMs.coerceAtLeast(1000L))) {

                            unlocked = true

                        }

                    }

                }

                if (unlocked) {

                    val details = RideDetails(

                        fare = if (cachedOlaPrice != -1) cachedOlaPrice else null,

                        distance = if (cachedOlaPickupKm >= 0f) cachedOlaPickupKm else null,

                        tripDistance = if (cachedOlaDropKm >= 0f) cachedOlaDropKm else null,

                        pickupLocation = cachedOlaPickupLoc,

                        dropLocation = cachedOlaDropLoc,

                        fullText = cachedOlaTexts.joinToString(" "),

                        rawTexts = cachedOlaTexts

                    )

                    executeOlaPreArmedShellStrike(cachedOlaX, cachedOlaY, packageName, details)

                    olaPreArmed = false

                    return

                }

                return

            }

        }

        // Fast-Path: Non-blocking compact price extraction (1:1 Ride Clicker OG)

        var fastOlaFare: Int? = null

        val priceNodes = try { rootNode.findAccessibilityNodeInfosByViewId("com.olacabs.oladriver:id/tv_compact_price") } catch (e: Exception) { null }

        if (!priceNodes.isNullOrEmpty()) {

            val pTxt = priceNodes[0].text?.toString() ?: ""

            val cleanP = pTxt.replace(Regex("[^0-9]"), "")

            fastOlaFare = cleanP.toIntOrNull()

            for (pn in priceNodes) { try { pn.recycle() } catch (ignored: Throwable) {} }

        }

        val acceptButtons = mutableListOf<AccessibilityNodeInfo>()

        findOlaAcceptButtons(rootNode, acceptButtons)

        if (acceptButtons.isEmpty()) {

            // Blind-fire fallback for custom Canvas / Webview overlays in Ola

            val hasOlaContext = hasOlaOverlayIndicators(rootNode)

            if (hasOlaContext) {

                val allRootTexts = mutableListOf<String>()

                collectLeafTexts(rootNode, allRootTexts)

                val rideDetails = parseRideDetails(allRootTexts, allRootTexts)

                if ((rideDetails.fare != null || rideDetails.distance != null || rideDetails.isAdvanceRide) &&

                    hasValidRideOrderContext(rideDetails, allRootTexts, packageName)

                ) {

                    val isCooldownActive = now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)

                    if ((!isCooldownActive || rideDetails.isAdvanceRide) && evaluateRideFilters(rideDetails)) {

                        val screenW = resources.displayMetrics.widthPixels

                        val screenH = resources.displayMetrics.heightPixels

                        val rootRect = Rect()

                        rootNode.getBoundsInScreen(rootRect)

                        val y = if (rootRect.height() in 200..(screenH * 0.75f).toInt() && rootRect.bottom < (screenH * 0.85f).toInt()) {

                            (rootRect.bottom - (rootRect.height() * 0.15f)).toInt()

                        } else {

                            (screenH * 0.85f).toInt()

                        }

                        val x = if (rootRect.width() in 200..screenW) {

                            (rootRect.left + (rootRect.width() * 0.75f)).toInt().coerceIn(10, screenW - 10)

                        } else {

                            screenW / 2

                        }

                        executeDirectPariTap(x, y, packageName, rideDetails)

                    }

                }

            }

            return

        }

        val allFiltersDisabled = !cachedIsFareActive && !cachedIsDistActive && cachedFavZones.isEmpty() && cachedNonFavZones.isEmpty()

        val isCooldownActive = now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)

        for (btnNode in acceptButtons) {

            if (isForbiddenTargetButton(btnNode)) {

                continue

            }

            val (_, targetBounds) = getClickTargetNodeAndBounds(btnNode)

            val screenW = resources.displayMetrics.widthPixels

            val screenH = resources.displayMetrics.heightPixels

            val targetX = if (targetBounds.centerX() > 0) targetBounds.centerX() else (screenW / 2)

            val targetY = if (targetBounds.centerY() > 0) targetBounds.centerY() else (screenH * 0.85f).toInt()

            val isCountdown = isOlaCountdownActive(btnNode)

            // Zero-Latency Filter Bypass (<0.05ms): If all filters & zones are OFF and countdown is not active, strike instantly!

            if (allFiltersDisabled && !isCountdown && !isCooldownActive) {

                val bypassDetails = RideDetails(fare = fastOlaFare, fullText = "Ola Zero-Latency Bypass", rawTexts = listOf("Ola Ride"))

                executeOlaPureShellStrike(btnNode, packageName, bypassDetails, targetX, targetY)

                olaPreArmed = false

                break

            }

            val cardNode = getIndividualRideCard(btnNode)

            val textList = mutableListOf<String>()

            collectLeafTexts(cardNode, textList)

            // Filter out home screen summary metrics like "Today's Earnings", "Daily Goals", etc.

            val filteredList = textList.filterNot { t ->

                val l = t.lowercase(Locale.ROOT).trim()

                l.startsWith("today") || l.startsWith("earnings") || l.startsWith("daily") ||

                l.startsWith("wallet") || l.startsWith("search radius") || l.startsWith("high cancellation")

            }

            val targetTexts = if (filteredList.isNotEmpty()) filteredList else textList

            val rideDetails = parseRideDetails(targetTexts)

            // Strict Ride Context Gating: ensure this card is truly an incoming ride offer

            if (!hasValidRideOrderContext(rideDetails, targetTexts, packageName)) {

                continue

            }

            // Cooldown check: Advance / Back-to-Back rides ALWAYS bypass cooldown so scheduled bookings are never missed!

            if (isCooldownActive && !rideDetails.isAdvanceRide) {

                continue

            }

            if (!evaluateRideFilters(rideDetails)) {

                continue

            }

            if (isCountdown) {

                olaPreArmed = true

                olaPreArmTime = now

                cachedOlaPrice = rideDetails.fare ?: -1

                cachedOlaPickupKm = rideDetails.distance ?: -1f

                cachedOlaDropKm = rideDetails.tripDistance ?: -1f

                cachedOlaPickupLoc = rideDetails.pickupLocation

                cachedOlaDropLoc = rideDetails.dropLocation

                cachedOlaTexts.clear()

                cachedOlaTexts.addAll(targetTexts)

                cachedOlaX = targetX

                cachedOlaY = targetY

                val remainingMs = parseCountdownRemainingMs(btnNode)

                cachedOlaCountdownDurationMs = remainingMs

                Log.i(TAG, "⚡ [OLA PRE-ARMED] Ride armed at ($cachedOlaX, $cachedOlaY)! Countdown: ${remainingMs}ms. Fare=₹$cachedOlaPrice, Pickup=${cachedOlaPickupKm}km, Loc: $cachedOlaPickupLoc -> $cachedOlaDropLoc")

                // High-precision strike timer scheduled at exact 0s countdown boundary

                handler.postDelayed({

                    if (olaPreArmed && System.currentTimeMillis() - olaPreArmTime < 16000L) {

                        Log.i(TAG, "⚡ [OLA COUNTDOWN TIMER ZERO-DELAY STRIKE] Firing at ($cachedOlaX, $cachedOlaY)")

                        val armedDetails = RideDetails(

                            fare = if (cachedOlaPrice != -1) cachedOlaPrice else null,

                            distance = if (cachedOlaPickupKm >= 0f) cachedOlaPickupKm else null,

                            tripDistance = if (cachedOlaDropKm >= 0f) cachedOlaDropKm else null,

                            pickupLocation = cachedOlaPickupLoc,

                            dropLocation = cachedOlaDropLoc,

                            fullText = cachedOlaTexts.joinToString(" "),

                            rawTexts = cachedOlaTexts

                        )

                        executeOlaPreArmedShellStrike(cachedOlaX, cachedOlaY, packageName, armedDetails)

                        olaPreArmed = false

                    }

                }, remainingMs)

            } else {

                executeOlaPureShellStrike(btnNode, packageName, rideDetails, targetX, targetY)

                olaPreArmed = false

            }

            break

        }

    }

    /**

     * Dedicated Rapido Captain Scanner (<0.5ms Ultra-Low Latency In-Memory Strike).

     * Supports Full-Screen Broadcast Popups, Main Menu Top/Up Header Banners, AND Multi-Order Right-Side Queue Cards.

     * Fires 6-Burst Rapid-Fire Native ACTION_CLICK (climbing 8 parent levels).

     */

    private fun processRapido(rootNode: AccessibilityNodeInfo, packageName: String) {
        val acceptButtons = mutableListOf<AccessibilityNodeInfo>()
        findRapidoAcceptButtons(rootNode, acceptButtons)

        val screenH = resources.displayMetrics.heightPixels
        val screenW = resources.displayMetrics.widthPixels
        val now = System.currentTimeMillis()

        if (acceptButtons.isEmpty()) {
            return
        }

        // Filter buttons that are visible on screen (both top header banners and bottom/right-side cards)
        val visibleButtons = acceptButtons.filter { btn ->
            val r = Rect()
            btn.getBoundsInScreen(r)
            r.centerY() in 20 until screenH && r.centerX() in 20 until screenW && r.width() > 10 && r.height() > 10
        }

        val candidateButtons = if (visibleButtons.isNotEmpty()) visibleButtons else acceptButtons

        val anyFilterActive = cachedIsFareActive || cachedIsDistActive || cachedNonFavZones.isNotEmpty() || cachedFavZones.isNotEmpty()

        // ⚡ Zero-Latency Full Bypass (<0.05ms): When all filters are OFF, strike immediately!
        if (!anyFilterActive) {
            val isCooldownActive = now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)
            if (!isCooldownActive) {
                val btnNode = candidateButtons[0]
                var fastRapidoFare: Int? = null
                try {
                    val p = btnNode.parent
                    if (p != null) {
                        for (i in 0 until p.childCount) {
                            val ch = p.getChild(i) ?: continue
                            val txt = ch.text?.toString() ?: ""
                            if (txt.contains("₹")) {
                                val digits = txt.filter { it.isDigit() }
                                if (digits.isNotEmpty()) {
                                    fastRapidoFare = digits.toIntOrNull()
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}

                val fastDetails = RideDetails(
                    fare = fastRapidoFare,
                    fullText = "Rapido Order",
                    rawTexts = listOf("Rapido Order")
                )
                executeRapidoBurstStrike(btnNode, packageName, fastDetails)
            }
            return
        }

        // Fast-Path: Single Incoming Order (95% of live ride events) with Active Filters
        if (candidateButtons.size == 1) {
            val btnNode = candidateButtons[0]
            val cardNode = getIndividualRideCard(btnNode)
            val textList = mutableListOf<String>()
            collectLeafTexts(cardNode, textList)
            val filteredTextList = textList.filterNot { t ->
                val l = t.lowercase(Locale.ROOT).trim()
                l.startsWith("today") || l.startsWith("earnings") || l.startsWith("wallet") || l.startsWith("search radius")
            }
            val targetTexts = if (filteredTextList.isNotEmpty()) filteredTextList else textList
            val rideDetails = parseRideDetails(targetTexts)

            val isCooldownActive = now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)
            if (!isCooldownActive || rideDetails.isAdvanceRide) {
                if (evaluateRideFilters(rideDetails)) {
                    executeRapidoBurstStrike(btnNode, packageName, rideDetails)
                }
            }
            return
        }

        // Multi-Order Smart Ranking: When multiple right-side cards appear simultaneously
        var bestButton: AccessibilityNodeInfo? = null
        var bestDetails: RideDetails? = null
        var bestScore: Float = -1f

        for (btnNode in candidateButtons) {
            val cardNode = getIndividualRideCard(btnNode)
            val textList = mutableListOf<String>()
            collectLeafTexts(cardNode, textList)
            val filteredTextList = textList.filterNot { t ->
                val l = t.lowercase(Locale.ROOT).trim()
                l.startsWith("today") || l.startsWith("earnings") || l.startsWith("wallet") || l.startsWith("search radius")
            }
            val targetTexts = if (filteredTextList.isNotEmpty()) filteredTextList else textList
            val rideDetails = parseRideDetails(targetTexts)

            val isCooldownActive = now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)
            if (isCooldownActive && !rideDetails.isAdvanceRide) {
                continue
            }

            if (!evaluateRideFilters(rideDetails)) {
                continue
            }

            val fare = (rideDetails.fare ?: 0).toFloat()
            val tripDist = rideDetails.tripDistance ?: (rideDetails.distance ?: 1f)
            val ratePerKm = if (tripDist > 0f) fare / tripDist else fare
            val score = if (fare > 0f) fare + (ratePerKm * 3f) else 10f

            if (score > bestScore) {
                bestScore = score
                bestButton = btnNode
                bestDetails = rideDetails
            }
        }

        if (bestButton != null && bestDetails != null) {
            executeRapidoBurstStrike(bestButton, packageName, bestDetails)
        }
    }

    /**
     * Dedicated Uber Driver Scanner: 100% Aligned with Lapak Engine.
     * Incorporates geometric card isolation (climbUberCardBySize), fast-find accept words,
     * non-blocking navigation architecture, and hardened stationary gesture taps.
     */
    private fun processUber(rootNode: AccessibilityNodeInfo, packageName: String) {

        val screenH = resources.displayMetrics.heightPixels

        val screenW = resources.displayMetrics.widthPixels

        val now = System.currentTimeMillis()

        // 1. Lapak Fast-Find: Direct IPC lookup for primary accept words (<0.1ms)

        var foundCard = fastFindUberCardLapak(rootNode, screenH)

        // 2. Fallback: View ID and scored tree scan

        if (foundCard == null) {

            foundCard = scanUberCardFallbackLapak(rootNode, screenH)

        }

        if (foundCard == null) {

            // Overlay indicator fallback for blind tap if floating overlay appears

            val isUberOverlay = hasUberOverlayIndicators(rootNode)

            if (isUberOverlay) {

                val allRootTexts = mutableListOf<String>()

                collectLeafTexts(rootNode, allRootTexts)

                val rideDetails = parseRideDetails(allRootTexts, allRootTexts)

                if ((rideDetails.fare != null || rideDetails.distance != null || rideDetails.isAdvanceRide) &&

                    hasValidRideOrderContext(rideDetails, allRootTexts, packageName)

                ) {

                    val isCooldownActive = now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)

                    if (!isCooldownActive && evaluateRideFilters(rideDetails)) {

                        val x = screenW / 2

                        val y = (screenH * 0.85f).toInt()

                        executeDirectPariTap(x, y, packageName, rideDetails, isUberBlind = true)

                    }

                }

            }

            return

        }

        val btnNode = foundCard.button

        val cardRoot = foundCard.cardRoot

        // 3. Extract texts strictly within isolated card bounds (prevents DecorView / map earnings leakage)

        val cardTexts = mutableListOf<String>()

        collectLeafTexts(cardRoot, cardTexts)

        val filtered = cardTexts.filter { t ->

            val l = t.lowercase(Locale.ROOT).trim()

            !l.startsWith("today") && !l.startsWith("earnings") && !l.startsWith("online") && !l.startsWith("finding trips")

        }

        val targetTexts = if (filtered.isNotEmpty()) filtered else cardTexts

        val rideDetails = parseRideDetails(targetTexts, targetTexts)

        // 4. Strict Ride Context Gating: ensure this card is truly an incoming ride offer

        if (!hasValidRideOrderContext(rideDetails, targetTexts, packageName)) {

            return

        }

        // 5. Cooldown check: Advance / Back-to-Back rides ALWAYS bypass cooldown

        val isCooldownActive = now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)

        if (isCooldownActive && !rideDetails.isAdvanceRide) {

            return

        }

        // 6. Evaluate filters (Fare, Distance, Zone)

        if (!evaluateRideFilters(rideDetails)) {

            return

        }

        // 7. Debounce consecutive duplicate taps

        val r = Rect()

        btnNode.getBoundsInScreen(r)

        val orderSignature = "uber_${rideDetails.fare}_${rideDetails.distance}_${r.centerX()}_${r.centerY()}"

        if (orderSignature == lastUberOrderSignature && now - lastUberOrderTime < 250L) {

            return

        }

        lastUberOrderSignature = orderSignature

        lastUberOrderTime = now

        val targetPkg = if (packageName.isNotEmpty() && !packageName.startsWith("com.android") && packageName != "android") {

            packageName

        } else {

            "com.ubercab.driver"

        }

        // 8. Execute Lapak Hardened Tap (85ms stationary hold -> 110ms hold -> 180ms hold)

        executeUberHardenedTap(btnNode, targetPkg, rideDetails, cardRoot)

    }

    /**

     * Dedicated Namma Yatri / Yatri Driver Scanner.

     * Incorporates Driver Hero Pro multi-window scanning for BottomSheet offer dialogs

     * and anti-detection performAction(ACTION_CLICK) execution.

     */

    private fun processNammaYatri(rootNode: AccessibilityNodeInfo, packageName: String) {

        // Multi-Window scan for BottomSheet offer dialogs (Driver Hero Pro n92/DriverAccessibilityService technique)

        var targetRoot = rootNode

        val allWindows = try { windows } catch (e: Exception) { null }

        if (!allWindows.isNullOrEmpty()) {

            for (window in allWindows) {

                try {

                    val wRoot = window.root ?: continue

                    val wPkg = wRoot.packageName?.toString()?.lowercase(Locale.ROOT) ?: continue

                    if (wPkg.contains("namma") || wPkg.contains("yatri") || wPkg.contains("juspay")) {

                        val testButtons = mutableListOf<AccessibilityNodeInfo>()

                        findNammaYatriAcceptButtons(wRoot, testButtons)

                        if (testButtons.isNotEmpty()) {

                            targetRoot = wRoot

                            break

                        }

                    }

                } catch (e: Exception) {}

            }

        }

        // Check DutyAccepter NAMMA_PAYMENT_BANNER_REGEX: ignore payment dues banners

        val allTexts = mutableListOf<String>()

        collectLeafTexts(targetRoot, allTexts)

        val allJoined = allTexts.joinToString(" ").lowercase(Locale.ROOT)

        if (NAMMA_PAYMENT_BANNER_REGEX.containsMatchIn(allJoined) && !allJoined.contains("accept") && !allJoined.contains("स्वीकार")) {

            return

        }

        val acceptButtons = mutableListOf<AccessibilityNodeInfo>()

        findNammaYatriAcceptButtons(targetRoot, acceptButtons)

        if (acceptButtons.isEmpty()) {

            tryNammaGeometricAccept(targetRoot, packageName)

            return

        }

        // 🛡️ Lazy Navigation Firewall: only runs when candidate accept buttons are detected

        if (isActiveNavigationScreen(targetRoot)) {

            return

        }

        val now = System.currentTimeMillis()

        for (btnNode in acceptButtons) {

            val cardNode = getIndividualRideCard(btnNode)

            val textList = mutableListOf<String>()

            collectLeafTexts(cardNode, textList)

            // DHP n92 rejection tokens filter + payment banner filter

            val joined = textList.joinToString(" ").lowercase(Locale.ROOT)

            if (NAMMA_REJECT_TOKENS.any { joined.contains(it) } || NAMMA_PAYMENT_BANNER_REGEX.containsMatchIn(joined)) {

                continue

            }

            val rideDetails = parseRideDetails(textList, textList)

            // Strict Ride Context Gating: ensure this card is truly an incoming ride offer

            if (!hasValidRideOrderContext(rideDetails, textList, packageName)) {

                continue

            }

            // Cooldown check: Advance / Back-to-Back rides ALWAYS bypass cooldown

            val isCooldownActive = now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)

            if (isCooldownActive && !rideDetails.isAdvanceRide) {

                continue

            }

            if (!evaluateRideFilters(rideDetails)) {

                continue

            }

            val targetPkg = if (packageName.isNotEmpty() && !packageName.startsWith("com.android") && packageName != "android") {

                packageName

            } else {

                "in.juspay.nammayatripartner"

            }

            // Execute accept via performAction(ACTION_CLICK) - anti-detection safe

            executeTapAction(btnNode, targetPkg, rideDetails)

            break

        }

    }

    /**

     * Dedicated Red Taxi Scanner (mirrors Driver Hero Pro ep.java mode 2 & dw2.java).

     * Filters Tamil rejection tokens and executes via standard accessibility click.

     */

    private fun processRedTaxi(rootNode: AccessibilityNodeInfo, packageName: String) {

        val acceptButtons = mutableListOf<AccessibilityNodeInfo>()

        findRedTaxiAcceptButtons(rootNode, acceptButtons)

        if (acceptButtons.isEmpty()) {

            return

        }

        if (isActiveNavigationScreen(rootNode)) {

            return

        }

        val now = System.currentTimeMillis()

        for (btnNode in acceptButtons) {

            val cardNode = getIndividualRideCard(btnNode)

            val textList = mutableListOf<String>()

            collectLeafTexts(cardNode, textList)

            val joined = textList.joinToString(" ").lowercase(Locale.ROOT)

            // DHP dw2.h rejection tokens filter

            if (RED_TAXI_REJECT_TOKENS.any { joined.contains(it) }) {

                continue

            }

            val rideDetails = parseRideDetails(textList, textList)

            if (!hasValidRideOrderContext(rideDetails, textList, packageName)) {

                continue

            }

            val isCooldownActive = now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)

            if (isCooldownActive && !rideDetails.isAdvanceRide) {

                continue

            }

            if (!evaluateRideFilters(rideDetails)) {

                continue

            }

            val targetPkg = if (packageName.isNotEmpty() && !packageName.startsWith("com.android") && packageName != "android") {

                packageName

            } else {

                "com.ind.redtaxi.driverapp"

            }

            executeTapAction(btnNode, targetPkg, rideDetails)

            break

        }

    }

    /**

     * Dedicated Bharat Taxi Scanner (mirrors Driver Hero Pro ep.java mode 0 & dp.java).

     * Scans Bharat Taxi ride offers and executes via standard accessibility click.

     */

    private fun processBharatTaxi(rootNode: AccessibilityNodeInfo, packageName: String) {

        val acceptButtons = mutableListOf<AccessibilityNodeInfo>()

        findBharatTaxiAcceptButtons(rootNode, acceptButtons)

        if (acceptButtons.isEmpty()) {

            return

        }

        if (isActiveNavigationScreen(rootNode)) {

            return

        }

        val now = System.currentTimeMillis()

        for (btnNode in acceptButtons) {

            val cardNode = getIndividualRideCard(btnNode)

            val textList = mutableListOf<String>()

            collectLeafTexts(cardNode, textList)

            val joined = textList.joinToString(" ").lowercase(Locale.ROOT)

            if (joined.contains("cancel ride") || joined.contains("cancel booking") ||

                joined.contains("start trip") || joined.contains("arrived at pickup")

            ) {

                continue

            }

            val rideDetails = parseRideDetails(textList, textList)

            if (!hasValidRideOrderContext(rideDetails, textList, packageName)) {

                continue

            }

            val isCooldownActive = now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)

            if (isCooldownActive && !rideDetails.isAdvanceRide) {

                continue

            }

            if (!evaluateRideFilters(rideDetails)) {

                continue

            }

            val targetPkg = if (packageName.isNotEmpty() && !packageName.startsWith("com.android") && packageName != "android") {

                packageName

            } else {

                "in.mobility.bharattaxidriver"

            }

            executeTapAction(btnNode, targetPkg, rideDetails)

            break

        }

    }

    /**

     * Dedicated Porter Scanner (DutyAccepter-v27 Porter logic).

     * Starts scanning at 20% screen height threshold and executes slide-to-accept or tap.

     */

    private fun processPorter(rootNode: AccessibilityNodeInfo, packageName: String) {

        if (isActiveNavigationScreen(rootNode)) return

        val screenH = resources.displayMetrics.heightPixels

        val minY = (screenH * 0.20f).toInt() // DutyAccepter Porter 20% Y threshold

        val candidates = mutableListOf<Pair<AccessibilityNodeInfo, Boolean>>()

        findPorterCandidates(rootNode, minY, candidates)

        if (candidates.isEmpty()) return

        val now = System.currentTimeMillis()

        for ((candidateNode, isSlide) in candidates) {

            val cardNode = getIndividualRideCard(candidateNode)

            val textList = mutableListOf<String>()

            collectLeafTexts(cardNode, textList)

            val joined = textList.joinToString(" ").lowercase(Locale.ROOT)

            if (joined.contains("cancel order") || joined.contains("cancel pickup") ||

                joined.contains("arrived at pickup") || joined.contains("end trip")

            ) {

                continue

            }

            val rideDetails = parseRideDetails(textList, textList)

            if (!hasValidRideOrderContext(rideDetails, textList, packageName)) {

                continue

            }

            val isCooldownActive = now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)

            if (isCooldownActive && !rideDetails.isAdvanceRide) {

                continue

            }

            if (!evaluateRideFilters(rideDetails)) {

                continue

            }

            val targetPkg = if (packageName.isNotEmpty() && !packageName.startsWith("com.android") && packageName != "android") {

                packageName

            } else {

                "com.theporter.android.driverapp"

            }

            if (isSlide) {

                val nodeRect = Rect()

                candidateNode.getBoundsInScreen(nodeRect)

                var trackRect = expandToWideContainer(candidateNode, nodeRect)

                if (trackRect.width() < trackRect.height() * 3) {

                    trackRect = stretchSlideBand(nodeRect)

                }

                executeTapAction(candidateNode, targetPkg, rideDetails, precomputedBounds = trackRect, isSlideAction = true)

            } else {

                executeTapAction(candidateNode, targetPkg, rideDetails, isSlideAction = false)

            }

            break

        }

    }

    private fun findPorterCandidates(

        node: AccessibilityNodeInfo?,

        minY: Int,

        results: MutableList<Pair<AccessibilityNodeInfo, Boolean>>,

        depth: Int = 0

    ) {

        if (node == null || isForbiddenWidget(node) || depth > 25) return

        val rect = Rect()

        node.getBoundsInScreen(rect)

        if (!rect.isEmpty && rect.centerY() >= minY) {

            val viewId = node.viewIdResourceName?.toString() ?: ""

            val shortId = if (viewId.isNotEmpty()) viewId.substringAfterLast(":id/") else ""

            val isPorterId = PORTER_ACCEPT_ID_SET.contains(viewId) || PORTER_ACCEPT_ID_SET.contains(shortId)

            val text = node.text?.toString()?.trim() ?: ""

            val desc = node.contentDescription?.toString()?.trim() ?: ""

            val combined = "$text $desc".trim().lowercase(Locale.ROOT)

            var isSlide = false

            var isAccept = false

            if (PORTER_SLIDE_REGEXES.any { it.containsMatchIn(combined) }) {

                isSlide = true

                isAccept = true

            } else if (isPorterId || PORTER_ACCEPT_TEXT_SET.contains(combined) || (combined.length < 35 && PORTER_ACCEPT_TEXT_SET.any { combined.contains(it) })) {

                isAccept = true

            }

            if (isAccept) {

                results.add(Pair(node, isSlide))

                return

            }

        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            findPorterCandidates(child, minY, results, depth + 1)

            if (results.size >= 3) return

        }

    }

    /**

     * Climbs parent hierarchy up to 4 levels to find the full horizontal track for sliders (DutyAccepter-v27).

     */

    private fun expandToWideContainer(node: AccessibilityNodeInfo, initial: Rect): Rect {

        val maxHeight = resources.displayMetrics.heightPixels * 0.13f

        var parent = node.parent

        var i = 0

        var currentRect = initial

        while (parent != null && i < 4) {

            val rectOf = Rect()

            parent.getBoundsInScreen(rectOf)

            if (rectOf.isEmpty || !rectOf.contains(currentRect) || rectOf.width() <= currentRect.width() + 20 || rectOf.height() > maxHeight) {

                break

            }

            currentRect = rectOf

            parent = parent.parent

            i++

        }

        return currentRect

    }

    /**

     * Stretches slide band across 4% to 96% screen width if button node bounds are narrow (DutyAccepter-v27).

     */

    private fun stretchSlideBand(nodeRect: Rect): Rect {

        val metrics = resources.displayMetrics

        val screenH = metrics.heightPixels

        val screenW = metrics.widthPixels

        val bandH = nodeRect.height().coerceIn(20, (screenH * 0.10f).toInt())

        val bandY = (nodeRect.centerY() - (bandH / 2)).coerceIn(0, screenH - bandH)

        return Rect((screenW * 0.04f).toInt(), bandY, (screenW * 0.96f).toInt(), bandY + bandH)

    }

    /**

     * Fallback geometric acceptance for Namma Yatri when accessibility nodes are obscured,

     * drawn on Canvas, or unclickable (DutyAccepter-v27 tryNammaGeometricAccept).

     */

    private fun tryNammaGeometricAccept(targetRoot: AccessibilityNodeInfo, packageName: String) {

        if (isActiveNavigationScreen(targetRoot)) return

        val now = System.currentTimeMillis()

        if (now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)) return

        val screenW = resources.displayMetrics.widthPixels

        val screenH = resources.displayMetrics.heightPixels

        // Attempt green button center detection via Accessibility Screenshot (Android 11+)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            try {

                takeScreenshot(

                    Display.DEFAULT_DISPLAY,

                    applicationContext.mainExecutor,

                    object : AccessibilityService.TakeScreenshotCallback {

                        override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {

                            try {

                                val buffer = screenshotResult.hardwareBuffer

                                val colorSpace = screenshotResult.colorSpace

                                val hwBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)

                                buffer.close()

                                val bitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)

                                hwBitmap?.recycle()

                                val greenCenter = if (bitmap != null) findGreenButtonCenter(bitmap) else null

                                bitmap?.recycle()

                                if (greenCenter != null) {

                                    Log.i(TAG, "🟢 NAMMA_GEO: Detected Green Button at (${greenCenter.first}, ${greenCenter.second})")

                                    val dummyDetails = RideDetails()

                                    executeDirectPariTap(greenCenter.first, greenCenter.second, packageName, dummyDetails)

                                } else {

                                    // Geometric Lower Band Fallback (Bottom 12% card band)

                                    val defaultX = screenW / 2

                                    val defaultY = (screenH * 0.88f).toInt()

                                    val dummyDetails = RideDetails()

                                    executeDirectPariTap(defaultX, defaultY, packageName, dummyDetails)

                                }

                            } catch (e: Exception) {

                                Log.w(TAG, "⚠️ Screenshot processing error: ${e.message}")

                            }

                        }

                        override fun onFailure(errorCode: Int) {

                            // Fallback direct coordinate tap

                            val defaultX = screenW / 2

                            val defaultY = (screenH * 0.88f).toInt()

                            val dummyDetails = RideDetails()

                            executeDirectPariTap(defaultX, defaultY, packageName, dummyDetails)

                        }

                    }

                )

                return

            } catch (e: Exception) {

                Log.w(TAG, "⚠️ takeScreenshot failed, using direct geometric band")

            }

        }

        // Direct geometric coordinate fallback for API < 30

        val defaultX = screenW / 2

        val defaultY = (screenH * 0.88f).toInt()

        val dummyDetails = RideDetails()

        executeDirectPariTap(defaultX, defaultY, packageName, dummyDetails)

    }

    /**

     * Color-space detection of Namma Yatri's signature green accept button (DutyAccepter-v27 findGreenButtonCenter).

     * Rule: green >= 120, red <= 110, blue <= 110, green - red >= 60, green - blue >= 50.

     */

    private fun findGreenButtonCenter(bmp: Bitmap): Pair<Int, Int>? {

        val width = bmp.width

        val height = bmp.height

        val pixels = IntArray(width * height)

        bmp.getPixels(pixels, 0, width, 0, 0, width, height)

        val minTargetWidth = (width * 0.30f).toInt()

        var bestWidth = 0

        var bestRow = 0

        var bestStartCol = 0

        val startRow = (height * 0.50f).toInt() // Button is in the lower half

        for (y in startRow until height) {

            var currentRun = 0

            var currentStart = -1

            for (x in 0 until width) {

                val pixel = pixels[y * width + x]

                val r = (pixel shr 16) and 0xFF

                val g = (pixel shr 8) and 0xFF

                val b = pixel and 0xFF

                if (g >= 120 && r <= 110 && b <= 110 && (g - r) >= 60 && (g - b) >= 50) {

                    if (currentStart < 0) currentStart = x

                    currentRun++

                } else {

                    if (currentRun > bestWidth) {

                        bestWidth = currentRun

                        bestRow = y

                        bestStartCol = currentStart

                    }

                    currentRun = 0

                    currentStart = -1

                }

            }

            if (currentRun > bestWidth) {

                bestWidth = currentRun

                bestRow = y

                bestStartCol = currentStart

            }

        }

        if (bestWidth >= minTargetWidth) {

            val centerX = bestStartCol + (bestWidth / 2)

            var topY = bestRow

            while (topY > 0) {

                val p = pixels[(topY - 1) * width + centerX]

                val r = (p shr 16) and 0xFF

                val g = (p shr 8) and 0xFF

                val b = p and 0xFF

                if (g < 120 || r > 110 || b > 110) break

                topY--

            }

            var bottomY = bestRow

            while (bottomY < height - 1) {

                val p = pixels[(bottomY + 1) * width + centerX]

                val r = (p shr 16) and 0xFF

                val g = (p shr 8) and 0xFF

                val b = p and 0xFF

                if (g < 120 || r > 110 || b > 110) break

                bottomY++

            }

            val centerY = (topY + bottomY) / 2

            return Pair(centerX, centerY)

        }

        return null

    }

    /**

     * Fast platform scanner for Swiggy, Zomato, etc.

     */

    private fun processFastPlatforms(rootNode: AccessibilityNodeInfo, packageName: String) {

        val appInfo = SupportedApps.findByPackage(packageName)

        val acceptButtons = mutableListOf<AccessibilityNodeInfo>()

        findGeneralAcceptButtons(rootNode, appInfo, acceptButtons)

        if (acceptButtons.isEmpty()) return

        val now = System.currentTimeMillis()

        for (btnNode in acceptButtons) {

            if (isForbiddenWidget(btnNode)) continue

            val cardNode = getIndividualRideCard(btnNode)

            val textList = mutableListOf<String>()

            collectLeafTexts(cardNode, textList)

            val rideDetails = parseRideDetails(textList, textList)

            // Strict Ride Context Gating: ensure this card is truly an incoming ride/order offer

            if (!hasValidRideOrderContext(rideDetails, textList, packageName)) {

                continue

            }

            // Cooldown check

            val isCooldownActive = now < globalCooldownUntil || now < (packageBusyUntilMs[packageName] ?: 0L)

            if (isCooldownActive && !rideDetails.isAdvanceRide) {

                continue

            }

            if (!evaluateRideFilters(rideDetails)) {

                continue

            }

            val isSlide = (btnNode.viewIdResourceName?.contains("slide") == true ||

                    btnNode.viewIdResourceName?.contains("slider") == true ||

                    btnNode.viewIdResourceName?.contains("seek") == true)

            executeTapAction(btnNode, packageName, rideDetails, isSlideAction = isSlide)

            break

        }

    }

    // ── Dedicated Button and Hierarchy Searches (<0.1ms Native C++ Lookups + Deep v3.3.4 Traversal) ──

    /**

     * Dedicated Ola Button Search using direct C++ lookup fast-paths (<0.1ms)

     * and deep recursive fallback matching v3.3.4.

     */

    private fun findOlaAcceptButtons(

        node: AccessibilityNodeInfo?,

        results: MutableList<AccessibilityNodeInfo>

    ) {

        if (node == null || isForbiddenWidget(node)) return

        val screenW = resources.displayMetrics.widthPixels

        val screenH = resources.displayMetrics.heightPixels

        // Fast Path 1: Primary Surgical Strike for Ola View IDs (<0.05ms single-IPC)

        val primaryOlaIds = arrayOf(

            "com.olacabs.oladriver:id/btn_accept",

            "btn_accept",

            "com.olacabs.oladriver:id/accept_booking_btn",

            "accept_booking_btn",

            "driver_action_accept"

        )

        for (primId in primaryOlaIds) {

            val primNodes = try { node.findAccessibilityNodeInfosByViewId(primId) } catch (e: Exception) { null }

            if (!primNodes.isNullOrEmpty()) {

                for (n in primNodes) {

                    if (n.isVisibleToUser && !isForbiddenWidget(n)) {

                        val r = Rect()

                        n.getBoundsInScreen(r)

                        if (r.width() in 20..(screenW + 10) && r.height() in 20..(screenH * 0.45f).toInt() && r.centerY() > 0) {

                            results.add(n)

                            return

                        }

                    }

                }

            }

        }

        // Fast Path 1b: Fallback C++ View ID lookups

        for (id in OLA_ACCEPT_IDS) {

            if (id == "btn_accept" || id == "accept_booking_btn" || id == "driver_action_accept") continue

            val shortNodes = try { node.findAccessibilityNodeInfosByViewId(id) } catch (e: Exception) { null }

            if (!shortNodes.isNullOrEmpty()) {

                for (n in shortNodes) {

                    if (n.isVisibleToUser && !isForbiddenWidget(n)) {

                        val r = Rect()

                        n.getBoundsInScreen(r)

                        if (r.width() in 20..(screenW + 10) && r.height() in 20..(screenH * 0.45f).toInt() && r.centerY() > 0) {

                            results.add(n)

                            return

                        }

                    }

                }

            }

        }

        // Fast Path 2: Direct C++ text lookups (Essential root action words - Android does substring containment)

        val OLA_FAST_TEXT_KEYS = arrayOf("accept", "स्वीकार", "एक्सेप्ट", "confirm", "accept booking", "accept advance", "ride accept")

        for (textVariant in OLA_FAST_TEXT_KEYS) {

            val textNodes = try { node.findAccessibilityNodeInfosByText(textVariant) } catch (e: Exception) { null }

            if (!textNodes.isNullOrEmpty()) {

                for (n in textNodes) {

                    if (n.isVisibleToUser && !isForbiddenWidget(n)) {

                        val nText = "${n.text ?: ""} ${n.contentDescription ?: ""}".trim().lowercase(Locale.ROOT)

                        val isRejected = OLA_REJECT_TOKENS.any { token -> nText.contains(token) }

                        if (nText.isNotEmpty() && !isRejected) {

                            val r = Rect()

                            n.getBoundsInScreen(r)

                            if (r.width() in 20..(screenW + 10) && r.height() in 20..(screenH * 0.45f).toInt() && r.centerY() > 0) {

                                results.add(n)

                                return

                            }

                        }

                    }

                }

            }

        }

        // Fallback: Comprehensive recursive traversal

        findOlaAcceptButtonsRecursive(node, results, screenW, screenH, 0)

    }

    private fun findOlaAcceptButtonsRecursive(

        node: AccessibilityNodeInfo?,

        results: MutableList<AccessibilityNodeInfo>,

        screenW: Int,

        screenH: Int,

        depth: Int = 0

    ) {

        if (node == null || isForbiddenWidget(node) || depth > 25) return

        val viewId = node.viewIdResourceName?.toString() ?: ""

        if (viewId.isNotEmpty()) {

            val shortId = viewId.substringAfterLast(":id/")

            val isOlaId = OLA_ACCEPT_ID_SET.contains(viewId) || OLA_ACCEPT_ID_SET.contains(shortId) ||

                    OLA_ACCEPT_IDS.any { id -> viewId.endsWith(":id/$id", ignoreCase = true) || viewId.equals(id, ignoreCase = true) }

            val nodeCombined = "${node.text ?: ""} ${node.contentDescription ?: ""}".lowercase(Locale.ROOT)

            val isNodeRejected = OLA_REJECT_TOKENS.any { token -> nodeCombined.contains(token) } ||

                    viewId.contains("cancel") || viewId.contains("reject")

            if (isOlaId && !isNodeRejected && !results.contains(node)) {

                val r = Rect()

                node.getBoundsInScreen(r)

                if (r.width() in 20..(screenW + 10) && r.height() in 20..(screenH * 0.45f).toInt() && r.centerY() > 0) {

                    results.add(node)

                    return

                }

            }

        }

        val text = node.text?.toString()?.trim() ?: ""

        val desc = node.contentDescription?.toString()?.trim() ?: ""

        val combined = "$text $desc".trim().lowercase(Locale.ROOT)

        if (combined.isNotEmpty()) {

            val isRejected = OLA_REJECT_TOKENS.any { token -> combined.contains(token) }

            if (!isRejected && combined.length < 35) {

                val isExact = OLA_EXACT_BUTTON_TEXT_SET.contains(combined)

                val isCountdown = OLA_COUNTDOWN_REGEX.matches(combined) || combined.startsWith("accept in") || combined.contains("accept in")

                val startsWithAction = combined.startsWith("accept") || combined.startsWith("confirm") ||

                        combined.startsWith("स्वीकार") || combined.startsWith("एक्सेप्ट") ||

                        combined.startsWith("राइड स्वीकारें")

                if ((isExact || isCountdown || startsWithAction) && !results.contains(node)) {

                    val r = Rect()

                    node.getBoundsInScreen(r)

                    if (r.width() in 20..(screenW + 10) && r.height() in 20..(screenH * 0.45f).toInt() && r.centerY() > 0) {

                        results.add(node)

                        return

                    }

                }

            }

        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            findOlaAcceptButtonsRecursive(child, results, screenW, screenH, depth + 1)

            if (results.isNotEmpty()) return

        }

    }

    private fun findRapidoAcceptButtons(
        node: AccessibilityNodeInfo?,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null || isForbiddenWidget(node)) return

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val maxButtonHeight = (screenH * 0.25f).toInt().coerceAtLeast(300)

        // ── Stage 1: RidePilot / OG 6 Fast Core Text Queries (<0.5ms Native C++ Lookup) ──
        val RAPIDO_FAST_QUERIES = listOf("Accept", "स्वीकार करा", "स्वीकारा", "Accept Order", "Accept Ride", "Accept Trip")
        for (query in RAPIDO_FAST_QUERIES) {
            val textNodes = try { node.findAccessibilityNodeInfosByText(query) } catch (e: Exception) { null }
            if (!textNodes.isNullOrEmpty()) {
                for (n in textNodes) {
                    if (n.isVisibleToUser && !isForbiddenWidget(n) && !results.contains(n)) {
                        val nText = "${n.text ?: ""} ${n.contentDescription ?: ""}".trim().lowercase(Locale.ROOT)
                        if (nText.contains("cancel") || nText.contains("reject") || nText.contains("decline")) continue
                        val r = Rect()
                        n.getBoundsInScreen(r)
                        if (r.width() in 20..(screenW + 10) && r.height() in 20..maxButtonHeight && r.centerY() > 0) {
                            results.add(n)
                        }
                    }
                }
                if (results.isNotEmpty()) return
            }
        }

        // ── Stage 2: Primary Surgical Strike for Rapido View IDs (<0.05ms single-IPC) ──
        val primaryRapidoIds = arrayOf("btn_accept", "order_accept_btn", "accept_btn", "btn_confirm", "accept")
        for (id in primaryRapidoIds) {
            val shortNodes = try { node.findAccessibilityNodeInfosByViewId(id) } catch (e: Exception) { null }
            if (!shortNodes.isNullOrEmpty()) {
                for (n in shortNodes) {
                    if (n.isVisibleToUser && !isForbiddenWidget(n) && !results.contains(n)) {
                        val r = Rect()
                        n.getBoundsInScreen(r)
                        if (r.width() in 20..(screenW + 10) && r.height() in 20..maxButtonHeight && r.centerY() > 0) {
                            results.add(n)
                        }
                    }
                }
                if (results.isNotEmpty()) return
            }
        }

        // ── Stage 3: Ultra-Fast In-Memory Traversal Fallback (<0.5ms, Zero Binder IPC) ──
        findRapidoAcceptButtonsRecursive(node, results, 0)
        if (results.isNotEmpty()) {
            return
        }

        // C++ Fast Path 2: Direct Text Lookups (<0.1ms IPC)
        for (textKey in RAPIDO_FAST_TEXT_KEYS) {
            val textNodes = try { node.findAccessibilityNodeInfosByText(textKey) } catch (e: Exception) { null }
            if (!textNodes.isNullOrEmpty()) {
                for (n in textNodes) {
                    if (n.isVisibleToUser && !isForbiddenWidget(n) && !results.contains(n)) {
                        val nText = "${n.text ?: ""} ${n.contentDescription ?: ""}".trim().lowercase(Locale.ROOT)
                        if (nText.contains("cancel") || nText.contains("reject") || nText.contains("decline")) continue
                        val r = Rect()
                        n.getBoundsInScreen(r)
                        if (r.width() in 20..(screenW + 10) && r.height() in 20..maxButtonHeight && r.centerY() > 0) {
                            results.add(n)
                        }
                    }
                }
                if (results.isNotEmpty()) return
            }
        }

        // C++ Fast Path 3: Fallback View ID Lookups
        for (id in RAPIDO_ACCEPT_IDS) {
            if (id == "btn_accept" || id == "accept_btn" || id == "btn_confirm" || id == "accept") continue
            val shortNodes = try { node.findAccessibilityNodeInfosByViewId(id) } catch (e: Exception) { null }
            if (!shortNodes.isNullOrEmpty()) {
                for (n in shortNodes) {
                    if (n.isVisibleToUser && !isForbiddenWidget(n) && !results.contains(n)) {
                        val r = Rect()
                        n.getBoundsInScreen(r)
                        if (r.width() in 20..(screenW + 10) && r.height() in 20..maxButtonHeight && r.centerY() > 0) {
                            results.add(n)
                        }
                    }
                }
                if (results.isNotEmpty()) return
            }
        }
    }


    private fun findRapidoAcceptButtonsRecursive(

        node: AccessibilityNodeInfo?,

        results: MutableList<AccessibilityNodeInfo>,

        depth: Int = 0

    ) {

        if (node == null || isForbiddenWidget(node) || depth > 25) return

        val text = node.text?.toString()?.trim() ?: ""

        val desc = node.contentDescription?.toString()?.trim() ?: ""

        val combined = "$text $desc".trim().lowercase(Locale.ROOT)

        if (combined.isNotEmpty()) {

            val isTimer = combined.contains("accept in") || combined.contains("एक्सेप्ट इन") ||

                    combined.contains(" sec") || combined.contains("sec ")

            val isRejected = isTimer || combined.contains("cancel") || combined.contains("reject") || combined.contains("decline") ||

                    combined.contains("skip") || combined.contains("go to") || combined.contains("arrived") ||

                    combined.contains("contact")

            if (!isRejected) {

                for (cue in RAPIDO_ACCEPT_TEXT_SET) {

                    if (combined == cue || (combined.contains(cue) && combined.length < 35)) {

                        if (!results.contains(node)) {

                            results.add(node)

                            break

                        }

                    }

                }

            }

        }

        val viewId = node.viewIdResourceName?.toString() ?: ""

        if (viewId.isNotEmpty()) {

            val shortId = viewId.substringAfterLast(":id/")

            val isRapidoId = RAPIDO_ACCEPT_ID_SET.contains(viewId) || RAPIDO_ACCEPT_ID_SET.contains(shortId) ||

                    RAPIDO_ACCEPT_IDS.any { id -> viewId.endsWith(":id/$id", ignoreCase = true) || viewId.equals(id, ignoreCase = true) }

            val isNodeRejected = combined.contains("cancel") || combined.contains("reject") || combined.contains("decline") ||

                    viewId.contains("cancel") || viewId.contains("reject")

            if (isRapidoId && !isNodeRejected && !results.contains(node)) {

                results.add(node)

            }

        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            findRapidoAcceptButtonsRecursive(child, results, depth + 1)

            if (results.size >= 5) return

        }

    }

    private data class FoundUberCard(

        val button: AccessibilityNodeInfo,

        val cardRoot: AccessibilityNodeInfo

    )

    /**

     * 100% Lapak CardFinder climbBySize algorithm for Uber:

     * Climbs parent hierarchy until card height >= 3x button height and width >= button width.

     * Hard-stops at 90% screen height to strictly isolate the offer card without swallowing DecorView / background map.

     */

    private fun climbUberCardBySize(from: AccessibilityNodeInfo, screenHeight: Int): AccessibilityNodeInfo? {

        val rect = Rect()

        from.getBoundsInScreen(rect)

        if (rect.isEmpty || from.parent == null) return null

        val rect2 = Rect()

        var parent = from.parent

        var candidate: AccessibilityNodeInfo? = null

        var climbCount = 0

        while (parent != null && climbCount < 8) {

            parent.getBoundsInScreen(rect2)

            // Lapak: stop if card exceeds 90% of screen height

            if (rect2.height() > screenHeight * 0.90f) {

                break

            }

            // Lapak: A card must be at least 3x the height of its accept button and at least as wide

            if (rect2.height() >= rect.height() * 3 && rect2.width() >= rect.width()) {

                candidate = parent

                break

            }

            candidate = parent

            parent = parent.parent

            climbCount++

        }

        var cardRoot = candidate ?: return null

        // Lapak: climb up to 3 more times if parent bounds are identical (wrappers with zero margins)

        val rootBounds = Rect()

        cardRoot.getBoundsInScreen(rootBounds)

        var p = cardRoot.parent

        var wrapCount = 0

        while (p != null && wrapCount < 3) {

            p.getBoundsInScreen(rect2)

            if (rect2 != rootBounds) break

            cardRoot = p

            p = p.parent

            wrapCount++

        }

        return cardRoot

    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo, boundary: AccessibilityNodeInfo? = null): AccessibilityNodeInfo? {

        if (node.isClickable && !isForbiddenWidget(node)) return node

        var curr = node.parent

        var depth = 0

        while (curr != null && depth < 6) {

            if (curr.isClickable && !isForbiddenWidget(curr)) return curr

            if (boundary != null && curr == boundary) break

            curr = curr.parent

            depth++

        }

        return null

    }

    /**

     * 100% Lapak swallowsCard / refusesAsButton logic:

     * A clickable ancestor is refused as the "accept button" if its area is more than 1/3 of the card,

     * which means it is the entire card body/container rather than the specific button pill.

     */

    private fun isControlSwallowingCard(control: AccessibilityNodeInfo, cardRoot: AccessibilityNodeInfo): Boolean {

        val ctrlRect = Rect()

        val cardRect = Rect()

        control.getBoundsInScreen(ctrlRect)

        cardRoot.getBoundsInScreen(cardRect)

        val cardArea = cardRect.width().toLong() * cardRect.height().toLong()

        val ctrlArea = ctrlRect.width().toLong() * ctrlRect.height().toLong()

        return cardArea > 0 && ctrlArea * 3L > cardArea

    }

    /**

     * 100% Lapak CardFinder.fastFind for Uber:

     * Fast IPC text queries on primary accept keywords ("match", "accept", "confirm", "exclusive", "मैच", "स्वीकार").

     * Validates visibility, skips reject tokens & status messages, and climbs by size to bind the exact card.

     */

    private fun fastFindUberCardLapak(rootNode: AccessibilityNodeInfo, screenHeight: Int): FoundUberCard? {

        // Fast-Find on verified action words only (excludes header labels like 'exclusive' to avoid top-badge false clicks)

        val fastWords = listOf("match", "accept", "confirm", "मैच", "स्वीकार")

        for (word in fastWords) {

            val nodes = try { rootNode.findAccessibilityNodeInfosByText(word) } catch (e: Exception) { null }

            if (nodes.isNullOrEmpty()) continue

            for (node in nodes) {

                if (!node.isVisibleToUser || isForbiddenWidget(node)) continue

                val txt = node.text?.toString()?.trim() ?: ""

                val desc = node.contentDescription?.toString()?.trim() ?: ""

                val combined = "$txt $desc".lowercase(Locale.ROOT)

                if (combined.contains("matching in progress") ||

                    combined.contains("another driver matched") ||

                    combined.contains("matches near you") ||

                    combined.contains("finding matches") ||

                    combined.contains("matched to this request") ||

                    combined.contains("matched to trip") ||

                    combined.contains("decline") || combined.contains("reject") || combined.contains("cancel") ||

                    combined.contains("32e5d85e") || combined.contains("dispatch_reject_tap")

                ) {

                    continue

                }

                val isAcceptMatch = UBER_STRICT_ACCEPT_REGEX.matches(txt) ||

                        UBER_STRICT_ACCEPT_REGEX.matches(desc) ||

                        txt.equals("match", ignoreCase = true) ||

                        txt.equals("confirm", ignoreCase = true) ||

                        txt.equals("accept", ignoreCase = true) ||

                        desc.equals("match", ignoreCase = true) ||

                        desc.equals("confirm", ignoreCase = true) ||

                        (combined.length <= 15 && (combined.contains("match") || combined.contains("confirm") || combined.contains("accept") || combined.contains("मैच") || combined.contains("स्वीकार")))

                if (!isAcceptMatch) continue

                val clickable = findClickableAncestor(node) ?: node

                val cardRoot = climbUberCardBySize(clickable, screenHeight) ?: climbUberCardBySize(node, screenHeight)

                if (cardRoot != null) {

                    val actualButton = if (isControlSwallowingCard(clickable, cardRoot)) node else clickable

                    return FoundUberCard(actualButton, cardRoot)

                }

            }

        }

        return null

    }

    private fun scanUberCardFallbackLapak(rootNode: AccessibilityNodeInfo, screenHeight: Int): FoundUberCard? {

        // 1. Direct view ID lookups

        for (id in UBER_ACCEPT_IDS) {

            val nodes = try { rootNode.findAccessibilityNodeInfosByViewId(id) } catch (e: Exception) { null }

            if (nodes.isNullOrEmpty()) continue

            for (node in nodes) {

                if (!node.isVisibleToUser || isForbiddenWidget(node)) continue

                val txt = node.text?.toString()?.trim() ?: ""

                val desc = node.contentDescription?.toString()?.trim() ?: ""

                val combined = "$txt $desc".lowercase(Locale.ROOT)

                if (combined.contains("matching in progress") ||

                    combined.contains("another driver matched") ||

                    combined.contains("matches near you") ||

                    combined.contains("finding matches") ||

                    combined.contains("matched to this request") ||

                    combined.contains("matched to trip") ||

                    combined.contains("decline") || combined.contains("reject") || combined.contains("cancel")

                ) continue

                val clickable = findClickableAncestor(node) ?: node

                val cardRoot = climbUberCardBySize(clickable, screenHeight) ?: climbUberCardBySize(node, screenHeight)

                if (cardRoot != null) {

                    val actualButton = if (isControlSwallowingCard(clickable, cardRoot)) node else clickable

                    return FoundUberCard(actualButton, cardRoot)

                }

            }

        }

        // 2. Recursive tree scan with priority scoring

        val scoredButtons = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()

        collectScoredUberAcceptButtons(rootNode, scoredButtons, 0)

        scoredButtons.sortByDescending { it.second }

        for (pair in scoredButtons) {

            val btn = pair.first

            val clickable = findClickableAncestor(btn) ?: btn

            val cardRoot = climbUberCardBySize(clickable, screenHeight) ?: climbUberCardBySize(btn, screenHeight)

            if (cardRoot != null) {

                val actualButton = if (isControlSwallowingCard(clickable, cardRoot)) btn else clickable

                return FoundUberCard(actualButton, cardRoot)

            }

        }

        return null

    }

    private fun collectScoredUberAcceptButtons(

        node: AccessibilityNodeInfo?,

        candidates: MutableList<Pair<AccessibilityNodeInfo, Int>>,

        depth: Int = 0

    ) {

        if (node == null || isForbiddenWidget(node) || depth > 25) return

        val viewId = node.viewIdResourceName?.toString() ?: ""

        val text = node.text?.toString()?.trim() ?: ""

        val desc = node.contentDescription?.toString()?.trim() ?: ""

        val combined = "$viewId $text $desc".lowercase(Locale.ROOT)

        val isRejectNode = UBER_REJECT_TAGS.any { combined.contains(it) } ||

                combined.contains("reject") || combined.contains("decline") || combined.contains("cancel") ||

                combined.contains("matching in progress") || combined.contains("another driver matched") ||

                combined.contains("matches near you") || combined.contains("finding matches") ||

                combined.contains("matched to this request") || combined.contains("matched to trip")

        if (!isRejectNode) {

            var baseScore = 0

            if (combined.contains("dispatch_accept_tap") || combined.contains("6e4e9f88")) {

                baseScore = 80

            } else if (combined.contains("9ab7a07a") || combined.contains("10f0a3a5")) {

                baseScore = 70

            } else if (combined.contains("job_board_offer_accept_tap")) {

                baseScore = 60

            } else if (UBER_ACCEPT_IDS.any { combined.contains(it) }) {

                baseScore = 50

            } else if (UBER_ACCEPT_TEXTS.any { combined.contains(it) } || (combined.length < 35 && UBER_ACCEPT_TEXT_SET.any { combined.contains(it) })) {

                baseScore = 40

            }

            if (baseScore > 0) {

                var totalScore = baseScore

                if (node.isEnabled) totalScore += 1000

                if (node.isVisibleToUser) totalScore += 200

                if (node.isClickable) totalScore += 50

                candidates.add(Pair(node, totalScore))

            }

        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            collectScoredUberAcceptButtons(child, candidates, depth + 1)

        }

    }

    /**

     * 100% Lapak Executor.click hardenedAccept execution for Uber:

     * - Accurate button touch point: computes centroid directly from active button bounds without

     *   overriding x with screen center or clamping y into the lower screen half.

     * - Pure stationary gesture holds: calibrated-tap (85ms hold) without synthetic ACTION_CLICK.

     *   (Synthetic ACTION_CLICK resets/cancels touch responders in React Native/Helix on Uber).

     * - ZERO coordinate drift: uses Path.moveTo(x, y) without lineTo.

     * - Concurrent Shizuku privileged shell stream input tap (0ms + 40ms follow-up).

     * - Lapak Recheck Follow-up (250ms) if offer button remains active and visible.

     */

    private fun executeUberHardenedTap(

        btnNode: AccessibilityNodeInfo,

        packageName: String,

        details: RideDetails,

        cardRoot: AccessibilityNodeInfo?

    ) {

        if (isIgnoredPackage(packageName) || isForbiddenWidget(btnNode)) return

        val activeBtn = btnNode

        val rect = Rect()

        activeBtn.getBoundsInScreen(rect)

        val screenH = resources.displayMetrics.heightPixels

        val screenW = resources.displayMetrics.widthPixels

        // Zero-blocking bounds resolution

        val x: Int

        val y: Int

        if (rect.isEmpty || rect.width() <= 0 || rect.height() <= 0 || rect.top >= screenH || rect.bottom <= 0) {

            // Default to Uber bottom action card pill coordinates if bounds uninitialized

            x = screenW / 2

            y = (screenH * 0.85f).toInt()

        } else {

            // Lapak 1:1 rule: Use the actual button centroid directly.

            // Do NOT force x to screen center and do NOT clamp y into lower screen half!

            // Trip Radar match buttons can appear in upper or middle screen cards!

            x = rect.centerX().coerceIn(20, screenW - 20)

            y = rect.centerY().coerceIn(20, screenH - 20)

        }

        Log.i(TAG, "🎯 [UBER FAST STRIKE] Tapping ($x, $y) Bounds=$rect Fare=₹${details.fare}")

        val now = System.currentTimeMillis()

        globalCooldownUntil = now + 100L

        packageBusyUntilMs[packageName] = now + 120L

        // Lapak Rule: NEVER click parentClickable on Uber!

        // Clicking parentClickable clicks the entire card container and collapses Trip Radar without matching.

        // Try native action click ONLY on the specific active button itself:

        try {

            if (activeBtn.isClickable && !isForbiddenWidget(activeBtn)) {

                activeBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            }

        } catch (ignored: Exception) {}

        // 1. Immediate Shizuku shell tap stream (T = 0ms)

        dispatchPariTap(x, y)

        // 3. Pure stationary gesture hold (85ms calibrated)

        dispatchStationaryGestureHold(x.toFloat(), y.toFloat(), 85L)

        // 4. Animation Catch Follow-up (T = 40ms) via Shell stream

        handler.postDelayed({

            try {

                dispatchPariTap(x, y)

            } catch (e: Exception) {}

        }, 40L)

        // 5. Recheck Follow-up (T = 250ms) if offer button still present on screen

        handler.postDelayed({

            try {

                if (activeBtn.refresh() && activeBtn.isVisibleToUser && activeBtn.isEnabled) {

                    val r2 = Rect()

                    activeBtn.getBoundsInScreen(r2)

                    if (!r2.isEmpty) {

                        dispatchStationaryGestureHold(r2.centerX().toFloat(), r2.centerY().toFloat(), 110L)

                        dispatchPariTap(r2.centerX(), r2.centerY())

                    }

                }

            } catch (e: Exception) {}

        }, 250L)

        speak("Accepting Uber ride...")

        armPendingAcceptance(packageName, details, if (shizukuShell != null) "pari_hardened" else "accessibility_hardened")

    }

    private fun dispatchStationaryGestureHold(x: Float, y: Float, holdMs: Long) {

        try {

            val path = Path().apply {

                moveTo(x, y)

                // ⚠️ CRITICAL LAPAK RULE: Pure stationary touch point! NO lineTo(x+1, y+1)

            }

            val stroke = GestureDescription.StrokeDescription(path, 0L, holdMs)

            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, null, null)

        } catch (e: Exception) {

            Log.e(TAG, "Error in dispatchStationaryGestureHold: ${e.message}")

        }

    }

    private fun findNammaYatriAcceptButtons(

        node: AccessibilityNodeInfo?,

        results: MutableList<AccessibilityNodeInfo>

    ) {

        if (node == null || isForbiddenWidget(node)) return

        // Fast Path 1: Direct C++ View ID lookups

        for (id in NAMMA_ACCEPT_IDS) {

            val nodes = try { node.findAccessibilityNodeInfosByViewId(id) } catch (e: Exception) { null }

            if (!nodes.isNullOrEmpty()) {

                for (n in nodes) {

                    if (n.isVisibleToUser && !isForbiddenWidget(n) && !results.contains(n)) {

                        results.add(n)

                    }

                }

                if (results.isNotEmpty()) return

            }

        }

        // Fast Path 2: Direct C++ text lookups

        for (textVariant in NAMMA_ACCEPT_TEXTS) {

            val nodes = try { node.findAccessibilityNodeInfosByText(textVariant) } catch (e: Exception) { null }

            if (!nodes.isNullOrEmpty()) {

                for (n in nodes) {

                    if (n.isVisibleToUser && !isForbiddenWidget(n) && !results.contains(n)) {

                        results.add(n)

                    }

                }

                if (results.isNotEmpty()) return

            }

        }

        // Fallback: Pruned recursive traversal

        findNammaYatriAcceptButtonsRecursive(node, results, 0)

    }

    private fun findNammaYatriAcceptButtonsRecursive(

        node: AccessibilityNodeInfo?,

        results: MutableList<AccessibilityNodeInfo>,

        depth: Int = 0

    ) {

        if (node == null || isForbiddenWidget(node) || depth > 25) return

        val viewId = node.viewIdResourceName?.toString() ?: ""

        if (viewId.isNotEmpty()) {

            val shortId = viewId.substringAfterLast(":id/")

            val isNammaId = NAMMA_ACCEPT_ID_SET.contains(viewId) || NAMMA_ACCEPT_ID_SET.contains(shortId)

            if (isNammaId && !results.contains(node)) {

                results.add(node)

                return // Branch pruning

            }

        }

        val text = node.text?.toString()?.trim() ?: ""

        val desc = node.contentDescription?.toString()?.trim() ?: ""

        val combined = "$text $desc".trim().lowercase(Locale.ROOT)

        if (combined.isNotEmpty()) {

            if (NAMMA_ACCEPT_TEXT_SET.contains(combined) || (combined.length < 35 && NAMMA_ACCEPT_TEXT_SET.any { combined.contains(it) })) {

                if (!results.contains(node)) {

                    results.add(node)

                    return // Branch pruning

                }

            }

        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            findNammaYatriAcceptButtonsRecursive(child, results, depth + 1)

            if (results.size >= 3) return

        }

    }

    /**

     * Dedicated Red Taxi Accept Button Finder (DHP ep.java mode 2).

     */

    private fun findRedTaxiAcceptButtons(

        node: AccessibilityNodeInfo?,

        results: MutableList<AccessibilityNodeInfo>

    ) {

        if (node == null || isForbiddenWidget(node)) return

        for (id in RED_TAXI_ACCEPT_IDS) {

            val nodes = try { node.findAccessibilityNodeInfosByViewId(id) } catch (e: Exception) { null }

            if (!nodes.isNullOrEmpty()) {

                for (n in nodes) {

                    if (n.isVisibleToUser && !isForbiddenWidget(n) && !results.contains(n)) {

                        results.add(n)

                    }

                }

                if (results.isNotEmpty()) return

            }

        }

        for (textVariant in RED_TAXI_ACCEPT_TEXTS) {

            val nodes = try { node.findAccessibilityNodeInfosByText(textVariant) } catch (e: Exception) { null }

            if (!nodes.isNullOrEmpty()) {

                for (n in nodes) {

                    if (n.isVisibleToUser && !isForbiddenWidget(n) && !results.contains(n)) {

                        results.add(n)

                    }

                }

                if (results.isNotEmpty()) return

            }

        }

        findRedTaxiAcceptButtonsRecursive(node, results, 0)

    }

    private fun findRedTaxiAcceptButtonsRecursive(

        node: AccessibilityNodeInfo?,

        results: MutableList<AccessibilityNodeInfo>,

        depth: Int = 0

    ) {

        if (node == null || isForbiddenWidget(node) || depth > 25) return

        val viewId = node.viewIdResourceName?.toString() ?: ""

        if (viewId.isNotEmpty()) {

            val shortId = viewId.substringAfterLast(":id/")

            val isRedTaxiId = RED_TAXI_ACCEPT_ID_SET.contains(viewId) || RED_TAXI_ACCEPT_ID_SET.contains(shortId)

            if (isRedTaxiId && !results.contains(node)) {

                results.add(node)

                return

            }

        }

        val text = node.text?.toString()?.trim() ?: ""

        val desc = node.contentDescription?.toString()?.trim() ?: ""

        val combined = "$text $desc".trim().lowercase(Locale.ROOT)

        if (combined.isNotEmpty()) {

            if (RED_TAXI_ACCEPT_TEXT_SET.contains(combined) || (combined.length < 35 && RED_TAXI_ACCEPT_TEXT_SET.any { combined.contains(it) })) {

                if (!results.contains(node)) {

                    results.add(node)

                    return

                }

            }

        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            findRedTaxiAcceptButtonsRecursive(child, results, depth + 1)

            if (results.size >= 3) return

        }

    }

    /**

     * Dedicated Bharat Taxi Accept Button Finder (DHP ep.java mode 0).

     */

    private fun findBharatTaxiAcceptButtons(

        node: AccessibilityNodeInfo?,

        results: MutableList<AccessibilityNodeInfo>

    ) {

        if (node == null || isForbiddenWidget(node)) return

        for (id in BHARAT_TAXI_ACCEPT_IDS) {

            val nodes = try { node.findAccessibilityNodeInfosByViewId(id) } catch (e: Exception) { null }

            if (!nodes.isNullOrEmpty()) {

                for (n in nodes) {

                    if (n.isVisibleToUser && !isForbiddenWidget(n) && !results.contains(n)) {

                        results.add(n)

                    }

                }

                if (results.isNotEmpty()) return

            }

        }

        for (textVariant in BHARAT_TAXI_ACCEPT_TEXTS) {

            val nodes = try { node.findAccessibilityNodeInfosByText(textVariant) } catch (e: Exception) { null }

            if (!nodes.isNullOrEmpty()) {

                for (n in nodes) {

                    if (n.isVisibleToUser && !isForbiddenWidget(n) && !results.contains(n)) {

                        results.add(n)

                    }

                }

                if (results.isNotEmpty()) return

            }

        }

        findBharatTaxiAcceptButtonsRecursive(node, results, 0)

    }

    private fun findBharatTaxiAcceptButtonsRecursive(

        node: AccessibilityNodeInfo?,

        results: MutableList<AccessibilityNodeInfo>,

        depth: Int = 0

    ) {

        if (node == null || isForbiddenWidget(node) || depth > 25) return

        val viewId = node.viewIdResourceName?.toString() ?: ""

        if (viewId.isNotEmpty()) {

            val shortId = viewId.substringAfterLast(":id/")

            val isBharatId = BHARAT_TAXI_ACCEPT_ID_SET.contains(viewId) || BHARAT_TAXI_ACCEPT_ID_SET.contains(shortId)

            if (isBharatId && !results.contains(node)) {

                results.add(node)

                return

            }

        }

        val text = node.text?.toString()?.trim() ?: ""

        val desc = node.contentDescription?.toString()?.trim() ?: ""

        val combined = "$text $desc".trim().lowercase(Locale.ROOT)

        if (combined.isNotEmpty()) {

            if (BHARAT_TAXI_ACCEPT_TEXT_SET.contains(combined) || (combined.length < 35 && BHARAT_TAXI_ACCEPT_TEXT_SET.any { combined.contains(it) })) {

                if (!results.contains(node)) {

                    results.add(node)

                    return

                }

            }

        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            findBharatTaxiAcceptButtonsRecursive(child, results, depth + 1)

            if (results.size >= 3) return

        }

    }

    private fun findGeneralAcceptButtons(

        node: AccessibilityNodeInfo?,

        appInfo: AppPackage?,

        results: MutableList<AccessibilityNodeInfo>

    ) {

        if (node == null || isForbiddenWidget(node)) return

        val customIds = (appInfo?.acceptButtonIds ?: emptyList()) + GENERAL_ACCEPT_IDS

        for (id in customIds) {

            val nodes = try { node.findAccessibilityNodeInfosByViewId(id) } catch (e: Exception) { null }

            if (!nodes.isNullOrEmpty()) {

                for (n in nodes) {

                    if (n.isVisibleToUser && !isForbiddenWidget(n) && !results.contains(n)) {

                        results.add(n)

                    }

                }

                if (results.isNotEmpty()) return

            }

        }

        val customTexts = (appInfo?.acceptButtonTexts ?: emptyList()) + ACCEPT_TEXTS.toList()

        for (textVariant in customTexts) {

            val nodes = try { node.findAccessibilityNodeInfosByText(textVariant) } catch (e: Exception) { null }

            if (!nodes.isNullOrEmpty()) {

                for (n in nodes) {

                    if (n.isVisibleToUser && !isForbiddenWidget(n) && !results.contains(n)) {

                        results.add(n)

                    }

                }

                if (results.isNotEmpty()) return

            }

        }

        val customIdSet = (appInfo?.acceptButtonIds?.toHashSet() ?: emptySet()) + GENERAL_ACCEPT_ID_SET

        val customTextSet = (appInfo?.acceptButtonTexts?.map { it.lowercase(Locale.ROOT) }?.toHashSet() ?: emptySet()) + ACCEPT_TEXT_SET

        findGeneralAcceptButtonsRecursive(node, customIdSet, customTextSet, results, 0)

    }

    private fun findGeneralAcceptButtonsRecursive(

        node: AccessibilityNodeInfo?,

        idSet: Set<String>,

        textSet: Set<String>,

        results: MutableList<AccessibilityNodeInfo>,

        depth: Int = 0

    ) {

        if (node == null || isForbiddenWidget(node) || depth > 20) return

        val viewId = node.viewIdResourceName?.toString() ?: ""

        if (viewId.isNotEmpty()) {

            val shortId = viewId.substringAfterLast(":id/")

            val isMatch = idSet.contains(viewId) || idSet.contains(shortId)

            if (isMatch && !results.contains(node)) {

                results.add(node)

                return // Branch pruning

            }

        }

        val text = node.text?.toString()?.trim() ?: ""

        val desc = node.contentDescription?.toString()?.trim() ?: ""

        val combined = "$text $desc".trim().lowercase(Locale.ROOT)

        if (combined.isNotEmpty()) {

            if (textSet.contains(combined) || (combined.length < 35 && textSet.any { combined.contains(it) })) {

                if (!results.contains(node)) {

                    results.add(node)

                    return // Branch pruning

                }

            }

        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            findGeneralAcceptButtonsRecursive(child, idSet, textSet, results, depth + 1)

            if (results.size >= 3) return

        }

    }

    // ── Tap & Swipe Execution Engines ──

    private fun executeTapAction(

        btnNode: AccessibilityNodeInfo,

        packageName: String,

        details: RideDetails,

        precomputedBounds: Rect? = null,

        isSlideAction: Boolean = false

    ) {

        if (isIgnoredPackage(packageName) || isForbiddenWidget(btnNode)) {

            Log.w(TAG, "⚠️ Blocked tap execution on ignored package or forbidden widget: $packageName")

            return

        }

        val (targetNode, rect) = if (precomputedBounds != null) {

            Pair(btnNode, precomputedBounds)

        } else {

            getClickTargetNodeAndBounds(btnNode)

        }

        if (isForbiddenWidget(targetNode)) {

            Log.w(TAG, "⚠️ Blocked tap execution on forbidden target node widget")

            return

        }

        // 🛡️ Final Hard Firewall: Verify button itself does NOT contain Re-centre or Navigation commands

        val btnRaw = normalizeTextHyphens("${extractNodeAndDescendantText(btnNode)} ${extractNodeAndDescendantText(targetNode)}".lowercase(Locale.ROOT))

        if (btnRaw.contains("re-center") || btnRaw.contains("recenter") || btnRaw.contains("re-centre") ||

            btnRaw.contains("recentre") || btnRaw.contains("re center") || btnRaw.contains("re centre") ||

            btnRaw.contains("re_center") || btnRaw.contains("re_centre") ||

            btnRaw.contains("री-सेंटर") || btnRaw.contains("फिर से केंद्रित") ||

            btnRaw.contains("route options") || btnRaw.contains("exit navigation") || btnRaw.contains("stop navigation")

        ) {

            Log.w(TAG, "🛑 Blocked tap execution: Target is a Navigation/Re-centre button ($btnRaw)")

            return

        }

        // 🛡️ Final Hard Firewall: Active navigation check before physical or privileged touch

        val activeRoot = rootInActiveWindow

        if (activeRoot != null && !packageName.contains("uber") && isActiveNavigationScreen(activeRoot)) {

            Log.w(TAG, "🛑 Blocked tap execution: Active screen is turn-by-turn navigation")

            return

        }

        val screenW = resources.displayMetrics.widthPixels

        val screenH = resources.displayMetrics.heightPixels

        val x = if (rect.centerX() > 0) rect.centerX() else (screenW / 2)

        val y = if (rect.centerY() > 0) rect.centerY() else (screenH * 0.85f).toInt()

        // Slight human jitter ±10% within the button bounds to prevent duplicate touch dropping

        val jitterX = if (rect.width() > 40) (Math.random() * rect.width() * 0.2 - rect.width() * 0.1).toInt() else 0

        val jitterY = if (rect.height() > 40) (Math.random() * rect.height() * 0.2 - rect.height() * 0.1).toInt() else 0

        val finalX = (x + jitterX).coerceIn(10, screenW - 10)

        val finalY = (y + jitterY).coerceIn(10, screenH - 10)

        Log.i(TAG, "🎯 ACCEPTING RIDE ($packageName) at ($finalX, $finalY) Fare=₹${details.fare}")

        val now = System.currentTimeMillis()

        val isRapido = packageName.contains("rapido", ignoreCase = true) || packageName.contains("captain", ignoreCase = true) || packageName.contains("rr.android", ignoreCase = true)

        val cooldownMs = if (isRapido) 200L else 1200L

        globalCooldownUntil = now + 200L

        packageBusyUntilMs[packageName] = now + cooldownMs

        if (isSlideAction) {

            val startX = if (rect.width() > 100) (rect.left + (rect.height() / 2)).coerceAtLeast(50) else (screenW * 0.15f).toInt()

            val endX = if (rect.width() > 100) (rect.right - 50).coerceAtMost(screenW - 30) else (screenW * 0.88f).toInt()

            dispatchPariSwipe(startX, finalY, endX, finalY, 180)

            performGestureSwipe(startX.toFloat(), finalY.toFloat(), endX.toFloat(), finalY.toFloat(), 180L)

        } else {

            // Immediate T=0ms triple strike (Pari privileged stream + node click + gesture tap)

            performNodeClick(targetNode)

            dispatchPariTap(finalX, finalY)

            performGestureTap(finalX.toFloat(), finalY.toFloat())

            // Rapid follow-up background burst taps (35ms, 70ms, 105ms) via Pari persistent shell stream

            tapExecutor.execute {

                for (b in 1..3) {

                    try {

                        Thread.sleep(35L)

                        synchronized(shellLock) {

                            val out = shizukuOut

                            if (out != null) {

                                out.writeBytes("input tap $finalX $finalY\n")

                                out.flush()

                            }

                        }

                    } catch (e: Exception) {}

                }

            }

        }

        speak("Accepting ride...")

        armPendingAcceptance(packageName, details, if (shizukuShell != null) "pari" else "accessibility")

    }

    private fun executeDirectPariTap(

        x: Int,

        y: Int,

        packageName: String,

        details: RideDetails,

        isUberBlind: Boolean = false

    ) {

        if (isIgnoredPackage(packageName)) {

            Log.w(TAG, "⚠️ Blocked direct tap execution on ignored package: $packageName")

            return

        }

        val activePkg = rootInActiveWindow?.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

        if (activePkg.isNotEmpty() && isIgnoredPackage(activePkg)) {

            Log.w(TAG, "⚠️ Blocked direct tap execution over active ignored package: $activePkg")

            return

        }

        val hasRealOffer = details.fare != null || (details.distance != null && (details.fullText.contains("₹") || details.fullText.contains("accept") || details.fullText.contains("match")))

        if (!hasRealOffer) {

            Log.w(TAG, "🛑 Blocked direct tap: Details lack valid offer context (${details.fullText})")

            return

        }

        val now = System.currentTimeMillis()

        val cooldownMs = 150L

        globalCooldownUntil = now + 100L

        packageBusyUntilMs[packageName] = now + cooldownMs

        // Burst 1 (T = 0ms immediate strike)

        dispatchPariTap(x, y)

        if (shizukuShell == null) {

            performGestureTap(x.toFloat(), y.toFloat())

        }

        if (isUberBlind) {

            val screenH = resources.displayMetrics.heightPixels

            val yAlt1 = (screenH * 0.78f).toInt()

            val yAlt2 = (screenH * 0.90f).toInt()

            dispatchPariTap(x, yAlt1)

            dispatchPariTap(x, yAlt2)

        }

        // Rapid follow-up background burst taps (50ms, 100ms) on Dispatchers.Default with gesture fallback

        serviceScope.launch(Dispatchers.Default) {

            val burstDelays = longArrayOf(50L, 100L)

            for (d in burstDelays) {

                try {

                    kotlinx.coroutines.delay(d)

                    var shellSent = false

                    synchronized(shellLock) {

                        val out = shizukuOut

                        if (out != null) {

                            out.writeBytes("input tap $x $y\n")

                            out.flush()

                            shellSent = true

                        }

                    }

                    if (!shellSent) {

                        handler.post { performGestureTap(x.toFloat(), y.toFloat()) }

                    }

                } catch (e: Exception) {}

            }

        }

        speak("Accepting ride...")

        armPendingAcceptance(packageName, details, "pari")

    }

    private fun executeSlideAction(

        btnNode: AccessibilityNodeInfo,

        packageName: String,

        details: RideDetails

    ) {

        val rect = Rect()

        btnNode.getBoundsInScreen(rect)

        val displayMetrics = resources.displayMetrics

        val screenW = displayMetrics.widthPixels

        val screenH = displayMetrics.heightPixels

        val startX = if (rect.width() > 100) (rect.left + (rect.height() / 2)).coerceAtLeast(50) else (screenW * 0.15f).toInt()

        val endX = if (rect.width() > 100) (rect.right - 50).coerceAtMost(screenW - 30) else (screenW * 0.88f).toInt()

        val y = if (rect.centerY() > 0) rect.centerY() else (screenH * 0.85f).toInt()

        Log.i(TAG, "🚚 Executing Slide-to-Accept from ($startX, $y) to ($endX, $y)")

        val now = System.currentTimeMillis()

        globalCooldownUntil = now + 200L

        packageBusyUntilMs[packageName] = now + 600L

        dispatchPariSwipe(startX, y, endX, y, 180)

        performGestureSwipe(startX.toFloat(), y.toFloat(), endX.toFloat(), y.toFloat(), 180L)

        handler.postDelayed({

            dispatchPariSwipe(startX, y, endX, y, 180)

        }, 200L)

        armPendingAcceptance(packageName, details, if (shizukuShell != null) "pari" else "accessibility")

    }

    // ── Pari Direct Shell Injection (<0.5ms with Persistent Executor) ──

    private fun dispatchPariTap(x: Int, y: Int) {

        if (x <= 0 || y <= 0) return

        tapExecutor.execute {

            synchronized(shellLock) {

                try {

                    ensurePariShell()

                    val out = shizukuOut

                    if (out != null) {

                        out.writeBytes("input tap $x $y\n")

                        out.flush()

                    }

                } catch (e: Exception) {

                    resetPariShell()

                }

            }

        }

    }

    private fun dispatchPariSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int) {

        if (startX <= 0 || startY <= 0 || endX <= 0 || endY <= 0) return

        tapExecutor.execute {

            synchronized(shellLock) {

                try {

                    ensurePariShell()

                    val out = shizukuOut

                    if (out != null) {

                        out.writeBytes("input swipe $startX $startY $endX $endY $durationMs\n")

                        out.flush()

                    }

                } catch (e: Exception) {

                    resetPariShell()

                }

            }

        }

    }

    private fun ensurePariShell() {

        if (shizukuShell != null && shizukuOut != null) return

        try {

            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {

                return

            }

            val newProcessMethod: Method = try {

                Shizuku::class.java.methods.firstOrNull {

                    it.name == "newProcess" && it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == Array<String>::class.java

                }?.apply { isAccessible = true }

                    ?: Shizuku::class.java.getDeclaredMethod(

                        "newProcess",

                        Array<String>::class.java,

                        Array<String>::class.java,

                        String::class.java

                    ).apply { isAccessible = true }

            } catch (e: Exception) {

                return

            }

            val proc = newProcessMethod.invoke(

                null,

                arrayOf("sh"),

                null,

                null

            ) as Process

            shizukuShell = proc

            shizukuOut = DataOutputStream(proc.outputStream)

            // Background drainers for stdout and stderr to prevent pipe buffer blocks

            Thread {

                try {

                    val reader = BufferedReader(InputStreamReader(proc.inputStream))

                    while (reader.readLine() != null) { /* drain */ }

                } catch (e: Exception) {}

            }.apply { isDaemon = true; start() }

            Thread {

                try {

                    val reader = BufferedReader(InputStreamReader(proc.errorStream))

                    while (reader.readLine() != null) { /* drain */ }

                } catch (e: Exception) {}

            }.apply { isDaemon = true; start() }

            Log.i(TAG, "⚡ Pari Shell Connected")

        } catch (e: Exception) {

            shizukuShell = null

            shizukuOut = null

        }

    }

    private fun resetPariShell() {

        try { shizukuOut?.close() } catch (e: Exception) {}

        try { shizukuShell?.destroy() } catch (e: Exception) {}

        shizukuOut = null

        shizukuShell = null

    }

    private fun clickNodeFast(node: AccessibilityNodeInfo?): Boolean {

        if (node == null || isForbiddenWidget(node)) return false

        try {

            if (node.isClickable && !isForbiddenWidget(node) && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {

                return true

            }

            if (!isForbiddenWidget(node) && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {

                return true

            }

        } catch (e: Exception) {}

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            try {

                if (!isForbiddenWidget(child) && child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {

                    return true

                }

            } catch (e: Exception) {}

        }

        var parent = node.parent

        var depth = 0

        val maxContainerH = (resources.displayMetrics.heightPixels * 0.94f).toInt().coerceAtLeast(300)

        while (parent != null && depth < 8) {

            if (parent.isClickable && !isForbiddenWidget(parent)) {

                val pRect = Rect()

                parent.getBoundsInScreen(pRect)

                if (pRect.height() in 20..maxContainerH) {

                    try {

                        if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {

                            return true

                        }

                    } catch (e: Exception) {}

                }

            }

            parent = parent.parent

            depth++

        }

        return false

    }

    private fun performNodeClick(node: AccessibilityNodeInfo?): Boolean {

        return clickNodeFast(node)

    }

    private fun executeRapidoBurstStrike(
        btnNode: AccessibilityNodeInfo,
        packageName: String,
        details: RideDetails
    ) {
        val now = System.currentTimeMillis()
        globalCooldownUntil = now + 100L
        packageBusyUntilMs[packageName] = now + 100L

        val btnText = "${btnNode.text ?: ""} ${btnNode.contentDescription ?: ""}".lowercase(Locale.ROOT)
        val viewIdLower = btnNode.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val className = btnNode.className?.toString() ?: ""

        val isSlideAction = btnText.contains("swipe") || btnText.contains("slide") ||
                viewIdLower.contains("slide") || viewIdLower.contains("swipe") ||
                className.contains("SeekBar", ignoreCase = true)

        if (isSlideAction) {
            executeSlideAction(btnNode, packageName, details)
            return
        }

        Log.i(TAG, "⚡ [RAPIDO SWB BURST STRIKE] Firing pure clickNodeFast on leaf button Fare=₹${details.fare}")

        val (targetNode, targetBounds) = getClickTargetNodeAndBounds(btnNode)
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val repeatX = if (targetBounds.centerX() > 0) targetBounds.centerX().coerceIn(10, screenW - 10) else (screenW / 2)
        val repeatY = if (targetBounds.centerY() > 0) targetBounds.centerY().coerceIn(10, screenH - 10) else (screenH * 0.85f).toInt()

        val safeTarget = try { AccessibilityNodeInfo.obtain(targetNode) } catch (e: Exception) { null }
        val safeBtn = try { AccessibilityNodeInfo.obtain(btnNode) } catch (e: Exception) { null }

        // Burst 1 (T = 0ms immediate virtual click - primary on targetNode, fallback to btnNode)
        var clicked = if (safeTarget != null) clickNodeFast(safeTarget) else clickNodeFast(targetNode)
        if (!clicked && safeBtn != null) {
            clicked = clickNodeFast(safeBtn)
        }

        // SWB Delays: 50ms, 100ms (Total 3 bursts at 0ms, 50ms, 100ms)
        val RAPIDO_DELAYS = longArrayOf(50L, 100L)
        for (delay in RAPIDO_DELAYS) {
            handler.postDelayed({
                try {
                    if (safeTarget != null) clickNodeFast(safeTarget) else if (safeBtn != null) clickNodeFast(safeBtn)
                } catch (e: Exception) {}
            }, delay)
        }

        handler.postDelayed({
            try { safeTarget?.recycle() } catch (e: Exception) {}
            try { safeBtn?.recycle() } catch (e: Exception) {}
        }, 300L)

        // Fallback: Only if pure ACTION_CLICK returns false across all hierarchy levels
        if (!clicked && repeatX > 0 && repeatY > 0) {
            dispatchPariTap(repeatX, repeatY)
            performGestureTap(repeatX.toFloat(), repeatY.toFloat())
        }

        speak("Accepting ride...")
        armPendingAcceptance(packageName, details, "accessibility")
    }

    private fun executeOlaPureShellStrike(

        btnNode: AccessibilityNodeInfo,

        packageName: String,

        details: RideDetails,

        precalculatedX: Int = -1,

        precalculatedY: Int = -1

    ) {

        val now = System.currentTimeMillis()

        lastOlaClickTime = now

        globalCooldownUntil = now + 100L

        packageBusyUntilMs[packageName] = now + 100L

        val screenW = resources.displayMetrics.widthPixels

        val screenH = resources.displayMetrics.heightPixels

        val (_, targetBounds) = getClickTargetNodeAndBounds(btnNode)

        val finalX: Int

        val finalY: Int

        if (precalculatedX > 0 && precalculatedY > 0) {

            finalX = precalculatedX.coerceIn(10, screenW - 10)

            finalY = precalculatedY.coerceIn(10, screenH - 10)

        } else {

            val x = if (targetBounds.centerX() > 0) targetBounds.centerX() else (screenW / 2)

            val y = if (targetBounds.centerY() > 0) targetBounds.centerY() else (screenH * 0.85f).toInt()

            finalX = x.coerceIn(10, screenW - 10)

            finalY = y.coerceIn(10, screenH - 10)

        }

        val isShizukuAvailable = shizukuShell != null && shizukuOut != null

        // ═════════════════════════════════════════════════════════════════════════

        // 1:1 RIDE CLICKER OG STRATEGY 1 (PRIMARY): Privileged 3-Burst Shell Tap

        // If Shizuku shell is available, fire 3-burst shell tap and RETURN IMMEDIATELY.

        // No concurrent dual-strike with ACTION_CLICK!

        // ═════════════════════════════════════════════════════════════════════════

        if (isShizukuAvailable) {

            Log.i(TAG, "⚡ [OLA PRIMARY: 3-BURST SHELL TAP] Firing at ($finalX, $finalY) Fare=₹${details.fare}")

            dispatchPariTap(finalX, finalY)

            val OLA_DELAYS = longArrayOf(50L, 100L)

            for (delay in OLA_DELAYS) {

                handler.postDelayed({

                    try {

                        val jx = (finalX + (Math.random() * 20 - 10)).toInt()

                        val jy = (finalY + (Math.random() * 20 - 10)).toInt()

                        synchronized(shellLock) {

                            val out = shizukuOut

                            if (out != null) {

                                out.writeBytes("input tap $jx $jy\n")

                                out.flush()

                            }

                        }

                    } catch (e: Exception) {}

                }, delay)

            }

            speak("Accepting ride...")

            armPendingAcceptance(packageName, details, "pari")

            return

        }

        // ═════════════════════════════════════════════════════════════════════════

        // 1:1 RIDE CLICKER OG STRATEGY 2 (FALLBACK 1): Native Accessibility ACTION_CLICK

        // If Shizuku is not available, fire native ACTION_CLICK. If it succeeds, RETURN.

        // ═════════════════════════════════════════════════════════════════════════

        val actionClicked = clickNodeFast(btnNode)

        if (actionClicked) {

            Log.i(TAG, "⚡ [OLA FALLBACK 1: ACTION_CLICK SUCCESS] on $packageName")

            speak("Accepting ride...")

            armPendingAcceptance(packageName, details, "accessibility")

            return

        }

        // ═════════════════════════════════════════════════════════════════════════

        // 1:1 RIDE CLICKER OG STRATEGY 3 (FALLBACK 2): Rapid 3-Burst Gesture Tap

        // Fired ONLY if both shell tap and ACTION_CLICK failed.

        // ═════════════════════════════════════════════════════════════════════════

        Log.i(TAG, "⚡ [OLA FALLBACK 2: 3-BURST GESTURE] Dispatching at ($finalX, $finalY)")

        performGestureTap(finalX.toFloat(), finalY.toFloat())

        val OLA_DELAYS = longArrayOf(50L, 100L)

        for (delay in OLA_DELAYS) {

            handler.postDelayed({

                try {

                    val jx = (finalX + (Math.random() * 20 - 10)).toFloat()

                    val jy = (finalY + (Math.random() * 20 - 10)).toFloat()

                    performGestureTap(jx, jy)

                } catch (e: Exception) {}

            }, delay)

        }

        speak("Accepting ride...")

        armPendingAcceptance(packageName, details, "accessibility")

    }

    private fun executeOlaPreArmedShellStrike(

        x: Int,

        y: Int,

        packageName: String,

        details: RideDetails

    ) {

        val now = System.currentTimeMillis()

        lastOlaClickTime = now

        globalCooldownUntil = now + 100L

        packageBusyUntilMs[packageName] = now + 100L

        val screenW = resources.displayMetrics.widthPixels

        val screenH = resources.displayMetrics.heightPixels

        val finalX = x.coerceIn(10, screenW - 10)

        val finalY = y.coerceIn(10, screenH - 10)

        val isShizukuAvailable = shizukuShell != null && shizukuOut != null

        // 1:1 RIDE CLICKER OG: If Shell available -> 3-burst shell tap and return

        if (isShizukuAvailable) {

            Log.i(TAG, "⚡ [OLA PRE-ARMED 3-BURST SHELL TAP] Firing at ($finalX, $finalY) Fare=₹${details.fare}")

            dispatchPariTap(finalX, finalY)

            val OLA_DELAYS = longArrayOf(50L, 100L)

            for (delay in OLA_DELAYS) {

                handler.postDelayed({

                    try {

                        val jx = (finalX + (Math.random() * 20 - 10)).toInt()

                        val jy = (finalY + (Math.random() * 20 - 10)).toInt()

                        synchronized(shellLock) {

                            val out = shizukuOut

                            if (out != null) {

                                out.writeBytes("input tap $jx $jy\n")

                                out.flush()

                            }

                        }

                    } catch (e: Exception) {}

                }, delay)

            }

            speak("Accepting ride...")

            armPendingAcceptance(packageName, details, "pari")

            return

        }

        // Fallback: 3-burst gesture tap

        Log.i(TAG, "⚡ [OLA PRE-ARMED 3-BURST GESTURE] Firing at ($finalX, $finalY) Fare=₹${details.fare}")

        performGestureTap(finalX.toFloat(), finalY.toFloat())

        val OLA_DELAYS = longArrayOf(50L, 100L)

        for (delay in OLA_DELAYS) {

            handler.postDelayed({

                try {

                    val jx = (finalX + (Math.random() * 20 - 10)).toFloat()

                    val jy = (finalY + (Math.random() * 20 - 10)).toFloat()

                    performGestureTap(jx, jy)

                } catch (e: Exception) {}

            }, delay)

        }

        speak("Accepting ride...")

        armPendingAcceptance(packageName, details, "accessibility")

    }

    private fun performGestureTap(x: Float, y: Float) {

        val path = Path().apply {

            moveTo(x, y)

        }

        val gesture = GestureDescription.Builder()

            .addStroke(GestureDescription.StrokeDescription(path, 0L, 3L))

            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {}, null)

    }

    private fun performGestureSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 180L) {

        val path = Path().apply {

            moveTo(startX, startY)

            lineTo(endX, endY)

        }

        val gesture = GestureDescription.Builder()

            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))

            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {}, null)

    }

    private fun getClickTargetNodeAndBounds(node: AccessibilityNodeInfo): Pair<AccessibilityNodeInfo, Rect> {

        val rect = Rect()

        val displayMetrics = resources.displayMetrics

        val screenW = displayMetrics.widthPixels

        val screenH = displayMetrics.heightPixels

        val maxButtonWidth = (screenW * 0.98f).toInt().coerceAtLeast(900)

        val maxButtonHeight = (screenH * 0.25f).toInt().coerceAtLeast(300)

        // 1. If the node itself is clickable and within valid button bounds, use it!

        if (node.isClickable && !isForbiddenWidget(node)) {

            node.getBoundsInScreen(rect)

            if (rect.width() in 20..maxButtonWidth && rect.height() in 20..maxButtonHeight && rect.centerY() > 0) {

                return Pair(node, rect)

            }

        }

        // 2. Climb up parent chain up to 8 levels to find the nearest clickable button container

        var current: AccessibilityNodeInfo? = node

        var depth = 0

        while (current != null && depth < 8) {

            if (current.isClickable && !isForbiddenWidget(current)) {

                current.getBoundsInScreen(rect)

                if (rect.width() in 20..maxButtonWidth && rect.height() in 20..maxButtonHeight && rect.centerY() > 0) {

                    return Pair(current, rect)

                }

            }

            current = current.parent

            depth++

        }

        // 3. Fallback for Dialog/Popup Cards: Climb to find nearest clickable container up to 94% screen height

        current = node

        depth = 0

        val maxCardHeight = (screenH * 0.94f).toInt()

        while (current != null && depth < 8) {

            if (current.isClickable && !isForbiddenWidget(current)) {

                current.getBoundsInScreen(rect)

                if (rect.width() in 20..screenW && rect.height() in 20..maxCardHeight && rect.centerY() > 0) {

                    return Pair(current, rect)

                }

            }

            current = current.parent

            depth++

        }

        node.getBoundsInScreen(rect)

        return Pair(node, rect)

    }

    private fun getIndividualRideCard(node: AccessibilityNodeInfo): AccessibilityNodeInfo {

        try {

            val screenH = resources.displayMetrics.heightPixels

            val maxCardHeight = (screenH * 0.94f).toInt()

            val r = Rect()

            var current: AccessibilityNodeInfo = node

            var parent = node.parent

            var depth = 0

            while (parent != null && depth < 15) {

                val className = parent.className?.toString() ?: ""

                parent.getBoundsInScreen(r)

                if (className.contains("RecyclerView", ignoreCase = true) ||

                    className.contains("ListView", ignoreCase = true) ||

                    className.contains("ViewPager", ignoreCase = true) ||

                    className.contains("DecorView", ignoreCase = true) ||

                    (r.height() > maxCardHeight && depth > 0)

                ) {

                    return current

                }

                current = parent

                parent = parent.parent

                depth++

            }

            return current

        } catch (e: Exception) {

            return node

        }

    }

    private fun collectLeafTexts(node: AccessibilityNodeInfo?, results: MutableList<String>, depth: Int = 0, seen: MutableSet<String>? = null) {

        if (node == null || depth > 20) return

        val seenSet = seen ?: HashSet<String>().also { s -> s.addAll(results) }

        val text = node.text?.toString()?.trim()

        val desc = node.contentDescription?.toString()?.trim()

        if (!text.isNullOrEmpty() && seenSet.add(text)) {

            results.add(text)

        }

        if (!desc.isNullOrEmpty() && desc != text && seenSet.add(desc)) {

            results.add(desc)

        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            try {

                collectLeafTexts(child, results, depth + 1, seenSet)

            } finally {

                recycleNode(child)

            }

        }

    }

    @Suppress("DEPRECATION")

    private fun recycleNode(node: AccessibilityNodeInfo?) {

        // No-op: Framework manages node pools automatically; manual recycling causes crashes on Android 10-14

    }

    private fun recycleList(list: List<AccessibilityNodeInfo>?) {

        if (list.isNullOrEmpty()) return

        for (node in list) {

            recycleNode(node)

        }

    }

    // ── Robust Multi-Distance & Fare Extraction (Reverse Engineered from Ride-Taker & My Ride Accepter) ──

    data class FareBreakdown(

        val totalFare: Int,

        val baseFare: Int = totalFare,

        val surgeBonus: Int = 0

    )

    private fun extractFareBreakdown(texts: List<String>): FareBreakdown? {

        if (texts.isEmpty()) return null

        // 1. Join all texts from the card into a unified lowercase string and strip commas (e.g. ₹1,250 -> ₹1250)

        val joined = texts.joinToString(" ").lowercase(Locale.ROOT)

        val clean = joined.replace(",", "").replace(CUSTOMER_EXTRA_REGEX, "").replace(RATE_PER_KM_REGEX, "")

        // 2. Match bonus/surge sum pattern (e.g. "₹780 + ₹390" or "440 + 219")

        val bonusMatcher = FARE_BONUS_PATTERN.matcher(clean)

        if (bonusMatcher.find()) {

            try {

                val f1 = bonusMatcher.group(1)?.toFloatOrNull()?.toInt() ?: 0

                val f2 = bonusMatcher.group(2)?.toFloatOrNull()?.toInt() ?: 0

                val sum = f1 + f2

                if (sum in 5..50000) {

                    return FareBreakdown(totalFare = sum, baseFare = f1, surgeBonus = f2)

                }

            } catch (e: Exception) {

                // fallback

            }

        }

        // 3. Standard currency pattern match across unified string

        val matcher = FARE_PATTERN.matcher(clean)

        if (matcher.find()) {

            val fareStr = matcher.group(1) ?: matcher.group(2)

            val fareVal = fareStr?.toFloatOrNull()?.toInt()

            if (fareVal != null && fareVal in 5..50000) {

                return FareBreakdown(totalFare = fareVal, baseFare = fareVal, surgeBonus = 0)

            }

        }

        // 4. Line-by-line fallback for isolated currency token adjacent to numeric token

        for (i in texts.indices) {

            val t = texts[i].trim()

            if ((t == "₹" || t.equals("rs", ignoreCase = true) || t.equals("rs.", ignoreCase = true) || t.equals("inr", ignoreCase = true)) && i + 1 < texts.size) {

                val next = texts[i + 1].trim().replace(",", "")

                if (!next.contains("/km", ignoreCase = true) && !next.contains("per km", ignoreCase = true)) {

                    val fare = next.toFloatOrNull()?.toInt()

                    if (fare != null && fare in 5..50000) {

                        return FareBreakdown(totalFare = fare, baseFare = fare, surgeBonus = 0)

                    }

                }

            }

        }

        // 5. Digit-only fallback when ride context is strongly present

        val hasRideContext = joined.contains("km") || joined.contains("drop") ||

                joined.contains("pickup") || joined.contains("trip") ||

                joined.contains("order") || joined.contains("accept") ||

                joined.contains("ride") || joined.contains("captain")

        if (hasRideContext) {

            for (t in texts) {

                val cleanToken = t.trim().replace(",", "").replace(".00", "")

                if (cleanToken.matches(Regex("""^\d{2,4}$"""))) {

                    val num = cleanToken.toIntOrNull()

                    if (num != null && num in 20..5000) {

                        return FareBreakdown(totalFare = num, baseFare = num, surgeBonus = 0)

                    }

                }

            }

        }

        return null

    }

    private fun extractTotalFare(texts: List<String>): Int? {

        return extractFareBreakdown(texts)?.totalFare

    }

    private fun extractKmListAndIndices(texts: List<String>): Pair<List<Float>, List<Int>> {

        val kmList = mutableListOf<Float>()

        val kmIndices = mutableListOf<Int>()

        for (i in texts.indices) {

            val raw = texts[i].trim()

            // Strip out rate-per-km and speedometer patterns so "₹21/km", "21 / km", or "16 km/h" are never matched as a ride distance

            val t = raw.replace(RATE_PER_KM_REGEX, "").replace(SPEEDOMETER_REGEX, "").trim()

            if (t.isEmpty()) continue

            val matcher = DIST_PATTERN.matcher(t)

            while (matcher.find()) {

                try {

                    val numStr = matcher.group(1)?.replace(',', '.')

                    val unit = matcher.group(2)?.lowercase(Locale.ROOT) ?: "km"

                    var num = numStr?.toFloatOrNull()

                    if (num != null && num >= 0f) {

                        if ((unit == "m" || unit == "meters") && num > 10f) {

                            num /= 1000f

                        }

                        kmList.add(num)

                        kmIndices.add(i)

                    }

                } catch (e: Exception) {

                    // Ignore parse error

                }

            }

        }

        return Pair(kmList, kmIndices)

    }

    private fun isNonAddressLine(text: String): Boolean {

        val t = text.trim()

        if (t.length < 2) return true

        val lower = t.lowercase(Locale.ROOT)

        // 1. Standalone Section / Navigation Labels

        val labelTokens = setOf(

            "pickup", "pick up", "pick-up", "pickup point", "pick up point", "drop", "drop off", "dropoff", "drop-off", "drop point", "drop location", "pickup location",

            "from", "to", "origin", "destination", "location", "pickup address", "drop address",

            "go to pickup", "reach pickup", "go to drop", "navigate to pickup", "arrived at pickup",

            "पिकअप", "ड्रॉप", "पिकअप लोकेशन", "ड्रॉप लोकेशन", "कहाँ से", "कहाँ जाना है",

            "category icon", "icon", "navigation", "map", "route"

        )

        if (labelTokens.contains(lower)) {

            return true

        }

        // 2. Currency & Fare

        if (t.startsWith("₹") || lower.startsWith("rs") || lower.startsWith("inr") || NUMBER_OR_DECIMAL_REGEX.matches(t)) {

            return true

        }

        // 3. Distance, Speedometer, Rate per km

        if (DIST_SPEED_UNIT_REGEX.matches(t) || RATE_PER_KM_LINE_REGEX.matches(t)) {

            return true

        }

        if (lower.endsWith("away") && (DIST_KM_PATTERN.matcher(t).find() || DIST_METER_PATTERN.matcher(t).find() || lower.contains("min"))) {

            return true

        }

        // 4. Time / Duration / ETA tokens (e.g. "4 min", "5 mins", "15 sec", "15s", "08:45 pm")

        if (DURATION_ETA_TOKEN_REGEX.matches(lower) ||

            TIME_FORMAT_REGEX.matches(lower) ||

            lower == "am" || lower == "pm" || lower == "now" || lower == "later") {

            return true

        }

        // 5. Action & Button tokens

        val buttonTokens = setOf(

            "accept", "reject", "pass", "decline", "cancel", "deny", "skip", "ignore",

            "dismiss", "confirm", "match", "take", "take ride", "take order", "tap to accept",

            "tap to match", "exclusive", "trip radar", "view", "view >", "details", "view details",

            "close", "back", "x", "slide to accept", "swipe to accept", "slide to arrive",

            "swipe to arrive", "accept in", "accept order", "accept ride", "accept delivery",

            "auto accept", "book", "book now", "reserve", "accept reserve", "scheduled",

            "स्वीकार", "अस्वीकार", "स्वीकार करें", "एक्सेप्ट", "रिजेक्ट", "कन्फर्म", "मैच",

            "रद्द", "छोड़ें", "पास", "अंगीकार", "ಸ್ವೀಕರಿಸಿ", "ಖಚಿತಪಡಿಸಿ", "ஏற்கவும்", "मान्य करा"

        )

        if (buttonTokens.contains(lower) || lower.startsWith("accept in ") || lower.startsWith("accept in")) {

            return true

        }

        // 6. Category / Vehicle type / App branding

        val categoryTokens = setOf(

            "ola", "uber", "rapido", "namma yatri", "yatri", "jugnoo",

            "ola auto", "ola mini", "ola prime", "ola bike", "rapido auto", "rapido bike",

            "rapido captain", "uber go", "uber auto", "uber moto", "uber xl", "premier",

            "auto", "mini", "prime", "sedan", "suv", "bike", "moto", "go", "package",

            "parcel", "courier", "delivery", "rental", "outstation", "share", "pool"

        )

        if (categoryTokens.contains(lower)) {

            return true

        }

        // 7. Dashboard / Metrics / System tokens

        val systemTokens = setOf(

            "today's earnings", "today earnings", "wallet", "wallet balance", "search radius",

            "rating", "completed", "orders", "1 ride", "1 order", "see 1 more", "see 1 more orders",

            "customer added", "extra", "cash", "upi", "online", "offline", "go online", "go offline",

            "paid", "pay", "payment", "cancellation rate", "high cancellation", "safety", "otp",

            "verify", "arrived", "navigate", "contact customer", "customer yet to arrive",

            "daily goals", "goals", "performance", "profile", "captain", "driver", "partner",

            "my earnings", "incentive", "incentives", "daily target", "target", "weekly",

            "re-center", "recenter", "re-centre", "recentre", "re center", "re centre", "री-सेंटर", "फिर से केंद्रित"

        )

        if (systemTokens.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }) {

            return true

        }

        if (lower.contains("★") || lower.contains("⭐") || t.matches(Regex("""^\d+(\.\d+)?\s*[★⭐].*"""))) {

            return true

        }

        return false

    }

    private fun cleanAddressString(text: String): String {

        var s = text.trim()

        s = s.removePrefix("📍").removePrefix("🎯").removePrefix("🟢").removePrefix("🔴").trim()

        // Strip Ola composite icon metadata prefixes like "Category Icon, ", "Icon, "

        s = s.replace(CATEGORY_ICON_REGEX, "").trim()

        // Strip distance & ETA prefixes embedded in accessibility nodes, e.g. "1.5 km • 6 mins, ", "1.5 km, 6 mins, ", "6 mins, ", "1.5 km, "

        s = s.replace(DIST_ETA_PREFIX_REGEX, "").trim()

        s = s.replace(DIST_ONLY_PREFIX_REGEX, "").trim()

        s = s.replace(ETA_ONLY_PREFIX_REGEX, "").trim()

        val prefixes = listOf(

            "pickup:", "drop:", "pickup at:", "drop at:", "to:", "from:", "pick up:",

            "drop off:", "destination:", "origin:", "pickup location:", "drop location:",

            "pickup", "drop", "pick up", "drop off", "pick-up:", "drop-off:", "pick-up", "drop-off",

            "pickup point:", "drop point:", "pickup point", "drop point",

            "पिकअप:", "ड्रॉप:", "पिकअप", "ड्रॉप"

        )

        for (p in prefixes) {

            if (s.startsWith(p, ignoreCase = true) && s.length > p.length + 2) {

                s = s.substring(p.length).trim().removePrefix(":").removePrefix("-").removePrefix("•").trim()

            }

        }

        val trailingJunk = listOf(

            ", reject", ", accept", ", pass", ", decline", ", confirm", ", cancel", ", 1 ride", ", 1 order",

            " reject", " accept", " pass", " decline", " confirm", " cancel", " skip", " dismiss",

            ", अस्वीकार", ", स्वीकार", ", रिजेक्ट", ", एक्सेप्ट", ", रद्द"

        )

        var changed = true

        while (changed) {

            changed = false

            for (junk in trailingJunk) {

                if (s.endsWith(junk, ignoreCase = true)) {

                    s = s.substring(0, s.length - junk.length).trim()

                    changed = true

                }

            }

        }

        s = s.trim().trimEnd(',', '-', '•', ':').trim()

        val isLabelOnly = s.equals("Pickup Location", ignoreCase = true) ||

            s.equals("Drop Location", ignoreCase = true) ||

            s.equals("Pickup", ignoreCase = true) ||

            s.equals("Pick up", ignoreCase = true) ||

            s.equals("Pick-up", ignoreCase = true) ||

            s.equals("Pickup Point", ignoreCase = true) ||

            s.equals("Drop", ignoreCase = true) ||

            s.equals("Drop off", ignoreCase = true) ||

            s.equals("Dropoff", ignoreCase = true) ||

            s.equals("Drop-off", ignoreCase = true) ||

            s.equals("Drop Point", ignoreCase = true) ||

            s.equals("Destination", ignoreCase = true) ||

            s.equals("Origin", ignoreCase = true) ||

            s.equals("Location", ignoreCase = true) ||

            s.equals("Category Icon", ignoreCase = true) ||

            s.equals("Icon", ignoreCase = true) ||

            s.equals("पिकअप", ignoreCase = true) ||

            s.equals("ड्रॉप", ignoreCase = true) ||

            s.length < 2

        if (isLabelOnly) {

            return ""

        }

        return s

    }

    private fun parseLocationsFromRawTexts(cardTexts: List<String>): Pair<String?, String?> {

        val cleanLines = mutableListOf<String>()

        var explicitPickupLoc: String? = null

        var explicitDropLoc: String? = null

        var nextIsPickup = false

        var nextIsDrop = false

        for (raw in cardTexts) {

            val trimmed = raw.trim()

            val lower = trimmed.lowercase(Locale.ROOT)

            // Check if this node is purely a section label

            val isPurePickupLabel = lower == "pickup" || lower == "pick up" || lower == "pick-up" || lower == "from" ||

                    lower == "origin" || lower == "pickup location" || lower == "pickup point" || lower == "pick up point" ||

                    lower == "पिकअप" || lower == "पिकअप लोकेशन"

            val isPureDropLabel = lower == "drop" || lower == "drop off" || lower == "dropoff" || lower == "drop-off" ||

                    lower == "to" || lower == "destination" || lower == "drop location" || lower == "drop point" ||

                    lower == "ड्रॉप" || lower == "ड्रॉप लोकेशन"

            if (isPurePickupLabel) {

                nextIsPickup = true

                nextIsDrop = false

                continue

            }

            if (isPureDropLabel) {

                nextIsDrop = true

                nextIsPickup = false

                continue

            }

            if (isNonAddressLine(trimmed)) continue

            val cleaned = cleanAddressString(trimmed)

            if (cleaned.length < 2) continue

            val hasPickupIndicator = lower.startsWith("pickup:") || lower.startsWith("pick up:") || lower.startsWith("pick-up:") ||

                    lower.startsWith("pickup at:") || lower.startsWith("from:") || lower.startsWith("origin:") ||

                    lower.startsWith("पिकअप:") || trimmed.startsWith("📍") || trimmed.startsWith("🟢")

            val hasDropIndicator = lower.startsWith("drop:") || lower.startsWith("drop off:") || lower.startsWith("drop-off:") ||

                    lower.startsWith("drop at:") || lower.startsWith("to:") || lower.startsWith("destination:") ||

                    lower.startsWith("ड्रॉप:") || trimmed.startsWith("🎯") || trimmed.startsWith("🔴")

            if ((hasPickupIndicator || nextIsPickup) && explicitPickupLoc == null) {

                explicitPickupLoc = cleaned

                nextIsPickup = false

            } else if ((hasDropIndicator || nextIsDrop) && explicitDropLoc == null) {

                explicitDropLoc = cleaned

                nextIsDrop = false

            } else if (!cleanLines.contains(cleaned)) {

                cleanLines.add(cleaned)

            }

        }

        var pickupLoc = explicitPickupLoc

        var dropoffLoc = explicitDropLoc

        if (pickupLoc == null && dropoffLoc == null) {

            if (cleanLines.size >= 2) {

                pickupLoc = cleanLines[0]

                dropoffLoc = cleanLines[1]

            } else if (cleanLines.size == 1) {

                pickupLoc = cleanLines[0]

            }

        } else if (pickupLoc == null && cleanLines.isNotEmpty()) {

            pickupLoc = cleanLines.removeAt(0)

        } else if (dropoffLoc == null && cleanLines.isNotEmpty()) {

            dropoffLoc = cleanLines.removeAt(0)

        }

        // Safety cleanup: Ensure labels or duplicate pickup addresses are never stored as drop

        val cleanPickup = pickupLoc?.let { cleanAddressString(it) }?.ifBlank { null }

        val cleanDrop = dropoffLoc?.let { cleanAddressString(it) }?.ifBlank { null }

        val finalDrop = if (cleanDrop != null && cleanDrop != cleanPickup) cleanDrop else null

        return Pair(cleanPickup, finalDrop)

    }

    private fun parseRideDetails(cardTexts: List<String>, allScreenTexts: List<String> = emptyList()): RideDetails {

        val anyFilterActive = cachedIsFareActive || cachedIsDistActive || cachedNonFavZones.isNotEmpty() || cachedFavZones.isNotEmpty()

        if (!anyFilterActive) {

            // ⚡ Zero-Latency Full Bypass (<0.05ms): When all filters are OFF, bypass heavy regex & distance context loops,

            // but still extract pickup/drop locations so history shows complete ride details!

            val fareInfo = extractFareBreakdown(cardTexts)

            val fare = fareInfo?.totalFare

            val (bypassPickup, bypassDrop) = parseLocationsFromRawTexts(cardTexts)

            return RideDetails(

                fare = fare,

                baseFare = fareInfo?.baseFare ?: fare,

                surgeBonus = fareInfo?.surgeBonus,

                distance = null,

                tripDistance = null,

                pickupLocation = bypassPickup,

                dropLocation = bypassDrop,

                fullText = cardTexts.joinToString(" "),

                rawTexts = cardTexts,

                isAdvanceRide = false

            )

        }

        val fareInfo = extractFareBreakdown(cardTexts)

        val fare = fareInfo?.totalFare

        val baseFare = fareInfo?.baseFare

        val surgeBonus = fareInfo?.surgeBonus

        val (kmList, kmIndices) = if (cachedIsDistActive) extractKmListAndIndices(cardTexts) else Pair(emptyList(), emptyList())

        var pickupKm: Float? = null

        var tripKm: Float? = null

        if (kmList.size >= 2) {

            // Check context around each distance to distinguish pickup vs trip

            var identifiedPickup: Float? = null

            var identifiedTrip: Float? = null

            for (i in kmList.indices) {

                val kmVal = kmList[i]

                val idx = kmIndices.getOrNull(i) ?: continue

                val textAtDist = cardTexts.getOrNull(idx)?.lowercase(Locale.ROOT) ?: ""

                val prevText = if (idx > 0) cardTexts.getOrNull(idx - 1)?.lowercase(Locale.ROOT) ?: "" else ""

                val nextText = if (idx + 1 < cardTexts.size) cardTexts.getOrNull(idx + 1)?.lowercase(Locale.ROOT) ?: "" else ""

                val ctx = "$prevText $textAtDist $nextText"

                val isPickupContext = ctx.contains("away") || ctx.contains("reach") ||

                        ctx.contains("to pickup") || ctx.contains("pickup") || ctx.contains("pick up") ||

                        ctx.contains("from") || ctx.contains("पिकअप") || ctx.contains("origin")

                val isTripContext = ctx.contains("to drop") || ctx.contains("drop") ||

                        ctx.contains("destination") || ctx.contains("trip") || ctx.contains("total") ||

                        ctx.contains("ड्रॉप") || ctx.contains("ट्रिप")

                if (isPickupContext && !isTripContext && identifiedPickup == null) {

                    identifiedPickup = kmVal

                } else if (isTripContext && !isPickupContext && identifiedTrip == null) {

                    identifiedTrip = kmVal

                }

            }

            if (identifiedPickup != null && identifiedTrip != null) {

                pickupKm = identifiedPickup

                tripKm = identifiedTrip

            } else if (identifiedPickup != null) {

                pickupKm = identifiedPickup

                tripKm = (kmList - identifiedPickup).firstOrNull()

            } else if (identifiedTrip != null) {

                tripKm = identifiedTrip

                pickupKm = (kmList - identifiedTrip).firstOrNull()

            } else {

                // If neither has explicit cue, in Indian ride apps pickup is almost universally the smaller distance

                val sorted = kmList.sorted()

                pickupKm = sorted[0]

                tripKm = sorted.last()

            }

        } else if (kmList.size == 1) {

            val distIdx = kmIndices.getOrNull(0) ?: 0

            val textAtDist = cardTexts.getOrNull(distIdx)?.lowercase(Locale.ROOT) ?: ""

            val prevText = if (distIdx > 0) cardTexts.getOrNull(distIdx - 1)?.lowercase(Locale.ROOT) ?: "" else ""

            val nextText = if (distIdx + 1 < cardTexts.size) cardTexts.getOrNull(distIdx + 1)?.lowercase(Locale.ROOT) ?: "" else ""

            val contextText = "$prevText $textAtDist $nextText"

            val isExplicitDrop = contextText.contains("drop") ||

                    contextText.contains("destination") ||

                    contextText.contains("trip") ||

                    contextText.contains("to drop") ||

                    contextText.contains("ड्रॉप") ||

                    contextText.contains("ट्रिप")

            val isExplicitPickup = contextText.contains("pickup") ||

                    contextText.contains("pick up") ||

                    contextText.contains("away") ||

                    contextText.contains("reach") ||

                    contextText.contains("to pickup") ||

                    contextText.contains("पिकअप")

            if (isExplicitDrop) {

                tripKm = kmList[0]

                pickupKm = null

            } else if (isExplicitPickup) {

                pickupKm = kmList[0]

                tripKm = null

            } else {

                // When single distance is shown without clear label, treat as tripKm unless <= 3.0km WITH proximity cue.

                // CRITICAL: If treated as tripKm, pickupKm is null, which prevents distance filter from false rejections!

                if (kmList[0] > 3.0f) {

                    tripKm = kmList[0]

                    pickupKm = null

                } else {

                    pickupKm = kmList[0]

                    tripKm = null

                }

            }

        }

        // ── 2-Condition Advance / Back-to-Back Ride Detection Engine (<0.05ms) ──

        val backgroundTexts = if (allScreenTexts.isNotEmpty()) (allScreenTexts - cardTexts.toSet()).map { normalizeTextHyphens(it.lowercase(Locale.ROOT)) } else emptyList()

        val isBackgroundNavigationRunning = backgroundTexts.any {

            SPEEDOMETER_REGEX.containsMatchIn(it) ||

            it.contains("km/h") || it.contains("kmph") || it.contains("km/hr") || it.contains("kph") ||

            it.contains("re-center") || it.contains("recenter") || it.contains("re-centre") ||

            it.contains("recentre") || it.contains("re center") || it.contains("re centre") ||

            it.contains("re_center") || it.contains("re_centre") ||

            (it.contains("min") && (it.contains("am") || it.contains("pm")) && (it.contains("•") || it.contains("·")))

        }

        val cardLower = cardTexts.map { normalizeTextHyphens(it.lowercase(Locale.ROOT)) }

        val isExplicitScheduledCard = cardLower.any { text ->

            text.contains("advance booking") || text.contains("advance ride") || text.contains("advance order") ||

            text.contains("adv booking") || text.contains("adv ride") ||

            text.contains("scheduled ride") || text.contains("scheduled trip") || text.contains("scheduled booking") ||

            text.contains("scheduled for") || text.contains("scheduled order") || text.contains("schedule ride") ||

            text.contains("schedule trip") || text.contains("reservation") || text.contains("reserved") ||

            text.contains("reserve trip") || text.contains("reserve ride") || text.contains("reserve booking") ||

            text.contains("pre-booking") || text.contains("prebooking") || text.contains("pre booking") ||

            text.contains("book for later") || text.contains("ride later") ||

            text.contains("next ride") || text.contains("next booking") || text.contains("next trip") ||

            text.contains("next order") || text.contains("back to back") || text.contains("back-to-back") ||

            text.contains("queued booking") || text.contains("queued ride") || text.contains("queued order") ||

            text.contains("trip queue") || text.contains("ride queue") ||

            text.contains("pickup after drop") || text.contains("pickup from drop") || text.contains("pickup at drop") ||

            text.contains("after current trip") || text.contains("after current ride") || text.contains("after current order") ||

            text.contains("pickup tomorrow") || text.contains("tomorrow") ||

            text.contains("अगली बुकिंग") || text.contains("एडवांस बुकिंग") || text.contains("एडवांस राइड") ||

            text.contains("पुढील बुकिंग") || text.contains("ಅಡ್ವಾನ್ಸ್") || text.contains("ಮುಂಗಡ ಬುಕಿಂಗ್")

        }

        val hasCardOfferContext = cardLower.any { text ->

            ACCEPT_TEXTS.any { a -> text.contains(a) } || text.contains("₹") || text.contains("rs.") || text.contains("inr") || text.contains("\u20b9") || text.contains("trip radar") || text.contains("match")

        }

        val isAdvanceRide = (isBackgroundNavigationRunning && hasCardOfferContext) || isExplicitScheduledCard

        // ⚡ Address Extraction: Always parse addresses so ride history shows pickup and drop locations!

        var (cleanPickup, cleanDrop) = parseLocationsFromRawTexts(cardTexts)

        if ((cleanPickup == null || cleanDrop == null) && allScreenTexts.isNotEmpty()) {

            val (screenPickup, screenDrop) = parseLocationsFromRawTexts(allScreenTexts)

            if (cleanPickup == null) cleanPickup = screenPickup

            if (cleanDrop == null) cleanDrop = screenDrop

        }

        return RideDetails(

            fare = fare,

            baseFare = baseFare,

            surgeBonus = surgeBonus,

            distance = pickupKm,

            tripDistance = tripKm,

            pickupLocation = cleanPickup,

            dropLocation = cleanDrop,

            fullText = cardTexts.joinToString(" "),

            rawTexts = cardTexts,

            isAdvanceRide = isAdvanceRide

        )

    }

    private fun matchesZoneKeyword(targetText: String, keyword: String): Boolean {

        if (targetText.isEmpty() || keyword.isEmpty()) return false

        val trimmed = keyword.trim().lowercase(Locale.ROOT)

        if (trimmed.isEmpty()) return false

        return try {

            val escaped = Regex.escape(trimmed)

            val pattern = "(?i)(^|[^\\p{L}\\p{N}])$escaped([^\\p{L}\\p{N}]|$)".toRegex()

            pattern.containsMatchIn(targetText)

        } catch (e: Exception) {

            targetText.contains(trimmed)

        }

    }

    private fun evaluateRideFilters(rideDetails: RideDetails): Boolean {

        // Fast path 1: If no filters are enabled, immediately pass (0.0ms delay using cached memory primitives)

        if (!cachedIsFareActive && !cachedIsDistActive && cachedNonFavZones.isEmpty() && cachedFavZones.isEmpty()) {

            return true

        }

        if (cachedIsFareActive) {

            val totalFare = rideDetails.fare

            val baseFare = rideDetails.baseFare ?: totalFare

            if (totalFare != null) {

                // Min Fare validates against Total Fare (ensures minimum earnings in pocket)

                if (totalFare < cachedMinFare) {

                    Log.d(TAG, "REJECTED by fare filter: Total ₹$totalFare < min ₹$cachedMinFare")

                    return false

                }

                // Max Fare validates against Base Fare (trip scale ceiling, immune to surge bonuses)

                if (baseFare != null && cachedMaxFare > 0 && cachedMaxFare < Int.MAX_VALUE && baseFare > cachedMaxFare) {

                    Log.d(TAG, "REJECTED by fare filter: Base ₹$baseFare > max ₹$cachedMaxFare limit (Total ₹$totalFare)")

                    return false

                }

            }

        }

        // Distance filter strictly validates pickup distance against cachedMaxDist UNLESS this is an Advance Ride

        val skipDistanceForAdvance = rideDetails.isAdvanceRide && prefs.skipPickupForAdvanceRide

        if (skipDistanceForAdvance) {

            Log.i(TAG, "⚡ Advance / Back-to-Back Ride detected! Skipping pickup distance filter (Pickup: ${rideDetails.distance}km, Trip: ${rideDetails.tripDistance}km)")

        } else if (cachedIsDistActive && cachedMaxDist > 0f) {

            val pickupDist = rideDetails.distance

            if (pickupDist != null && pickupDist > cachedMaxDist) {

                Log.d(TAG, "REJECTED by pickup distance filter: ${pickupDist}km > ${cachedMaxDist}km limit")

                return false

            }

        }

        if (cachedNonFavZones.isNotEmpty() || cachedFavZones.isNotEmpty()) {

            val cleanLocationText = buildString {

                if (!rideDetails.pickupLocation.isNullOrBlank()) append(rideDetails.pickupLocation).append(" ")

                if (!rideDetails.dropLocation.isNullOrBlank()) append(rideDetails.dropLocation).append(" ")

                // If structured pickup/drop were not detected, use clean address tokens from rawTexts (excluding buttons, fares, categories)

                if (isEmpty()) {

                    val cleanAddressTokens = rideDetails.rawTexts.filter { !isNonAddressLine(it.trim()) }

                    append(cleanAddressTokens.joinToString(" "))

                }

            }.lowercase(Locale.ROOT)

            if (cachedNonFavZones.isNotEmpty() && cleanLocationText.isNotEmpty()) {

                for (nonFav in cachedNonFavZones) {

                    if (matchesZoneKeyword(cleanLocationText, nonFav)) {

                        Log.d(TAG, "REJECTED by non-fav zone blacklist: matched '$nonFav' in '$cleanLocationText'")

                        return false

                    }

                }

            }

            if (cachedFavZones.isNotEmpty()) {

                var matchedFav = false

                if (cleanLocationText.isNotEmpty()) {

                    for (fav in cachedFavZones) {

                        if (matchesZoneKeyword(cleanLocationText, fav)) {

                            matchedFav = true

                            break

                        }

                    }

                }

                if (!matchedFav) {

                    Log.d(TAG, "REJECTED by fav zone whitelist: no matching favorite zone in '$cleanLocationText'")

                    return false

                }

            }

        }

        return true

    }

    // ── Verification & History Management ──

    private fun getSuccessKeysForPlatform(platform: String): Array<String> {

        val lower = platform.lowercase(Locale.ROOT)

        return when {

            lower.contains("ola") -> OLA_SUCCESS_KEYS

            lower.contains("rapido") || lower.contains("captain") -> RAPIDO_SUCCESS_KEYS

            lower.contains("uber") -> UBER_SUCCESS_KEYS

            lower.contains("namma") || lower.contains("yatri") -> NAMMA_SUCCESS_KEYS

            lower.contains("bharat") -> BHARAT_SUCCESS_KEYS

            lower.contains("jugnoo") -> JUGNOO_SUCCESS_KEYS

            else -> GENERAL_SUCCESS_KEYS

        }

    }

    private fun checkRideSuccess(node: AccessibilityNodeInfo?, successKeys: Array<String>, depth: Int = 0): Boolean {

        if (node == null || depth > 20) return false

        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""

        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""

        val combined = "$text $desc".trim()

        if (combined.isNotEmpty()) {

            for (key in successKeys) {

                if (combined.contains(key)) {

                    return true

                }

            }

        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            if (checkRideSuccess(child, successKeys, depth + 1)) {

                return true

            }

        }

        return false

    }

    private fun checkAndHandleCancellation(rootNode: AccessibilityNodeInfo, pkg: String) {

        val now = System.currentTimeMillis()

        // Rate-limit cancellation checks: at most once every 2.5 seconds to prevent Binder IPC frame drops

        if (now - lastCancellationCheckTime < 2500L) {

            return

        }

        // Avoid checking history or completed trips screen to prevent false cancellation alerts

        if (hasTextMatchingAny(rootNode, HISTORY_SCREEN_TOKENS)) {

            return

        }

        var rideIdToCancel: Long? = null

        var platformToCancel: String? = null

        synchronized(pendingLock) {

            val aId = activeRideId

            val aPlat = activePlatform

            if (aId != null && aPlat != null) {

                if (now - activeRideTime < 1800000L) { // Within 30 minutes of accepting ride

                    // Only scan the active platform's package!

                    val isMatchingPlatform = when {

                        aPlat.contains("Ola", ignoreCase = true) -> pkg.contains("ola", ignoreCase = true)

                        aPlat.contains("Rapido", ignoreCase = true) -> pkg.contains("rapido", ignoreCase = true) || pkg.contains("captain", ignoreCase = true)

                        aPlat.contains("Uber", ignoreCase = true) -> pkg.contains("uber", ignoreCase = true)

                        aPlat.contains("Namma", ignoreCase = true) || aPlat.contains("Yatri", ignoreCase = true) -> pkg.contains("juspay", ignoreCase = true) || pkg.contains("yatri", ignoreCase = true) || pkg.contains("namma", ignoreCase = true)

                        else -> pkg.contains(aPlat, ignoreCase = true)

                    }

                    if (isMatchingPlatform) {

                        lastCancellationCheckTime = now

                        if (hasTextMatchingAny(rootNode, CANCELLATION_KEYS)) {

                            rideIdToCancel = aId

                            platformToCancel = aPlat

                            activeRideId = null

                            activePlatform = null

                        }

                    }

                } else {

                    activeRideId = null

                    activePlatform = null

                }

            }

        }

        if (rideIdToCancel != null) {

            val id = rideIdToCancel!!

            Log.w(TAG, "⚠️ RIDE CANCELLED BY CUSTOMER for $platformToCancel (Ride ID: $id)")

            speak("Ride cancelled by customer")

            serviceScope.launch {

                try {

                    db.rideHistoryDao().updateRideCancelled(id, "Cancelled by Customer")

                } catch (e: Exception) {

                    Log.e(TAG, "Failed to mark ride #$id as cancelled", e)

                }

            }

        }

    }

    private fun hasTextMatchingAny(node: AccessibilityNodeInfo?, keys: Array<String>, depth: Int = 0): Boolean {

        if (node == null || depth > 12) return false

        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""

        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""

        val combined = "$text $desc".trim()

        if (combined.isNotEmpty()) {

            for (k in keys) {

                if (combined.contains(k)) return true

            }

        }

        val count = node.childCount

        for (i in 0 until count) {

            val child = node.getChild(i) ?: continue

            if (hasTextMatchingAny(child, keys, depth + 1)) return true

        }

        return false

    }

    private fun armPendingAcceptance(

        packageName: String,

        details: RideDetails,

        method: String

    ) {

        val platform = determineAppNameFromPackage(packageName)

        val price = details.fare ?: 0

        val pickupKm = details.distance ?: 0f

        val dropKm = details.tripDistance ?: 0f

        val isAdvance = details.isAdvanceRide

        val rawTexts = details.rawTexts

        val pickupLoc = details.pickupLocation

        val dropLoc = details.dropLocation

        synchronized(pendingLock) {

            pendingPlatform = platform

            pendingPrice = price

            pendingPickupKm = pickupKm

            pendingDropKm = dropKm

            pendingRawTexts = rawTexts

            pendingPickupLoc = pickupLoc

            pendingDropLoc = dropLoc

            pendingClickTime = System.currentTimeMillis()

            pendingMethod = method

            pendingIsAdvance = isAdvance

            Log.i(TAG, "⏳ Armed pending ride acceptance for $pendingPlatform (Advance=$isAdvance, Fare=₹$pendingPrice, Pickup=${pendingPickupKm}km, Loc: $pendingPickupLoc -> $pendingDropLoc)")

        }

        // Guaranteed periodic asynchronous verification triggers across transition window

        val verifyDelays = longArrayOf(400L, 800L, 1200L, 1800L, 2500L, 3500L, 5000L, 7500L, 10000L)

        for (delay in verifyDelays) {

            handler.postDelayed({

                if (pendingPlatform != null) {

                    checkPendingVerification(System.currentTimeMillis())

                }

            }, delay)

        }

    }

    private fun checkPendingVerification(now: Long, eventSource: AccessibilityNodeInfo? = null) {

        synchronized(pendingLock) {

            val currentPendingPlatform = pendingPlatform ?: return

            val elapsed = now - pendingClickTime

            val maxVerificationTimeout = if (currentPendingPlatform.contains("uber")) 12000L else 6000L

            if (elapsed > maxVerificationTimeout) {

                // If max timeout elapsed, auto-verify and persist to history if fare is positive, advance ride, or pickup distance is valid

                if (pendingPrice > 0 || pendingIsAdvance || pendingPickupKm > 0f) {

                    Log.i(TAG, "🏆 Auto-verifying pending ride on timeout for $currentPendingPlatform (Fare=₹$pendingPrice, Pickup=${pendingPickupKm}km)")

                    if (pendingPrice > 0) {

                        speak("Ride confirmed! Fare $pendingPrice rupees")

                    } else if (pendingIsAdvance) {

                        speak("Advance ride confirmed!")

                    } else {

                        speak("Ride confirmed!")

                    }

                    extractAndSaveVerifiedData(

                        platform = currentPendingPlatform,

                        price = pendingPrice,

                        pickupKm = pendingPickupKm,

                        dropKm = pendingDropKm,

                        rawTexts = pendingRawTexts,

                        savedPickupLoc = pendingPickupLoc,

                        savedDropLoc = pendingDropLoc,

                        method = pendingMethod

                    )

                }

                pendingPlatform = null

                pendingPickupLoc = null

                pendingDropLoc = null

                return

            }

            if (elapsed >= 300L) {

                // Only verify after 300ms to allow UI transition and avoid matching old offer card

                val successKeys = getSuccessKeysForPlatform(currentPendingPlatform)

                var verified = false

                var failed = false

                val activeRoot = rootInActiveWindow ?: eventSource

                val activePkg = activeRoot?.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

                val isNavForeground = activePkg.contains("maps") || activePkg.contains("navlite") || activePkg.contains("waze")

                if (isNavForeground) {

                    verified = true

                } else if (activeRoot != null) {

                    if (hasTextMatchingAny(activeRoot, FAILURE_KEYS)) {

                        failed = true

                    } else if (checkRideSuccess(activeRoot, successKeys)) {

                        verified = true

                    }

                }

                if (!failed && !verified) {

                    val winList = try { windows } catch (e: Exception) { null }

                    if (!winList.isNullOrEmpty()) {

                        for (window in winList.reversed()) {

                            val winRoot = window.root ?: continue

                            val winPkg = winRoot.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""

                            if (winPkg.contains("maps") || winPkg.contains("navlite") || winPkg.contains("waze")) {

                                verified = true

                                break

                            }

                            if (hasTextMatchingAny(winRoot, FAILURE_KEYS)) {

                                failed = true

                                break

                            }

                            if (checkRideSuccess(winRoot, successKeys)) {

                                verified = true

                                break

                            }

                        }

                    }

                }

                // Reliability Fallback:

                // If offer card has disappeared and active screen is NOT an idle screen AND not an explicit failure

                if (!failed && !verified && elapsed >= 600L) {

                    val isOfferStillPresent = activeRoot != null && hasTextMatchingAny(

                        activeRoot,

                        arrayOf("accept in", "accept booking", "accept ride", "accept order", "tap to accept", "incoming booking", "incoming ride")

                    )

                    val isIdleScreen = activeRoot != null && hasTextMatchingAny(

                        activeRoot,

                        arrayOf("search radius", "today's earnings", "looking for rides", "you are online", "finding rides", "daily goal", "earnings", "go online", "you're offline")

                    )

                    val isUber = currentPendingPlatform.contains("uber")

                    if (isUber) {

                        val isUberPending = activeRoot != null && hasTextMatchingAny(activeRoot, UBER_PENDING_KEYS)

                        if (isUberPending) {

                            // Lapak AcceptConfirm.Signal.PENDING: Uber server matching is actively negotiating. Hold deadline!

                            Log.d(TAG, "⏳ [UBER ASYNC PENDING] Uber match in progress, holding verification deadline...")

                        } else if (!isOfferStillPresent) {

                            val isUberIdle = activeRoot != null && hasTextMatchingAny(activeRoot, UBER_IDLE_KEYS)

                            if (isUberIdle && elapsed >= 10000L && !checkRideSuccess(activeRoot, successKeys)) {

                                failed = true

                            } else if (!isUberIdle && (pendingPrice > 0 || pendingIsAdvance || pendingPickupKm > 0f)) {

                                verified = true

                            }

                        }

                    } else {

                        // Standard platforms (Ola, Rapido, Namma Yatri, Porter) - 100% UNTOUCHED

                        if (!isOfferStillPresent) {

                            if (isIdleScreen && elapsed >= 1500L && !checkRideSuccess(activeRoot, successKeys)) {

                                // Returned to idle search screen after 1.5s without active trip keys

                                failed = true

                            } else if (!isIdleScreen && (pendingPrice > 0 || pendingIsAdvance || pendingPickupKm > 0f)) {

                                // Offer card gone, screen transitioned, no failure dialog, not idle screen

                                verified = true

                            }

                        }

                    }

                }

                if (failed) {

                    Log.w(TAG, "❌ Ride acceptance failed for $currentPendingPlatform (missed/expired/taken)")

                    pendingPlatform = null

                    pendingPickupLoc = null

                    pendingDropLoc = null

                } else if (verified) {

                    Log.i(TAG, "🏆 RIDE ACCEPTANCE VERIFIED for $currentPendingPlatform (Advance=$pendingIsAdvance, Fare=₹$pendingPrice, Pickup=${pendingPickupKm}km)")

                    globalCooldownUntil = now + 2000L

                    packageBusyUntilMs[currentPendingPlatform] = now + 2000L

                    serviceScope.launch(Dispatchers.Default) {

                        try {

                            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 180)

                        } catch (e: Exception) {}

                    }

                    if (pendingPrice > 0) {

                        speak("Ride confirmed! Fare $pendingPrice rupees")

                    } else if (pendingIsAdvance) {

                        speak("Advance ride confirmed!")

                    } else {

                        speak("Ride confirmed!")

                    }

                    extractAndSaveVerifiedData(

                        platform = currentPendingPlatform,

                        price = pendingPrice,

                        pickupKm = pendingPickupKm,

                        dropKm = pendingDropKm,

                        rawTexts = pendingRawTexts,

                        savedPickupLoc = pendingPickupLoc,

                        savedDropLoc = pendingDropLoc,

                        method = pendingMethod

                    )

                    pendingPlatform = null

                    pendingPickupLoc = null

                    pendingDropLoc = null

                }

            }

        }

    }

    private fun extractAndSaveVerifiedData(

        platform: String,

        price: Int,

        pickupKm: Float,

        dropKm: Float,

        rawTexts: List<String>,

        savedPickupLoc: String? = null,

        savedDropLoc: String? = null,

        method: String

    ) {

        serviceScope.launch(Dispatchers.Default) {

            val (parsedPickup, parsedDrop) = parseLocationsFromRawTexts(rawTexts)

            val cleanPickup = savedPickupLoc?.ifBlank { null } ?: parsedPickup

            val cleanDrop = savedDropLoc?.ifBlank { null } ?: parsedDrop

            val finalPickup = cleanPickup?.ifBlank { null } ?: "Pickup Location"

            val finalDrop = cleanDrop?.ifBlank { null } ?: "Drop Location"

            try {

                val insertedId = db.rideHistoryDao().insertRide(

                    RideHistoryEntity(

                        appName = platform,

                        fare = if (price > 0) price else null,

                        distance = if (pickupKm > 0f) pickupKm else null,

                        tripDistance = if (dropKm > 0f) dropKm else null,

                        pickupLocation = finalPickup,

                        dropLocation = finalDrop,

                        accepted = true,

                        status = "ACCEPTED",

                        isCancelled = false,

                        method = method

                    )

                )

                // Ring buffer auto-trim to keep SQLite database lean and instant

                try {

                    db.rideHistoryDao().pruneOldRides(100)

                } catch (e: Exception) {

                    // Ignored

                }

                synchronized(pendingLock) {

                    activeRideId = insertedId

                    activePlatform = platform

                    activeRideTime = System.currentTimeMillis()

                }

                Log.i(TAG, "💾 Saved verified ride #$insertedId to database: $platform, ₹$price, Pickup: ${pickupKm}km, Trip: ${dropKm}km, Loc: $finalPickup -> $finalDrop")

            } catch (e: Exception) {

                Log.e(TAG, "Failed to save verified ride to history", e)

            }

        }

    }

    // ── Utilities & Wake-Up Helpers ──

    private fun wakeScreenAndBringToForeground(targetPackage: String) {

        try {

            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager

            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

            val isScreenOn = pm?.isInteractive ?: true

            val isLocked = km?.isKeyguardLocked ?: false

            // Shell-level privileged wakeup if Shizuku/Pari is active

            if (!isScreenOn || isLocked) {

                synchronized(shellLock) {

                    try {

                        shizukuOut?.write("input keyevent KEYCODE_WAKEUP\nwm dismiss-keyguard\n".toByteArray())

                        shizukuOut?.flush()

                    } catch (e: Exception) {}

                }

            }

            if ((!isScreenOn || isLocked) && prefs.autoWakeLockscreen) {

                LockscreenWakeActivity.wakeAndLaunch(applicationContext, targetPackage)

            } else if (prefs.acceptWhenMinimized) {

                val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)

                if (launchIntent != null) {

                    launchIntent.addFlags(

                        Intent.FLAG_ACTIVITY_NEW_TASK or

                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or

                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

                    )

                    startActivity(launchIntent)

                }

            }

        } catch (e: Exception) {

            Log.w(TAG, "Failed to wake or launch $targetPackage", e)

        }

    }

    private fun determineAppNameFromPackage(packageName: String): String {

        return SupportedApps.findByPackage(packageName)?.displayName ?: packageName

    }

    private fun initTextToSpeech() {

        try {

            tts = TextToSpeech(this) { status ->

                if (status == TextToSpeech.SUCCESS) {

                    val inResult = tts?.setLanguage(Locale("en", "IN"))

                    if (inResult == TextToSpeech.LANG_MISSING_DATA || inResult == TextToSpeech.LANG_NOT_SUPPORTED) {

                        val usResult = tts?.setLanguage(Locale.US)

                        if (usResult == TextToSpeech.LANG_MISSING_DATA || usResult == TextToSpeech.LANG_NOT_SUPPORTED) {

                            tts?.language = Locale.getDefault()

                        }

                    }

                }

            }

        } catch (e: Exception) {

            Log.w(TAG, "Failed to initialize TTS", e)

        }

    }

    private fun speak(text: String) {

        if (!prefs.isTtsEnabled) return

        serviceScope.launch(Dispatchers.Default) {

            val now = System.currentTimeMillis()

            if (text == lastSpokenText && now - lastSpokenTime < 5000) return@launch

            try {

                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ride_${now}")

                lastSpokenText = text

                lastSpokenTime = now

            } catch (e: Exception) {}

        }

    }

}
