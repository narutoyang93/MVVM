package com.naruto.mvvm.utils

import android.util.Log
import com.naruto.mvvm.BuildConfig

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2021/12/15 0015
 * @Note
 */
object LogUtils {
    private const val DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss:SSS"
    private val stringBuilder = StringBuilder()
    private fun log(msg: String, block: ((String, String) -> Unit)) {
        Throwable().stackTrace[2].run {
            //block(className, "$msg[$className.$methodName($fileName:$lineNumber)]")
            val content = "[${toString()}]$msg"
            if (BuildConfig.DEBUG) block(BuildConfig.APP_NAME, content)
            else stringBuilder.append("${currentDateTime(DATETIME_FORMAT)} $content\n")
        }
    }

    fun v(msg: String) {
        log(msg) { tag, m -> Log.v(tag, m) }
    }

    fun d(msg: String) {
        log(msg) { tag, m -> Log.d(tag, m) }
    }

    fun i(msg: String) {
        log(msg) { tag, m -> Log.i(tag, m) }
    }

    fun w(msg: String) {
        log(msg) { tag, m -> Log.w(tag, m) }
    }

    fun e(msg: String) {
        log(msg) { tag, m -> Log.e(tag, m) }
    }

    fun e(msg: String, tr: Throwable) {
        log(msg) { tag, m -> Log.e(tag, m, tr) }
    }

    fun writeToFile() {
        if (BuildConfig.DEBUG) return
        GlobalScope.launch {
            FileUtil.writeDataToExternalPublicSpaceFile(
                stringBuilder.toString().toByteArray(), FileUtil.MediaType.FILE, "log/",
                "${todayDate("yyyy-MM-dd")}.txt", true
            ) { stringBuilder.clear() }
        }
    }
}