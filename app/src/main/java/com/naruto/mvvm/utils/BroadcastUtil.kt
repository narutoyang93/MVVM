package com.naruto.mvvm.utils

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/3/29 0029
 * @Note
 */
/**
 *
 */
fun createPendingIntentFlag(flag: Int): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        PendingIntent.FLAG_IMMUTABLE or flag
    else flag
}

/**
 * 创建广播Intent
 */
fun createBroadcastIntent(context: Context, action: String, cls: Class<*>? = null): Intent {
    val intent = Intent(action)
    if (cls != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {//8.0及以上的静态广播需要指定component
        intent.component = ComponentName(context, cls)
    }
    LogUtils.i("--->create action $action")
    return intent
}

/**
 * 发送本地广播
 */
fun sendLocalBroadcast(context: Context, action: String) {
    doWithLocalBroadcast(context) { sendBroadcast(createBroadcastIntent(context, action)) }
}

fun doWithLocalBroadcast(context: Context, block: LocalBroadcastManager.() -> Unit) {
    block(LocalBroadcastManager.getInstance(context))
}