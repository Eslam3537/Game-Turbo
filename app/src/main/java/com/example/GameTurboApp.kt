package com.example

import android.app.Application
import com.example.engine.session.SessionController

class GameTurboApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionController.init(this)
    }
}
