package com.aioshell.app

import android.app.Application
import com.aioshell.app.icon.IconSwitcher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class AioShellApplication : Application() {

    @Inject lateinit var iconSwitcher: IconSwitcher

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 恢复用户选择的启动器图标
        appScope.launch { runCatching { iconSwitcher.restore() } }
    }
}