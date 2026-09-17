package com.anirust.app

import android.app.Application
import com.anirust.app.di.AppContainer
import com.anirust.app.di.DefaultAppContainer

class AnirustApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
