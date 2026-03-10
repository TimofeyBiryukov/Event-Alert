package com.example.eventalert.power

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Returns true when system battery optimization is effectively ON for this app.
 *
 * If the platform API is unavailable or an error occurs, this conservatively returns true
 * so that the UI can warn the user.
 */
fun isBatteryOptimizationEnabled(context: Context): Boolean {
    return try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Battery optimization does not apply before Android M.
            false
        } else {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return true
            // isIgnoringBatteryOptimizations == true => app is exempt, so optimization is NOT enabled.
            !pm.isIgnoringBatteryOptimizations(context.packageName)
        }
    } catch (_: Exception) {
        // Fail safe: assume optimized so we show the banner.
        true
    }
}

