package com.styropyr0.kairos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Abstract class for milestone receivers used by [Kairos].
 * Provides a default implementation for [onReceive].
 *
 * @author Saurav Sajeev
 */
open class KairosReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {}
}