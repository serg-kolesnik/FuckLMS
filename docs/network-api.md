# Сетевой слой и модель данных FuckLMS

> Приложение **не имеет собственного backend-сервера**. Вся работа идёт напрямую с Moodle LMS
> университета: `https://lms.uni-dubna.ru`. Документ описывает, как именно это происходит,
> и соответствует текущему коду в `app/src/main/java/com/fucklms/app/`.

## Обзор

В репозитории есть два механизма работы с LMS:

| Механизм | Где реализован | Статус | Суть |
|---|---|---|---|
| **WebView-автоматизация** | `MainActivity.kt` → `BrowserScreen()` | ✅ **активный**, используется в UI | Встроенный браузер с JS-инъекциями: сам заполняет форму входа и форму отметки |
| **HTTP-клиент** | `MoodleClient.kt` | ⚠️ вспомогательный, к UI не подключён | Прямые OkHttp-запросы: скачивает HTML, парсит регексами, отправляет формы |

Оба механизма работают с одними и теми же страницами Moodle — контракт один и тот же.

---

## 1. Основной поток — WebView-автоматизация

Источник: `MainActivity.kt`, функция `BrowserScreen()`.

### Подготовка

- Входные данные: QR-ссылка (приходит со вкладки «Сканер») и список выбранных аккаунтов
  (галочки на вкладке «Аккаунты»).
- WebView настраивается: включены JavaScript и DOM Storage, мобильный User-Agent Chrome.
- **Для каждого аккаунта по очереди:**
  1. Все cookies WebView удаляются (`CookieManager.removeAllCookies()` + `flush()`) —
     у каждого аккаунта гарантированно свежая сессия.
  2. В WebView загружается QR-ссылка.

### Логика `onPageFinished` — что делает JS на каждой странице

| Открытая страница | Действие приложения |
|---|---|
| URL содержит `login/index.php` | JS заполняет поля `#username` и `#password` значениями аккаунта и нажимает `#loginbtn` |
| URL содержит `attendance.php` | Статус «Анализ страницы…» — JS проверяет и отправляет форму отметки (см. ниже) |
| URL содержит `success=true` или `attendance/view.php` | Статус «Успех! Переход…» — пауза 1,5 с, переход к следующему аккаунту |
| Прочая | Статус «Редирект…» — QR-ссылка загружается повторно |

### JS на странице отметки (`attendance.php`)

1. **Проверка «уже отмечено».** Если текст страницы содержит `уже была отмечена` или
   `already been marked`, выполняется переход на `view.php?id=122864&success=true`
   (идентификатор зашит в коде как константа), что переводит приложение в ветку успеха.
2. **Выбор статуса.** Среди радиокнопок `input[type="radio"][name="status"]` выбирается та,
   подпись которой содержит одно из слов: `present`, `присутств`, `был`, `была`, `были`,
   `явился`, `явилась`, `уважительн`. Если совпадений нет — выбирается первая радиокнопка.
3. **Пароль из QR.** Если в QR-ссылке был параметр `qrpass`, его значение подставляется в поле
   `input[name="studentpassword"]`.
4. **Сохранение.** Нажимается кнопка отправки (`button[type="submit"]` или
   `input[type="submit"]`) — форма уходит обычным POST, как из браузера.

> Пароль QR извлекается функцией `parseQrPassword()` в `MainActivity.kt` — она читает
> **только** параметр `qrpass`.

### Завершение

Когда выбранные аккаунты закончились, показывается
«Все выбранные аккаунты отмечены!».

---

## 2. Вспомогательный HTTP-клиент — `MoodleClient.kt`

Класс `MoodleClient(username, password)` построен на OkHttp. В текущей версии к интерфейсу
**не подключён**, но полностью реализует тот же сценарий и может использоваться как
программный аналог WebView-потока.

### `login(): String?` (null — успех, строка — текст ошибки)

