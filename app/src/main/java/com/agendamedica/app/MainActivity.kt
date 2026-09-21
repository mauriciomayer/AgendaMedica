package com.agendamedica.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.agendamedica.app.ui.navigation.AgendaMedicaNavHost
import com.agendamedica.app.ui.theme.AgendaMedicaTheme
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    // Deep links (agendamedica://reset-password) are handed to the NavHost, which routes them.
    private val deepLinks = MutableSharedFlow<Intent>(replay = 1, extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh launch: after a configuration change the same intent must not be replayed.
        if (savedInstanceState == null) deepLinks.tryEmit(intent)
        setContent {
            AgendaMedicaTheme {
                AgendaMedicaNavHost(deepLinks = deepLinks)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinks.tryEmit(intent)
    }
}
