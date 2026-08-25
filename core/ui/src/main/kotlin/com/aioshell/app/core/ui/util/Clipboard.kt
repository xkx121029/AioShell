package com.aioshell.app.core.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** 复制文本到系统剪贴板。 */
fun Context.copyTextToClipboard(label: String, text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}