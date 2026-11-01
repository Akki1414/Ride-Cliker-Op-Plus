package com.rideauto.assistant

data class AppPackage(
    val name: String,
    val packageName: String,
    val keywords: List<String>,
    val defaultEnabled: Boolean = true,
    val iconRes: Int? = null,
    val isSlideAccept: Boolean = false,
    val fastPathSupported: Boolean = false,
    val acceptButtonIds: List<String> = emptyList(),
    val iconResName: String? = null
) {
    val displayName: String get() = name
    val acceptButtonTexts: List<String> get() = keywords

    constructor(
        displayName: String,
        packageName: String,
        acceptButtonTexts: List<String>,
        acceptButtonIds: List<String> = emptyList(),
        iconResName: String? = null
    ) : this(
        name = displayName,
        packageName = packageName,
        keywords = acceptButtonTexts,
        defaultEnabled = true,
        iconRes = null,
        isSlideAccept = acceptButtonTexts.any { it.contains("slide", ignoreCase = true) || it.contains("swipe", ignoreCase = true) },
        fastPathSupported = true,
        acceptButtonIds = acceptButtonIds,
        iconResName = iconResName
    )
}
