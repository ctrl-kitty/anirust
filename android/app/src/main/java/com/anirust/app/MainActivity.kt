package com.anirust.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anirust.app.ui.navigation.AnirustAppRoot
import com.anirust.app.ui.theme.AnirustTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AnirustApp).container

        setContent { AnirustTheme { AnirustAppRoot(container = container) } }
    }
}
