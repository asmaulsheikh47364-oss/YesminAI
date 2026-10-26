package com.yesmin.ai.ui

import android.util.Log

object LiveTaskMonitor {
    private const val TAG = "LiveTaskMonitor"

    fun logStatus(step: String, status: String, details: String) {
        Log.i(TAG, "[$step] Status: $status | Details: $details")
    }
}