1. `GET /login/index.php` → из HTML регексом извлекается скрытое поле `logintoken`.
2. `POST /login/index.php` с формой: `username`, `password`, `rememberusername=0`, `logintoken`.
3. Логин считается успешным, если в ответе **нет** `name="logintoken"` и `loginerrormessage`.

### `markAttendance(sessionId, qrPassword): String`

1. `GET /mod/attendance/attendance.php?sessid=…[&qrpass=…]`:
   - если HTML содержит `<title>Вход на сайт` → ошибка «сессия слетела»;
   - регексом `sesskey=([a-zA-Z0-9]+)` извлекается `sesskey`.
2. `POST /mod/attendance/attendance.php?sessid=…&mark=1` с формой:
   `sessid`, `sesskey`, `status=1` (присутствовал), `submitbutton=Save`
   и, при наличии пароля QR, — `studentpassword` + `qrpass`.
3. Результат определяется по HTML ответа:
   - `saved` / `сохранено` / `успешно` → «Успех!»;
   - `already` / `уже отмечен` → «Уже отмечен»;
   - иначе → «Ответ 200, но галочка не встала».

Cookies хранятся в собственной реализации `CookieJar` (класс `CookieManager` в том же файле) —
in-memory карта `host → список cookie`, без persistence.

---

## 3. HTTP-точки Moodle

| Метод | Путь | Назначение |
|---|---|---|
| GET | `/login/index.php` | Страница входа; содержит скрытое поле `logintoken` |
| POST | `/login/index.php` | Отправка формы входа |
| GET | `/mod/attendance/attendance.php?sessid={id}[&qrpass={pass}]` | Страница отметки посещаемости |
| POST | `/mod/attendance/attendance.php?sessid={id}&mark=1` | Сохранение отметки |
| GET | `/mod/attendance/view.php?id={id}&success=true` | Страница модуля посещаемости; WebView использует её как подтверждение успеха |

Заголовки имитируют браузер:

- `User-Agent` — WebView отправляет мобильный Chrome, `MoodleClient` — десктопный Chrome 120;
- `Referer` и `Origin` — соответствующая страница и `https://lms.uni-dubna.ru`;
- `Content-Type: application/x-www-form-urlencoded` для POST-форм.

Модель аутентификации — **сессия на cookies** (`MoodleSession`), как в браузере.
Токенов и собственного REST API приложение не использует.

---

## 4. Разбор QR-кода

`QrParser.kt`:

```kotlin
data class QrData(val sessionId: String, val qrPassword: String?)
```

`parseQrUrl(qrUrl)` принимает из ссылки параметры:

- `sessid` — обязателен, иначе разбор считается неудачным;
- `qrpass`, либо `password`, либо `studentpassword` — пароль QR (в таком порядке приоритета).

> ⚠️ В UI `parseQrUrl()` сейчас **не вызывается**: сканер передаёт в `BrowserScreen` сырую
> ссылку целиком, а пароль QR извлекается упрощённой функцией `parseQrPassword()` из
> `MainActivity.kt` (читает только `qrpass`).

---

## 5. Аккаунты и хранение

`AccountManager.kt` сохраняет аккаунты в `EncryptedSharedPreferences`
(файл `secure_accounts_prefs`, ключ `accounts_list`) в виде JSON-массива:

```json
[
  {
    "login": "ivanov",
    "password": "secret",
    "isSelected": true
  }
]
```

Шифрование:

- мастер-ключ — `MasterKey` со схемой `AES256_GCM`;
- ключи — `AES256_SIV`, значения — `AES256_GCM`.

Операции:

- `addAccount(login, password)` — добавляет; дубликаты по логину игнорируются;
- `removeAccount(login)` — удаляет;
- `toggleAccountSelection(login)` — переключает галочку «использовать аккаунт».

---

## Связанные файлы

- [`openapi.yaml`](openapi.yaml) — OpenAPI-описание HTTP-контракта
- [`app-flow.html`](app-flow.html) — диаграмма работы приложения
- [`README.md`](README.md) — индекс документации
- [`../README.md`](../README.md) — пользовательский обзор проекта
