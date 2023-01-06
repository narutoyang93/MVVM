package com.naruto.mvvm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.widget.Toast
import com.naruto.mvvm.utils.FileUtil
import com.naruto.mvvm.utils.createPendingIntentFlag
import com.naruto.mvvm.utils.currentDateTime
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/1/28 0028
 * @Note
 */
abstract class CrashHandler : Thread.UncaughtExceptionHandler {
    companion object {
        private val DIR_CRASH_LOG: String = "crash/"
        private val DIR_THROWABLE_LOG: String = "throwable/"
    }

    protected abstract fun getContext(): Context

    fun init() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        if (BuildConfig.DEBUG) return
        FileUtil.doWithStoragePermission(false) {
            saveLogInfo("crash", e) {
/*                MyApplication.doByActivity { activity ->
                    DialogFactory.showHintDialog(
                        -1, "很抱歉,程序出现异常,即将重启", "确定",
                        true, activity
                    ) { restartApp() }
                }*/
                showCrashToast()
                restartApp()
            }
        }
    }

    /**
     * 提示即将重启
     */
    private fun showCrashToast() {
        GlobalScope.launch {
            Toast.makeText(getContext(), "很抱歉,程序出现异常,即将重启", Toast.LENGTH_LONG).show()
            delay(1000)
        }
    }


    /**
     * 保存异常日志
     *
     * @param e
     */
    fun saveExceptionInfo(e: Throwable) {
        if (!BuildConfig.DEBUG) FileUtil.doWithStoragePermission(false) {
            saveLogInfo("exception", e, null)
        }
    }

    /**
     * 保存错误信息
     *
     * @param type      日志类型（crash/exception）
     * @param e
     * @param callback 保存日志后的操作
     */
    private fun saveLogInfo(type: String, e: Throwable, callback: ((Boolean) -> Unit)?) {
        val info: Writer = StringWriter()
        val printWriter = PrintWriter(info)
        e.printStackTrace(printWriter)
        var cause = e.cause
        while (cause != null) {
            cause.printStackTrace(printWriter)
            cause = cause.cause
        }
        val result = info.toString() + collectCrashDeviceInfo()
        //保存文件
        val relativePath = when (type) {
            "crash" -> DIR_CRASH_LOG
            else -> DIR_THROWABLE_LOG
        }
        val fileName: String = currentDateTime("yyyyMMdd_HHmmssSSS") + ".txt"
        FileUtil.writeDataToExternalPublicSpaceFile(
            result.toByteArray(), FileUtil.MediaType.FILE, relativePath, fileName, false, callback
        )
    }


    /**
     * 重启app
     */
    private fun restartApp() {
        val context = getContext()
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val restartIntent = PendingIntent.getActivity(
            context, 0, intent, createPendingIntentFlag(PendingIntent.FLAG_ONE_SHOT)
        )
        //重启应用
        val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mgr.setExactAndAllowWhileIdle(
                AlarmManager.RTC,
                System.currentTimeMillis(),
                restartIntent
            )
        } else {
            mgr.setExact(AlarmManager.RTC, System.currentTimeMillis(), restartIntent)
        }

        //清空Activity栈,防止系统自动重启至崩溃页面,导致崩溃再次出现.
        MyApplication.finishAllActivity()
        //退出程序
        Process.killProcess(Process.myPid())
        System.exit(0)
        System.gc()
    }


    /**
     * 获取系统信息
     */
    private fun collectCrashDeviceInfo(): String {
        val sb = StringBuilder("\n/****************系统信息****************/\n")
        try {
            val context: Context = getContext()
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
            if (pi != null) {
                appendInfo(sb, "版本名称", pi.versionName)
                appendInfo(sb, "版本号", pi.versionCode)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        //使用反射来收集设备信息
        val fields = Build::class.java.declaredFields
        for (field in fields) {
            try {
                field.isAccessible = true
                appendInfo(sb, field.name, field[null])
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return sb.toString()
    }

    /**
     * @param sb
     * @param label
     * @param value
     */
    private fun appendInfo(sb: StringBuilder, label: String, value: Any) {
        sb.append(label).append(":").append(value).append("\n")
    }
}