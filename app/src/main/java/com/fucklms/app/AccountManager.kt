package com.fucklms.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class UserAccount(
    val login: String,
    val password: String,
    val isSelected: Boolean = true // По умолчанию аккаунт выбран
)

class AccountManager(context: Context) {

    private val sharedPreferences: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "secure_accounts_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getAccounts(): List<UserAccount> {
        val jsonString = sharedPreferences.getString("accounts_list", "[]")
        val accountsList = mutableListOf<UserAccount>()

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                accountsList.add(
                    UserAccount(
                        login = obj.getString("login"),
                        password = obj.getString("password"),
                        isSelected = if (obj.has("isSelected")) obj.getBoolean("isSelected") else true
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return accountsList
    }

    fun addAccount(login: String, password: String) {
        val currentAccounts = getAccounts().toMutableList()
        if (currentAccounts.none { it.login == login }) {
            currentAccounts.add(UserAccount(login, password, isSelected = true))
            saveAccounts(currentAccounts)
        }
    }

    fun removeAccount(login: String) {
        val currentAccounts = getAccounts().filter { it.login != login }
        saveAccounts(currentAccounts)
    }

    // НОВЫЙ МЕТОД: переключение галочки
    fun toggleAccountSelection(login: String) {
        val currentAccounts = getAccounts().map {
            if (it.login == login) it.copy(isSelected = !it.isSelected) else it
        }
        saveAccounts(currentAccounts)
    }

    private fun saveAccounts(accounts: List<UserAccount>) {
        val jsonArray = JSONArray()
        for (account in accounts) {
            val obj = JSONObject()
            obj.put("login", account.login)
            obj.put("password", account.password)
            obj.put("isSelected", account.isSelected)
            jsonArray.put(obj)
        }
        sharedPreferences.edit().putString("accounts_list", jsonArray.toString()).apply()
    }
}