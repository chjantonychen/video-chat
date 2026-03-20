package com.videochat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "video_chat_prefs")

class PreferencesManager(private val context: Context) {
    
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("token")
        private val USER_ID_KEY = longPreferencesKey("user_id")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private const val SHARED_PREFS_NAME = "video_chat_prefs"
        private const val TOKEN_KEY_SP = "token"
    }
    
    // 使用SharedPreferences作为同步读取的后备
    private val sharedPrefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    
    val token: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val userId: Flow<Long?> = context.dataStore.data.map { it[USER_ID_KEY] }
    val username: Flow<String?> = context.dataStore.data.map { it[USERNAME_KEY] }
    
    suspend fun saveToken(token: String) {
        // 同时保存到DataStore和SharedPreferences
        context.dataStore.edit { it[TOKEN_KEY] = token }
        sharedPrefs.edit().putString(TOKEN_KEY_SP, token).apply()
    }
    
    suspend fun saveUserInfo(userId: Long, username: String) {
        context.dataStore.edit {
            it[USER_ID_KEY] = userId
            it[USERNAME_KEY] = username
        }
    }
    
    suspend fun clear() {
        android.util.Log.d("PreferencesManager", "clear: clearing all preferences")
        context.dataStore.edit { it.clear() }
        // 使用commit()同步提交，确保清除完成
        val result = sharedPrefs.edit().clear().commit()
        android.util.Log.d("PreferencesManager", "clear: sharedPrefs commit result=$result, token now=${sharedPrefs.getString(TOKEN_KEY_SP, null)}")
    }
    
    suspend fun getTokenSync(): String? = token.first()
    suspend fun getUserIdSync(): Long? = userId.first()
    suspend fun getUsernameSync(): String? = username.first()
    
    // 同步读取token - 使用SharedPreferences
    fun getTokenBlocking(): String? {
        val token = sharedPrefs.getString(TOKEN_KEY_SP, null)
        android.util.Log.d("PreferencesManager", "getTokenBlocking: token=${if (token != null) "exists" else "null"}")
        return token
    }
}
