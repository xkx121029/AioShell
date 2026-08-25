package com.aioshell.app.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aioshell.app.feature.chat.ChatScreen
import com.aioshell.app.feature.config.ConfigEditScreen
import com.aioshell.app.feature.config.ConfigListScreen
import com.aioshell.app.feature.session.SessionListScreen
import com.aioshell.app.icon.IconPickerScreen

object Routes {
    const val SESSIONS = "sessions"
    const val CHAT = "chat/{sessionId}"
    const val CONFIGS = "configs"
    const val CONFIG_EDIT = "config/edit?configId={configId}"
    const val ICONS = "icons"

    fun chat(sessionId: String) = "chat/$sessionId"
    fun configEdit(configId: String?) = "config/edit?configId=${configId.orEmpty()}"
}

@Composable
fun AioAppNav() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.SESSIONS) {

        composable(Routes.SESSIONS) {
            SessionListScreen(
                onOpenSession = { navController.navigate(Routes.chat(it)) },
                onGoConfig = { navController.navigate(Routes.CONFIGS) },
                onSelectConfig = { navController.navigate(Routes.CONFIGS) },
                onSelectIcon = { navController.navigate(Routes.ICONS) },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onGoConfig = { navController.navigate(Routes.CONFIGS) },
            )
        }

        composable(Routes.CONFIGS) {
            ConfigListScreen(
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Routes.configEdit(null)) },
                onEdit = { navController.navigate(Routes.configEdit(it)) },
            )
        }

        composable(
            route = Routes.CONFIG_EDIT,
            arguments = listOf(navArgument("configId") { type = NavType.StringType; defaultValue = "" }),
        ) {
            ConfigEditScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ICONS) {
            IconPickerScreen(onBack = { navController.popBackStack() })
        }
    }
}