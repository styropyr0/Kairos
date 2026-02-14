package com.styropyr0.testproject

import android.app.Application
import com.styropyr0.kairos.Kairos

class TestProjectApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Kairos here
        Kairos.init(this)
    }
}