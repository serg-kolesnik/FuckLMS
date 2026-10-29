package com.fucklms.app

import okhttp3.*
import java.io.IOException

class MoodleClient(
    private val username: String,
    private val password: String
) {
    // Встроенный менеджер куки, который сохраняет сессии (MoodleSession)
    private val cookieManager = CookieManager()

    // Клиент с правильными заголовками браузера
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieManager)
        .followRedirects(true) // Разрешаем редиректы, как в браузере
        .build()

    fun login(): String? {
        val loginUrl = "https://lms.uni-dubna.ru/login/index.php"

        try {
            // 1. GET-запрос: загружаем страницу логина
            val getRequest = Request.Builder()
                .url(loginUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            var logintoken = ""

            client.newCall(getRequest).execute().use { response ->
                val html = response.body?.string() ?: ""
                val regex = Regex("<input[^>]*name=[\"']logintoken[\"'][^>]*value=[\"']([^\"']+)[\"']")
                val match = regex.find(html)
                if (match != null) {
                    logintoken = match.groupValues[1]
                } else {
                    return "Ошибка: не найден logintoken на странице"
                }
            }

            // 2. POST-запрос: отправляем логин и пароль (куки сохранятся автоматически)
            val formBody = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("rememberusername", "0")
                .add("logintoken", logintoken)
                .build()

            val postRequest = Request.Builder()
                .url(loginUrl)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", loginUrl)
                .header("Origin", "https://lms.uni-dubna.ru")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            client.newCall(postRequest).execute().use { response ->
                val html = response.body?.string() ?: ""

                // Если в ответе снова есть logintoken, значит, нас не пустили
                if (html.contains("name=\"logintoken\"") || html.contains("loginerrormessage")) {
                    return "Неверный логин/пароль или сбой куки"
                }

                return null // УСПЕХ! Куки сохранены в cookieManager
            }
        } catch (e: IOException) {
            return "Сетевая ошибка: ${e.message}"
        }
    }

    fun markAttendance(sessionId: String, qrPassword: String?): String {
        val baseUrl = "https://lms.uni-dubna.ru"
        val attendanceUrl = "$baseUrl/mod/attendance/attendance.php?sessid=$sessionId" +
                if (!qrPassword.isNullOrEmpty()) "&qrpass=$qrPassword" else ""

        try {
            // 3. GET-запрос: заходим по ссылке из QR (отправляем сохраненные куки)
            val getRequest = Request.Builder()
                .url(attendanceUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            var sesskey = ""

            client.newCall(getRequest).execute().use { response ->
                val html = response.body?.string() ?: ""

                // Проверяем, не выкинуло ли нас обратно на страницу логина
                if (html.contains("<title>Вход на сайт")) {
                    return "Ошибка: сессия слетела"
                }

                // Ищем sesskey
                val regex = Regex("sesskey=([a-zA-Z0-9]+)")
                val match = regex.find(html)
                if (match != null) {
                    sesskey = match.groupValues[1]
                } else {
                    return "Не найден sesskey. (Возможно, курс закрыт)"
                }
            }

            // 4. POST-запрос: отправляем форму отметки
            val formBody = FormBody.Builder()
                .add("sessid", sessionId)
                .add("sesskey", sesskey)
                .add("status", "1") // 1 - Присутствовал
                .add("submitbutton", "Save")
                .apply {
                    if (!qrPassword.isNullOrEmpty()) {
                        add("studentpassword", qrPassword)
                        add("qrpass", qrPassword)
                    }
                }
                .build()

            val postRequest = Request.Builder()
                .url("$baseUrl/mod/attendance/attendance.php?sessid=$sessionId&mark=1")
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", attendanceUrl)
                .header("Origin", "https://lms.uni-dubna.ru")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            client.newCall(postRequest).execute().use { response ->
                val html = response.body?.string()?.lowercase() ?: ""
                return if (html.contains("saved") || html.contains("сохранено") || html.contains("успешно")) {
                    "Успех!"
                } else if (html.contains("already") || html.contains("уже отмечен")) {
                    "Уже отмечен"
                } else {
                    "Ответ 200, но галочка не встала"
                }
            }
        } catch (e: Exception) {
            return "Сетевая ошибка: ${e.message}"
        }
    }
}

// Менеджер куки (как в браузере)
class CookieManager : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlHost = url.host
        val cookieList = cookieStore.getOrPut(urlHost) { mutableListOf() }
        cookieList.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host] ?: emptyList()
    }
}