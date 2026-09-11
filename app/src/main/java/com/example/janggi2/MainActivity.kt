package com.example.janggi2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.janggi2.domain.repository.AuthRepository
import com.example.janggi2.presentation.navigation.JangGiNavHost
import com.example.janggi2.presentation.navigation.Screen
import com.example.janggi2.ui.theme.JangGi2Theme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Firebase가 세션을 로컬에 유지하므로 네트워크 호출 없이 동기적으로 확인됩니다.
        val startDestination =
            if (authRepository.getCurrentUser() != null) Screen.Game.route else Screen.Login.route
        setContent {
            JangGi2Theme {
                JangGi2App(startDestination = startDestination)
            }
        }
    }
}

@Composable
fun JangGi2App(startDestination: String = Screen.Game.route) {
    val navController = rememberNavController()

    JangGiNavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize()
    )
}