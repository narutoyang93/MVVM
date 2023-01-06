package com.naruto.mvvm

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.SparseArray
import android.widget.Toast
import androidx.core.util.forEach
import com.naruto.mvvm.base.BaseActivity
import java.lang.ref.WeakReference

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/3/28 0028
 * @Note
 */
class MyApplication : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        crashHandler.init()
        registerActivityLifecycleCallbacks(this)//监听activity生命周期
    }

    companion object {
        lateinit var context: Context
        val crashHandler = object : CrashHandler() {
            override fun getContext(): Context = context
        }
        private var currentActivity: WeakReference<Activity>? = null
        private val activityMap by lazy { SparseArray<Activity>() }

        fun toast(msg: String, short: Boolean = true) {
            val duration: Int = if (short) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            Toast.makeText(context, msg, duration).show()
        }


        /**
         * 利用当前活动的Activity执行操作
         *
         * @param operation
         * @return Activity的hashCode
         */
        fun doByActivity(operation: (BaseActivity) -> Unit) {
            val activity = currentActivity?.get()
            if (activity != null) operation(activity as BaseActivity)
        }

        /**
         * 执行需要权限的操作
         *
         * @param callBack
         */
        fun doWithPermission(callBack: BaseActivity.RequestPermissionsCallBack) {
            doByActivity { activity -> activity.doWithPermission(callBack) }
        }

        /**
         * 关闭所有activity
         */
        fun finishAllActivity() {
            val list = mutableListOf<WeakReference<Activity>>()
            activityMap.forEach { key, value -> list.add(WeakReference(value)) }//Activity finish会触发activityMap执行remove，故不能直接在forEach里面执行finish
            list.forEach { it.get()?.finish() }
        }
    }


    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activityMap.put(activity.hashCode(), activity)
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        currentActivity = WeakReference(activity) //记录当前正在活动的activity
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        activityMap.remove(activity.hashCode())
    }


}