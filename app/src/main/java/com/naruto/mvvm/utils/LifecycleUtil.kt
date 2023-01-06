package com.naruto.mvvm.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2021/9/1 0001
 * @Note
 */
object LifecycleUtil {
    /**
     * 添加监听
     *
     * @param lifecycleOwner
     * @param targetObject
     * @param lifecycleEvent
     * @param operation
     * @param <T>
     * @return 用于移除监听
     */
    fun addObserver(
        lifecycleOwner: LifecycleOwner, lifecycleEvent: Lifecycle.Event, operation: () -> Unit
    ): Runnable {
        val wf = WeakReference(operation)
        val lifecycleObserver: LifecycleObserver =
            LifecycleEventObserver { _, event ->
                if (event == lifecycleEvent) wf.get()?.run { invoke() }
            }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(lifecycleObserver)
        val wf_lifecycle = WeakReference(lifecycle)
        val wf_lifecycleObserver = WeakReference(lifecycleObserver)
        return Runnable {
            if (wf_lifecycle.get() != null) wf_lifecycle.get()!!
                .removeObserver(wf_lifecycleObserver.get()!!)
        }
    }

    /**
     * 添加 ON_DESTROY 监听
     *
     * @param lifecycleOwner
     * @param targetObject
     * @param operation
     * @param <T>
     * @return 用于移除监听
     */
    fun addDestroyObserver(lifecycleOwner: LifecycleOwner, operation: () -> Unit): Runnable {
        return addObserver(lifecycleOwner, Lifecycle.Event.ON_DESTROY, operation)
    }
}