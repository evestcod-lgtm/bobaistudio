package com.bobai.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bobai.studio.ui.EditorScreen
import com.bobai.studio.ui.HomeScreen
import com.bobai.studio.ui.theme.BobAiStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BobAiStudioTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(onProjectOpened = { projectId ->
                            navController.navigate("editor/$projectId")
                        })
                    }
                    composable(
                        route = "editor/{projectId}",
                        arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
                        EditorScreen(projectId = projectId)
                    }
                }
            }
        }
    }
}
