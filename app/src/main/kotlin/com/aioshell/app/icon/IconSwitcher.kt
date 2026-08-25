package com.aioshell.app.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.iconPrefs by preferencesDataStore(name = "aioshell_icon")

/**
 * 启动器图标切换：通过切换 Manifest 中三个 activity-alias 的启用状态实现。
 * 选择会持久化，并在应用启动时重新应用。
 */
@Singleton
class IconSwitcher @Inject constructor(@ApplicationContext private val context: Context) {

    private companion object {
        const val DEFAULT = "default"
        const val A = "a"
        const val B = "b"
        val KEY_ICON = stringPreferencesKey("launcher_icon")

        val aliases = mapOf(
            DEFAULT to "com.aioshell.app.LauncherDefault",
            A to "com.aioshell.app.LauncherA",
            B to "com.aioshell.app.LauncherB",
        )
    }

    val options = listOf(DEFAULT, A, B)

    val current: Flow<String> = context.iconPrefs.data.map { it[KEY_ICON] ?: DEFAULT }

    suspend fun pick(icon: String) {
        if (icon !in aliases) return
        val pm = context.packageManager
        aliases.values.forEach { comp ->
            val name = ComponentName(context, comp)
            val enabled = comp == aliases[icon]
            pm.setComponentEnabledSetting(
                name,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        context.iconPrefs.edit { it[KEY_ICON] = icon }
    }

    /** 应用启动时调用，恢复上次选择的图标。 */
    suspend fun restore() = pick(context.iconPrefs.data.first()[KEY_ICON] ?: DEFAULT)

    fun label(name: String): String = when (name) {
        DEFAULT -> "默认"
        A -> "图标 A"
        B -> "图标 B"
        else -> name
    }
}