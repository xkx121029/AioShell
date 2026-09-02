package com.aioshell.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.aioshell.app.MainActivity
import com.aioshell.app.R

/**
 * 桌面快捷小部件：一键唤起 AioShell。
 * 提供「新建对话」与「打开应用」两个快捷动作，跟随系统深色/浅色主题着色。
 */
class QuickActionWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context)) }
    }

    companion object {
        const val ACTION_NEW_CHAT = "com.aioshell.app.action.WIDGET_NEW_CHAT"
        const val ACTION_OPEN = "com.aioshell.app.action.WIDGET_OPEN"

        /** 手动刷新小部件（深/浅色切换后调用）。 */
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, QuickActionWidget::class.java))
            ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context)) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_action)
            val newChat = Intent(context, MainActivity::class.java).apply {
                action = ACTION_NEW_CHAT
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            views.setOnClickPendingIntent(
                R.id.widget_new_chat,
                android.app.PendingIntent.getActivity(context, 0, newChat, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE),
            )
            val open = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            views.setOnClickPendingIntent(
                R.id.widget_open,
                android.app.PendingIntent.getActivity(context, 1, open, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE),
            )
            return views
        }
    }
}