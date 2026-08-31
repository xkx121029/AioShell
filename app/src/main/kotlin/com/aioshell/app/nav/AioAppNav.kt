package com.aioshell.app.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aioshell.app.feature.chat.ChatScreen
import com.aioshell.app.feature.chat.TemplatesScreen
import com.aioshell.app.feature.config.ConfigEditScreen
import com.aioshell.app.feature.config.ConfigListScreen
import com.aioshell.app.feature.config.MiscAiScreen
import com.aioshell.app.feature.session.AppearanceScreen
import com.aioshell.app.feature.session.ArchivedScreen
import com.aioshell.app.feature.session.SearchScreen
import com.aioshell.app.feature.session.StatsScreen
import com.aioshell.app.feature.session.SessionListScreen
import com.aioshell.app.icon.IconPickerScreen

object Routes {
    const val SESSIONS = "sessions"
    const val CHAT = "chat/{sessionId}?highlight={messageId}"
    const val SEARCH = "search"
    const val ARCHIVED = "archived"
    const val STATS = "stats"
    const val APPEARANCE = "appearance"
    const val TEMPLATES = "templates"
    const val CONFIGS = "configs"
    const val CONFIG_EDIT = "config/edit?configId={configId}"
    const val MISC_AI = "config/misc"
    const val ICONS = "icons"

    fun chat(sessionId: String, highlightMessageId: String? = null) =
        "chat/$sessionId?highlight=${highlightMessageId.orEmpty()}"
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
                onSearch = { navController.navigate(Routes.SEARCH) },
                onGoArchived = { navController.navigate(Routes.ARCHIVED) },
                onGoStats = { navController.navigate(Routes.STATS) },
                onGoAppearance = { navController.navigate(Routes.APPEARANCE) },
            )
        }

        composable(Routes.ARCHIVED) {
            ArchivedScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { navController.navigate(Routes.chat(it)) },
            )
        }

        composable(Routes.STATS) {
            StatsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.APPEARANCE) {
            AppearanceScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenResult = { sessionId, messageId ->
                    navController.navigate(Routes.chat(sessionId, messageId))
                },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("highlight") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onGoConfig = { navController.navigate(Routes.CONFIGS) },
                onGoTemplates = { navController.navigate(Routes.TEMPLATES) },
            )
        }

        composable(Routes.TEMPLATES) {
            TemplatesScreen(
                onBack = { navController.popBackStack() },
                onUseTemplate = { template ->
                    // 选择模板后返回对话页，由共享内存态接收
                    com.aioshell.app.feature.chat.TemplateTransfer.pending.value = template
                    navController.popBackStack()
                },
            )
        }

        composable(Routes.CONFIGS) {
            ConfigListScreen(
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Routes.configEdit(null)) },
                onEdit = { navController.navigate(Routes.configEdit(it)) },
                onMiscAi = { navController.navigate(Routes.MISC_AI) },
            )
        }

        composable(Routes.MISC_AI) {
            MiscAiScreen(onBack = { navController.popBackStack() })
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