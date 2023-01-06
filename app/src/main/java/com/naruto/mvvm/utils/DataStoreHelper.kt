package com.naruto.mvvm.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.naruto.mvvm.MyApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.reduce

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2021/12/3 0003
 * @Note
 */
const val DEF_STRING = ""
const val DEF_INT = -1
const val DEF_LONG: Long = -1
const val DEF_FLOAT = -1.0f
const val DEF_DOUBLE = -1.0

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "MyData")

object CommonDataStore : DataStoreHelper(MyApplication.context.dataStore)

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/4/28 0028
 * @Note
 */
open class DataStoreHelper(private val dataStore: DataStore<Preferences>) {
    fun getIntValue(key: String, def: Int = DEF_INT): Flow<Int> {
        return getValue(intPreferencesKey(key), def)
    }

    suspend fun setIntValue(key: String, value: Int) {
        setValue(intPreferencesKey(key), value)
    }

    fun getFloatValue(key: String, def: Float = DEF_FLOAT): Flow<Float> {
        return getValue(floatPreferencesKey(key), def)
    }

    suspend fun setFloatValue(key: String, value: Float) {
        setValue(floatPreferencesKey(key), value)
    }

    fun getLongValue(key: String, def: Long = DEF_LONG): Flow<Long> {
        return getValue(longPreferencesKey(key), def)
    }

    suspend fun setLongValue(key: String, value: Long) {
        setValue(longPreferencesKey(key), value)
    }

    fun getDoubleValue(key: String, def: Double = DEF_DOUBLE): Flow<Double> {
        return getValue(doublePreferencesKey(key), def)
    }

    suspend fun setDoubleValue(key: String, value: Double) {
        setValue(doublePreferencesKey(key), value)
    }

    fun getStringValue(key: String, def: String = DEF_STRING)
            : Flow<String> {
        return getValue(stringPreferencesKey(key), def)
    }

    suspend fun setStringValue(key: String, value: String) {
        setValue(stringPreferencesKey(key), value)
    }

    private fun <T> getValue(key: Preferences.Key<T>, def: T): Flow<T> {
        return dataStore.data.map { it[key] ?: def }
    }

    suspend fun <T> setValue(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    suspend fun <T> remove(key: Preferences.Key<T>) {
        dataStore.edit { it.remove(key) }
    }

    suspend fun <T> clear() {
        dataStore.edit { it.clear() }
    }
}