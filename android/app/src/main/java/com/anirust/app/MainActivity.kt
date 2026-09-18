package com.anirust.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anirust.app.ui.navigation.AnirustAppRoot
import com.anirust.app.ui.player.ExternalPlaybackTracker
import com.anirust.app.ui.player.ExternalPlayerHost
import com.anirust.app.ui.theme.AnirustTheme

class MainActivity : ComponentActivity(), ExternalPlayerHost {
    private lateinit var externalPlayback: ExternalPlaybackTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AnirustApp).container
        externalPlayback = ExternalPlaybackTracker(this, container.watchHistoryUseCase)

        setContent { AnirustTheme { AnirustAppRoot(container = container) } }
    }

    override fun launchExternalPlayer(intent: Intent, historyId: String) {
        externalPlayback.launchExternalPlayer(intent, historyId)
    }
}
