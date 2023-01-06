package com.naruto.mvvm.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/3/29 0029
 * @Note
 */
fun now(): Long = System.currentTimeMillis()
fun todayDate(pattern: String = "yyyy/MM/dd"): String = SimpleDateFormat(pattern).format(Date())
fun currentDateTime(pattern: String = "yyyy/MM/dd HH:mm:ss"): String =
    SimpleDateFormat(pattern).format(Date())