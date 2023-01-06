package com.naruto.mvvm.utils

import androidx.core.math.MathUtils
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/5/19 0019
 * @Note
 */
object RandomUtil {

    /**
     * 生成一个固定长度的数字
     *
     * @param length 数字长度(大于1)
     * @return
     */
    fun randomNum(length: Int): Long {
        val min = 10.0.pow(length - 1).toLong()
        val max = min * 10 - 1
        return min + (Math.random() * max).toLong()
    }

    /**
     * 生成一个一定范围内的随机数
     * @param min Int
     * @param max Int
     * @return Int
     */
    fun randomInt(min: Int, max: Int): Int {
        return min + (Math.random() * max).toInt()
    }

    /**
     * 随机Boolean
     *
     * @return
     */
    fun randomBoolean(): Boolean {
        return Math.random() < 0.5
    }

    /**
     * 生成数字字母混合随机字符串
     *
     * @param length 长度
     * @return
     */
    fun randomStr(length: Int): String {
        var value = ""
        val random = Random()
        for (i in 0 until length) {
            if (randomBoolean()) {// 字符串
                val choice = if (randomBoolean()) 65 else 97 //取得大写字母还是小写字母
                value += (choice + random.nextInt(26)).toChar()
            } else {// 数字
                value += random.nextInt(10)
            }
        }
        return value
    }

    /**
     * 生成字母混合随机字符串
     *
     * @param length 长度
     * @return
     */
    fun randomLetterStr(length: Int): String {
        var value = ""
        val random = Random()
        for (i in 0 until length) {
            val choice = if (randomBoolean()) 65 else 97 //取得大写字母还是小写字母
            value += (choice + random.nextInt(26)).toChar()
        }
        return value
    }
}