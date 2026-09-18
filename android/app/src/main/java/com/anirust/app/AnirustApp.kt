package com.anirust.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.anirust.app.data.local.createPosterImageLoader
import com.anirust.app.di.AppContainer
import com.anirust.app.di.DefaultAppContainer

class AnirustApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader = createPosterImageLoader(this)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
