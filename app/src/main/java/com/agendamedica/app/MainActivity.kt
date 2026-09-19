package com.agendamedica.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.agendamedica.app.ui.navigation.AgendaMedicaNavHost
import com.agendamedica.app.ui.theme.AgendaMedicaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgendaMedicaTheme {
                AgendaMedicaNavHost()
            }
        }
    }
}
