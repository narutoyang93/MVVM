package com.naruto.mvvm

import android.util.SparseArray
import android.view.View
import com.naruto.mvvm.utils.LogUtils


/**
 * @Description 扩展函数
 * @Author Naruto Yang
 * @CreateDate 2021/9/3 0003
 * @Note
 */
const val clickInterval: Long = 500

@Volatile
var lastClickTime: Long = 0

inline fun <T : View> T.setMyOnClickListener(crossinline block: ((T) -> Unit)) {
    setOnClickListener { v ->
        (v as T).doClick(block)
    }
}

fun <T : View> T.setMyOnClickListener(onClickListener: View.OnClickListener) {
    setOnClickListener { v ->
        (v as T).doClick { view -> onClickListener.onClick(view) }
    }
}

inline fun <T : View> T.doClick(block: ((T) -> Unit)) {
    val time = System.currentTimeMillis()
    if (time - lastClickTime >= clickInterval) {
        lastClickTime = time
        block(this)
    } else LogUtils.e("--->$clickInterval ms内只允许一次点击事件")
}

fun <E> SparseArray<E>.putIfAbsent(key: Int, block: () -> E) {
    get(key) ?: kotlin.run { put(key, block()) }
}