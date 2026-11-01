package com.rideauto.assistant.managers

data class DevOptionsState(
    val isDevOptionsEnabled: Boolean,
    val isUsbDebuggingEnabled: Boolean,
    val isWirelessDebuggingEnabled: Boolean,
    val isKeepScreenOnEnabled: Boolean,
    val windowAnimationScale: Float,
    val transitionAnimationScale: Float,
    val animatorDurationScale: Float
)
