package com.rideauto.assistant

/**
 * Registry of all supported ride-hailing and delivery apps with their package names
 * and accept button text/id variants. 100% synchronized with RideAccepterPro core.
 */
object SupportedApps {

    // The 3 primary platforms displayed in the driver UI
    val primaryApps: List<AppPackage> = listOf(
        AppPackage(
            displayName = "Ola Driver",
            packageName = "com.olacabs.oladriver",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Accept Ride", "ACCEPT RIDE",
                "Ride Accept", "RIDE ACCEPT", "Confirm", "CONFIRM", "Tap to accept", "TAP TO ACCEPT",
                "Accept Advance", "Advance Booking", "Accept Scheduled", "Book Now", "BOOK NOW", "Accept Booking", "ACCEPT BOOKING",
                "स्वीकार", "स्वीकार करें", "एक्सेप्ट", "कन्फर्म", "राइड स्वीकारें"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "button_accept", "driver_action_accept", "accept_btn", "accept_button",
                "accept_booking_btn", "btn_accept_booking", "acceptButton", "accept_ride_button",
                "acceptRideButton", "booking_accept_btn", "action_accept", "accept_ride",
                "btn_accept_advance", "accept_advance_btn", "btn_advance_accept", "advance_btn",
                "btn_advance", "btn_scheduled_accept", "accept_schedule_btn", "booking_accept_button",
                "ola_accept_button", "btn_ride_accept", "ride_accept_btn", "accept_container",
                "accept_layout", "motivator_overlay_container", "btn_confirm", "btn_take_ride", "take_ride_btn"
            )
        ),
        AppPackage(
            displayName = "Rapido Captain",
            packageName = "com.rapido.captain",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Accept Ride", "ACCEPT RIDE", "Accept Order", "ACCEPT ORDER",
                "Confirm", "CONFIRM", "Confirm Order", "Pick Order", "PICK ORDER", "Accept Delivery",
                "Auto Accept", "Tap to accept", "TAP TO ACCEPT", "Accept Advance", "Advance Booking",
                "Accept Scheduled", "Confirm Advance", "Book Now", "Take Ride", "Take Order",
                "Accept Next Ride", "एक्सेप्ट", "स्वीकार", "स्वीकार करें"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "accept_btn", "accept_button", "button_accept", "accept", "accept_order",
                "action_accept", "btn_accept_order", "accept_card_view", "btn_order_accept", "accept_layout", "order_accept_btn",
                "btn_accept_advance", "advance_accept_btn", "btn_advance_accept", "btn_accept_scheduled", "scheduled_accept_btn",
                "btn_schedule_accept", "advance_booking_accept", "btn_book", "btn_take_ride", "tv_accept", "tv_accept_order", "tv_accept_advance"
            )
        ),
        AppPackage(
            displayName = "Uber Driver",
            packageName = "com.ubercab.driver",
            acceptButtonTexts = listOf(
                "Match", "MATCH", "Accept Match", "ACCEPT MATCH",
                "Accept", "ACCEPT", "Accept Trip", "ACCEPT TRIP",
                "Accept Offer", "ACCEPT OFFER", "Tap to match", "TAP TO MATCH",
                "Exclusive", "EXCLUSIVE", "Tap to accept", "TAP TO ACCEPT",
                "Reserve", "RESERVE", "Accept Reserve", "Accept Reservation", "Confirm Reservation",
                "Match Reserve", "Accept Scheduled", "Confirm Reserve"
            ),
            acceptButtonIds = listOf(
                "upfront_offer_configurable_details_accept_button",
                "upfront_offer_configurable_details_auditable_accept_button",
                "uda_details_accept_button",
                "upfront_offer_view_holder_button",
                "offer_button",
                "accept_button",
                "ub_btn_accept_offer",
                "ub__preview_accept_button",
                "rider_offer_accept_button",
                "ub__basic_preview_accept",
                "ub__self_driving_match_accept_button",
                "ub__request_button",
                "ub__reserve_accept_button",
                "reserve_accept_button",
                "btn_reserve",
                "ub__scheduled_accept_button",
                "ub__trip_radar_accept",
                "trip_radar_button",
                "ub__trip_radar_match_button",
                "ub__trip_radar_offer_button",
                "btn_accept_reserve",
                "accept_reserve_btn",
                "ub__reserve_button"
            )
        )
    )

    // Complete registry of all supported platforms & sub-variants
    val all: List<AppPackage> = listOf(
        primaryApps[0], // Ola Driver
        AppPackage(
            displayName = "Ola Driver (Alt)",
            packageName = "com.olacabs.driver",
            acceptButtonTexts = primaryApps[0].acceptButtonTexts,
            acceptButtonIds = primaryApps[0].acceptButtonIds
        ),
        AppPackage(
            displayName = "Ola Driver (Lite)",
            packageName = "com.ola",
            acceptButtonTexts = primaryApps[0].acceptButtonTexts,
            acceptButtonIds = primaryApps[0].acceptButtonIds
        ),
        AppPackage(
            displayName = "Ola Partner",
            packageName = "com.olacabs.partner",
            acceptButtonTexts = primaryApps[0].acceptButtonTexts,
            acceptButtonIds = primaryApps[0].acceptButtonIds
        ),
        primaryApps[1], // Rapido Captain
        AppPackage(
            displayName = "Rapido Partner",
            packageName = "com.rapido.partner",
            acceptButtonTexts = primaryApps[1].acceptButtonTexts,
            acceptButtonIds = primaryApps[1].acceptButtonIds
        ),
        AppPackage(
            displayName = "Rapido Driver",
            packageName = "com.rapido.driver",
            acceptButtonTexts = primaryApps[1].acceptButtonTexts,
            acceptButtonIds = primaryApps[1].acceptButtonIds
        ),
        AppPackage(
            displayName = "Rapido Rider",
            packageName = "com.rapido.rider",
            acceptButtonTexts = primaryApps[1].acceptButtonTexts,
            acceptButtonIds = primaryApps[1].acceptButtonIds
        ),
        AppPackage(
            displayName = "Rapido Captain (RR)",
            packageName = "com.rr.android",
            acceptButtonTexts = primaryApps[1].acceptButtonTexts,
            acceptButtonIds = primaryApps[1].acceptButtonIds
        ),
        AppPackage(
            displayName = "Rapido (Generic)",
            packageName = "com.rapido",
            acceptButtonTexts = primaryApps[1].acceptButtonTexts,
            acceptButtonIds = primaryApps[1].acceptButtonIds
        ),
        primaryApps[2], // Uber Driver
        AppPackage(
            displayName = "Uber Driver (Alt)",
            packageName = "com.ubercab",
            acceptButtonTexts = primaryApps[2].acceptButtonTexts,
            acceptButtonIds = primaryApps[2].acceptButtonIds
        ),
        AppPackage(
            displayName = "Uber Partner",
            packageName = "com.ubercab.partner",
            acceptButtonTexts = listOf(
                "Match", "MATCH", "Accept Match", "ACCEPT MATCH",
                "Accept", "ACCEPT", "Accept Trip", "ACCEPT TRIP",
                "Accept Offer", "ACCEPT OFFER", "Tap to match", "TAP TO MATCH",
                "Exclusive", "EXCLUSIVE", "Tap to accept", "TAP TO ACCEPT",
                "Reserve", "RESERVE", "Accept Reserve", "Accept Reservation", "Confirm Reservation",
                "Match Reserve", "Accept Scheduled", "Confirm Reserve",
                "मैच", "मैच करें", "स्वीकार"
            ),
            acceptButtonIds = primaryApps[2].acceptButtonIds
        ),
        AppPackage(
            displayName = "Uber Driver (Beta)",
            packageName = "com.ubercab.driver.beta",
            acceptButtonTexts = primaryApps[2].acceptButtonTexts,
            acceptButtonIds = primaryApps[2].acceptButtonIds
        ),
        AppPackage(
            displayName = "Uber Driver (Legacy)",
            packageName = "com.uber.driver",
            acceptButtonTexts = primaryApps[2].acceptButtonTexts,
            acceptButtonIds = primaryApps[2].acceptButtonIds
        ),
        AppPackage(
            displayName = "Namma Yatri Partner",
            packageName = "in.juspay.nammayatripartner",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Accept Ride", "ACCEPT RIDE",
                "Offer", "OFFER", "Confirm", "CONFIRM", "Reserve", "RESERVE",
                "Take", "TAKE", "Tap to accept", "Accept Advance", "Advance Booking",
                "एक्सेप्ट", "स्वीकार", "स्वीकार करें"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "accept_btn", "accept_button", "btn_confirm",
                "accept_ride", "offer_btn", "action_accept", "btn_accept_ride", "accept_ride_btn",
                "btn_accept_advance", "advance_accept_btn", "btn_scheduled_accept"
            )
        ),
        AppPackage(
            displayName = "Namma Yatri",
            packageName = "in.juspay.nammayatri",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Accept Ride", "ACCEPT RIDE",
                "Offer", "OFFER", "Confirm", "CONFIRM", "Reserve", "RESERVE",
                "Take", "TAKE", "Tap to accept", "Accept Advance", "Advance Booking",
                "एक्सेप्ट", "स्वीकार", "स्वीकार करें"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "accept_btn", "accept_button", "btn_confirm",
                "accept_ride", "offer_btn", "action_accept", "btn_accept_ride", "accept_ride_btn",
                "btn_accept_advance", "advance_accept_btn", "btn_scheduled_accept"
            )
        ),
        AppPackage(
            displayName = "Yatri Partner",
            packageName = "in.juspay.yatridriver",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Accept Ride", "ACCEPT RIDE",
                "Offer", "OFFER", "Confirm", "CONFIRM", "Reserve", "RESERVE",
                "Take", "TAKE", "Tap to accept", "Accept Advance", "Advance Booking",
                "एक्सेप्ट", "स्वीकार", "स्वीकार करें"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "accept_btn", "accept_button", "btn_confirm",
                "accept_ride", "offer_btn", "action_accept", "btn_accept_ride", "accept_ride_btn",
                "btn_accept_advance", "advance_accept_btn", "btn_scheduled_accept"
            )
        ),
        AppPackage(
            displayName = "Mana Yatri Partner",
            packageName = "in.juspay.manayatripartner",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Accept Ride", "ACCEPT RIDE",
                "Offer", "OFFER", "Confirm", "CONFIRM", "Reserve", "RESERVE",
                "Take", "TAKE", "Tap to accept", "Accept Advance", "Advance Booking",
                "एक्सेप्ट", "स्वीकार", "स्वीकार करें"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "accept_btn", "accept_button", "btn_confirm",
                "accept_ride", "offer_btn", "action_accept", "btn_accept_ride", "accept_ride_btn",
                "btn_accept_advance", "advance_accept_btn", "btn_scheduled_accept"
            )
        ),
        AppPackage(
            displayName = "Yatri Sathi Partner",
            packageName = "in.juspay.yatrisathi",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Accept Ride", "ACCEPT RIDE",
                "Offer", "OFFER", "Confirm", "CONFIRM", "Reserve", "RESERVE",
                "Take", "TAKE", "Tap to accept", "Accept Advance", "Advance Booking",
                "एक्सेप्ट", "स्वीकार", "स्वीकार करें"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "accept_btn", "accept_button", "btn_confirm",
                "accept_ride", "offer_btn", "action_accept", "btn_accept_ride", "accept_ride_btn",
                "btn_accept_advance", "advance_accept_btn", "btn_scheduled_accept"
            )
        ),
        AppPackage(
            displayName = "Swiggy Partner",
            packageName = "in.swiggy.deliveryapp",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Accept Order", "ACCEPT ORDER", "Confirm", "CONFIRM",
                "Pick", "PICK", "Delivery", "DELIVERY", "Slide to accept", "Tap to accept"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "accept_btn", "accept_button", "btn_confirm", "order_accept"
            )
        ),
        AppPackage(
            displayName = "Swiggy Delivery Partner",
            packageName = "in.swiggy.deliverypartner",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Accept Order", "ACCEPT ORDER", "Confirm", "CONFIRM",
                "Pick", "PICK", "Delivery", "DELIVERY", "Slide to accept", "Tap to accept"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "accept_btn", "accept_button", "btn_confirm", "order_accept"
            )
        ),
        AppPackage(
            displayName = "Zomato Partner",
            packageName = "com.zomato.delivery",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Accept Order", "ACCEPT ORDER", "Confirm", "CONFIRM",
                "Start", "START", "Delivery", "DELIVERY", "Slide to accept", "Tap to accept"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "accept_btn", "accept_button", "btn_confirm", "order_accept"
            )
        ),
        AppPackage(
            displayName = "Red Taxi",
            packageName = "com.ind.redtaxi.driverapp",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Confirm", "CONFIRM", "Take", "TAKE",
                "ஏற்கவும்", "ஏற்றுக்கொள்", "ஏற்றுக்கொள்ளவும்", "உறுதிப்படுத்து", "பொருந்து"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "accept_btn", "accept_button", "btn_confirm", "accept_ride", "button_accept"
            )
        ),
        AppPackage(
            displayName = "Bharat Taxi",
            packageName = "in.mobility.bharattaxidriver",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Confirm", "CONFIRM", "Take", "TAKE",
                "स्वीकार", "स्वीकार करें", "एक्सेप्ट"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "accept_btn", "accept_button", "btn_confirm", "accept_ride"
            )
        ),
        AppPackage(
            displayName = "Porter Driver",
            packageName = "com.theporter.android.driverapp",
            acceptButtonTexts = listOf(
                "Accept", "ACCEPT", "Accept Order", "ACCEPT ORDER", "Accept Pickup", "ACCEPT PICKUP",
                "Slide to accept", "Slide To Accept", "SLIDE TO ACCEPT", "Swipe to accept", "SWIPE TO ACCEPT",
                "Confirm", "CONFIRM", "Take Order", "TAKE ORDER"
            ),
            acceptButtonIds = listOf(
                "btn_accept", "accept_btn", "accept_button", "btn_confirm", "order_accept",
                "btn_slide_accept", "slide_to_accept", "accept_order_btn", "tv_accept"
            )
        )
    )

    // Map of sub-packages associated with each primary platform
    val platformSubPackages: Map<String, List<String>> = mapOf(
        "com.olacabs.oladriver" to listOf(
            "com.olacabs.oladriver",
            "com.olacabs.driver",
            "com.ola",
            "com.olacabs.partner"
        ),
        "com.rapido.captain" to listOf(
            "com.rapido.captain",
            "com.rapido.partner",
            "com.rapido.driver",
            "com.rapido.rider",
            "com.rr.android",
            "com.rapido"
        ),
        "com.ubercab.driver" to listOf(
            "com.ubercab.driver",
            "com.ubercab.partner",
            "com.ubercab.driver.beta",
            "com.ubercab",
            "com.uber.driver"
        ),
        "in.juspay.nammayatripartner" to listOf(
            "in.juspay.nammayatripartner",
            "in.juspay.nammayatri",
            "in.juspay.yatridriver",
            "in.juspay.manayatripartner",
            "in.juspay.yatrisathi"
        ),
        "com.theporter.android.driverapp" to listOf(
            "com.theporter.android.driverapp",
            "com.theporter.driverapp"
        ),
        "com.ind.redtaxi.driverapp" to listOf(
            "com.ind.redtaxi.driverapp"
        ),
        "in.mobility.bharattaxidriver" to listOf(
            "in.mobility.bharattaxidriver"
        ),
        "com.zomato.delivery" to listOf(
            "com.zomato.delivery",
            "in.swiggy.deliverypartner",
            "in.swiggy.deliveryapp"
        )
    )

    data class UnifiedPlatform(
        val id: String,
        val name: String,
        val subtitle: String,
        val primaryPackage: String,
        val badgeText: String,
        val subPackages: List<String>
    )

    val unifiedPlatforms: List<UnifiedPlatform> = listOf(
        UnifiedPlatform(
            id = "ola",
            name = "Ola Driver",
            subtitle = "Ola Driver & Partner",
            primaryPackage = "com.olacabs.oladriver",
            badgeText = "OLA",
            subPackages = platformSubPackages["com.olacabs.oladriver"] ?: listOf("com.olacabs.oladriver")
        ),
        UnifiedPlatform(
            id = "rapido",
            name = "Rapido Captain",
            subtitle = "Rapido Captain & Partner",
            primaryPackage = "com.rapido.captain",
            badgeText = "RAPIDO",
            subPackages = platformSubPackages["com.rapido.captain"] ?: listOf("com.rapido.captain")
        ),
        UnifiedPlatform(
            id = "uber",
            name = "Uber Driver",
            subtitle = "Uber Driver Platform",
            primaryPackage = "com.ubercab.driver",
            badgeText = "UBER",
            subPackages = platformSubPackages["com.ubercab.driver"] ?: listOf("com.ubercab.driver")
        ),
        UnifiedPlatform(
            id = "namma_yatri",
            name = "Namma Yatri / Yatri",
            subtitle = "Namma Yatri, Mana Yatri & Yatri Sathi",
            primaryPackage = "in.juspay.nammayatripartner",
            badgeText = "YATRI",
            subPackages = platformSubPackages["in.juspay.nammayatripartner"] ?: listOf("in.juspay.nammayatripartner")
        ),
        UnifiedPlatform(
            id = "red_taxi",
            name = "Red Taxi",
            subtitle = "Red Taxi Driver App",
            primaryPackage = "com.ind.redtaxi.driverapp",
            badgeText = "RED TAXI",
            subPackages = platformSubPackages["com.ind.redtaxi.driverapp"] ?: listOf("com.ind.redtaxi.driverapp")
        ),
        UnifiedPlatform(
            id = "bharat_taxi",
            name = "Bharat Taxi",
            subtitle = "Bharat Taxi Driver App",
            primaryPackage = "in.mobility.bharattaxidriver",
            badgeText = "BHARAT TAXI",
            subPackages = platformSubPackages["in.mobility.bharattaxidriver"] ?: listOf("in.mobility.bharattaxidriver")
        ),
        UnifiedPlatform(
            id = "porter",
            name = "Porter Driver",
            subtitle = "Porter Driver Partner",
            primaryPackage = "com.theporter.android.driverapp",
            badgeText = "PORTER",
            subPackages = platformSubPackages["com.theporter.android.driverapp"] ?: listOf("com.theporter.android.driverapp")
        ),
        UnifiedPlatform(
            id = "delivery",
            name = "Delivery Partners",
            subtitle = "Zomato & Swiggy Delivery",
            primaryPackage = "com.zomato.delivery",
            badgeText = "DELIVERY",
            subPackages = platformSubPackages["com.zomato.delivery"] ?: listOf("com.zomato.delivery")
        )
    )

    /** All known "Accept" button text variants across all apps */
    val universalAcceptVariants: List<String> = listOf(
        "accept", "confirm", "match", "offer", "reserve", "take", "pick", "start", "book now",
        "एक्सेप्ट", "कन्फर्म", "स्वीकार", "स्वीकारें", "स्वीकारा", "मान्य करा", "स्वीकार करा", "रिजर्व", "शेड्यूल",
        "tap to accept", "delivery", "exclusive", "slide to accept", "swipe to accept",
        "Accept", "ACCEPT", "Accept Ride", "ACCEPT RIDE", "Accept Order", "ACCEPT ORDER",
        "Accept Trip", "ACCEPT TRIP", "Tap to match", "TAP TO MATCH", "Match", "MATCH",
        "Order Match", "ORDER MATCH", "Pick Order", "PICK ORDER", "Accept Booking",
        "Confirm Order", "CONFIRM ORDER", "Accept Advance", "Advance Booking", "Accept Scheduled",
        "Reserve", "Accept Reserve", "Accept Reservation", "Confirm Reservation", "Match Reserve"
    )

    /** Regional language "Accept" variants */
    val regionalAcceptVariants: List<String> = emptyList()

    /** All package names for quick lookup */
    val allPackageNames: List<String> = all.map { it.packageName }

    fun isSupported(packageName: String?): Boolean {
        if (packageName == null) return false
        val pkg = packageName.lowercase()
        return all.any { it.packageName.equals(pkg, ignoreCase = true) || pkg.contains(it.packageName) || it.packageName.contains(pkg) } ||
                pkg.contains("olacabs") || pkg.contains("rapido") || pkg.contains("ubercab") ||
                pkg.contains("com.ola") || pkg.contains("com.rr.android") || pkg.contains("juspay") ||
                pkg.contains("namma") || pkg.contains("yatri") || pkg.contains("porter") ||
                pkg.contains("redtaxi") || pkg.contains("bharattaxi") || pkg.contains("bharat") ||
                pkg.contains("swiggy") || pkg.contains("zomato") || pkg.contains("theporter")
    }

    fun getAppByPackage(packageName: String?): AppPackage? {
        if (packageName == null) return null
        val pkg = packageName.lowercase()
        return all.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) || pkg.contains(it.packageName) }
            ?: primaryApps.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) || pkg.contains(it.packageName) }
    }

    /** Find AppPackage by its package name */
    fun findByPackage(packageName: String): AppPackage? {
        val lower = packageName.lowercase()
        return all.find {
            val pkg = it.packageName.lowercase()
            lower.contains(pkg) || pkg.contains(lower) ||
            (lower.contains("namma") && pkg.contains("namma")) ||
            (lower.contains("yatri") && pkg.contains("yatri")) ||
            (lower.contains("juspay") && pkg.contains("juspay")) ||
            (lower.contains("porter") && pkg.contains("porter")) ||
            (lower.contains("redtaxi") && pkg.contains("redtaxi")) ||
            (lower.contains("bharat") && pkg.contains("bharat")) ||
            (lower.contains("mobility") && pkg.contains("mobility")) ||
            (lower.contains("swiggy") && pkg.contains("swiggy")) ||
            (lower.contains("zomato") && pkg.contains("zomato"))
        } ?: getAppByPackage(packageName)
    }

    /** Get all accept text variants combined */
    fun getAllAcceptVariants(): List<String> {
        return (universalAcceptVariants + regionalAcceptVariants).distinct()
    }
}
