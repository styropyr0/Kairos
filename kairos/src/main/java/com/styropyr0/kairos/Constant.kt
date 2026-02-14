package com.styropyr0.kairos

internal object Constant {
    const val PREF_IDENTIFIER = "kairos_pref"
    const val SCHEDULED_TIMER = "scheduled_timer"
    const val SCHEDULED_TIMER_TYPE = "scheduled_timer_type"
    const val KAIROS_INIT_FAILED_EXCEPT = "Kairos is not initialized. Ensure Kairos.init(context) is called in your Application.onCreate() before invoking any Kairos APIs."
    const val KAIROS_UNPRIVILEGED_ACCESS = "Kairos require at least one of the required permissions - `SCHEDULE_EXACT_ALARM` permission in manifest, or the app must be excluded from battery optimization."
}