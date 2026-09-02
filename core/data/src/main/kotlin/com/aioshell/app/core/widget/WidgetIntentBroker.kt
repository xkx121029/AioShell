package com.aioshell.app.core.widget

/** 进程级意图持有器：承载桌面小部件触发的动作，供界面响应。 */
object WidgetIntentBroker {
    /** 是否请求「新建对话」：由 MainActivity 从 intent 置位，会话列表消费后清除。 */
    val pendingNewChat = java.util.concurrent.atomic.AtomicBoolean(false)

    fun consumeNewChat(): Boolean = pendingNewChat.getAndSet(false)
}