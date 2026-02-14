package com.styropyr0.kairos

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

internal object KairosCacheHelper {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    fun init(context: Application) {
        sharedPreferences = context.getSharedPreferences(Constant.PREF_IDENTIFIER, Context.MODE_PRIVATE)
        editor = sharedPreferences.edit()
    }

    fun isInit() = ::sharedPreferences.isInitialized

    @Suppress("UNCHECKED_CAST")
    fun <T> getPref(key: String): T? {
        return try {
            sharedPreferences.all[key] as T
        } catch (_: Exception) {
            null
        }
    }

    fun isPrefExists(key: String): Boolean {
        return sharedPreferences.contains(key)
    }

    fun savePref(key: String, value: Any?) {
        delete(key)
        if (value is Boolean) {
            editor.putBoolean(key, value)
        } else if (value is Int) {
            editor.putInt(key, value)
        } else if (value is Float) {
            editor.putFloat(key, value)
        } else if (value is Long) {
            editor.putLong(key, value)
        } else if (value is String) {
            editor.putString(key, value as String?)
        } else if (value is Enum<*>) {
            editor.putString(key, value.toString())
        } else if (value != null) {
            throw RuntimeException("Attempting to save non-primitive preference")
        }
        editor.commit()
    }

    fun delete(key: String) {
        if (sharedPreferences.contains(key)) {
            editor.remove(key).commit()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getPref(key: String, defValue: T): T {
        return when (defValue) {
            is String -> sharedPreferences.getString(key, defValue) as T
            is Int -> sharedPreferences.getInt(key, defValue) as T
            is Boolean -> sharedPreferences.getBoolean(key, defValue) as T
            is Float -> sharedPreferences.getFloat(key, defValue) as T
            is Long -> sharedPreferences.getLong(key, defValue) as T
            else -> throw IllegalArgumentException("Unsupported type")
        }
    }

}