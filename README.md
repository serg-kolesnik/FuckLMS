# FuckLMS

Небольшое Android-приложение для работы с Moodle LMS через обычный веб-интерфейс.

## Что делает приложение

Приложение не имеет собственного серверного API. Вместо этого оно:

- открывает страницы Moodle через HTTP
- получает скрытые поля вроде `logintoken` и `sesskey`
- отправляет форму логина и отметки посещаемости
- хранит cookies как обычный браузер
- сохраняет учетные данные локально в защищенном хранилище

## Основные HTTP-запросы

| Endpoint | Метод | Что отправляется | Назначение |
|---|---|---|---|
| `https://lms.uni-dubna.ru/login/index.php` | GET | — | Получить страницу логина и найти `logintoken` |
| `https://lms.uni-dubna.ru/login/index.php` | POST | `username`, `password`, `rememberusername`, `logintoken` | Авторизация пользователя в Moodle |
| `https://lms.uni-dubna.ru/mod/attendance/attendance.php?sessid={sessionId}` | GET | `sessid` | Загрузить страницу посещаемости и извлечь `sesskey` |
| `https://lms.uni-dubna.ru/mod/attendance/attendance.php?sessid={sessionId}&mark=1` | POST | `sessid`, `sesskey`, `status=1`, `submitbutton=Save`, `studentpassword?`, `qrpass?` | Отметить пользователя как присутствовавшего |

## Поля форм

### 1. Авторизация

Тело запроса:

```text
username=<логин>
password=<пароль>
rememberusername=0
logintoken=<значение из HTML>
```

### 2. Отметка посещаемости

Тело запроса:

```text
sessid=<sessionId>
sesskey=<значение из HTML>
status=1
submitbutton=Save
studentpassword=<пароль из QR, если есть>
qrpass=<пароль из QR, если есть>
```

## Заголовки и сессия

Приложение отправляет заголовки, похожие на браузер:

```http
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
Referer: https://lms.uni-dubna.ru/...
Origin: https://lms.uni-dubna.ru
Content-Type: application/x-www-form-urlencoded
```

Cookie хранятся через `OkHttp CookieJar`, то есть приложение использует механизм, похожий на обычную веб-сессию браузера.

## Структуры данных в приложении

### `QrData`

```kotlin
data class QrData(val sessionId: String, val qrPassword: String?)
```

Используется для разбора QR-кода. Из него приложение берет:

- `sessid`
- `qrpass`, либо `password`, либо `studentpassword`

### `UserAccount`

```kotlin
data class UserAccount(
    val login: String,
    val password: String,
    val isSelected: Boolean = true
)
```

Список учетных записей сохраняется в `EncryptedSharedPreferences` в формате JSON:

```json
[
  {
    "login": "student@example.com",
    "password": "secret",
    "isSelected": true
  }
]
```

## Логика обработки ответов

### Авторизация

Приложение считает вход успешным, если после POST не появляется:

- `logintoken`
- `loginerrormessage`

### Отметка посещаемости

После POST приложение анализирует HTML-ответ и ищет признаки успеха:

- `saved`
- `сохранено`
- `успешно`

Если встречаются признаки, что посетитель уже отмечен:

- `already`
- `уже отмечен`

то приложение сообщает, что посещение уже зарегистрировано.

## Где находится сетевой код

- `app/src/main/java/com/fucklms/app/MoodleClient.kt`
- `app/src/main/java/com/fucklms/app/QrParser.kt`
- `app/src/main/java/com/fucklms/app/AccountManager.kt`

## Важная оговорка

Это не REST API приложения и не собственный backend. По факту это клиент Moodle, который работает как браузер:

- открывает HTML-страницы,
- читает hidden-поля,
- отправляет формы,
- хранит cookies,
- анализирует HTML-ответы.

Полная техническая документация по сетевому слою находится в:

- `docs/network-api.md`
- `docs/openapi.yaml`
