package com.example.janggi2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.janggi2.presentation.navigation.JangGiNavHost
import com.example.janggi2.ui.theme.JangGi2Theme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JangGi2Theme {
                JangGi2App()
            }
        }
    }
}

@Composable
fun JangGi2App() {
    val navController = rememberNavController()

    JangGiNavHost(
        navController = navController,
        modifier = Modifier.fillMaxSize()
    )
}