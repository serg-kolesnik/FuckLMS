# FuckLMS

Приложение работает как клиент Moodle, а не как обычный REST-клиент.

## Как оно работает

1. Пользователь добавляет логин и пароль или выбирает сохраненный аккаунт.
2. Приложение читает QR-код и извлекает параметры `sessid` и, при необходимости, пароль.
3. Оно делает GET на `https://lms.uni-dubna.ru/login/index.php`, чтобы получить скрытый `logintoken`.
4. Затем отправляет POST с формой логина:
   - `username`
   - `password`
   - `rememberusername=0`
   - `logintoken`
5. Moodle возвращает cookies, которые приложение сохраняет и использует дальше.
6. После входа приложение открывает страницу посещаемости:
   - `GET https://lms.uni-dubna.ru/mod/attendance/attendance.php?sessid={sessionId}`
   - извлекает `sesskey` из HTML страницы
7. Далее отправляет форму отметки присутствия:
   - `sessid`
   - `sesskey`
   - `status=1`
   - `submitbutton=Save`
   - `studentpassword` / `qrpass` — если есть в QR
8. В ответе приложение анализирует HTML и определяет результат:
   - успех
   - уже отмечен
   - ошибка/сессия сброшена

## Основные эндпоинты

| Метод | URL | Назначение |
|---|---|---|
| GET | `https://lms.uni-dubna.ru/login/index.php` | Получить страницу логина и `logintoken` |
| POST | `https://lms.uni-dubna.ru/login/index.php` | Выполнить авторизацию |
| GET | `https://lms.uni-dubna.ru/mod/attendance/attendance.php?sessid={sessionId}` | Открыть страницу посещаемости и получить `sesskey` |
| POST | `https://lms.uni-dubna.ru/mod/attendance/attendance.php?sessid={sessionId}&mark=1` | Отметить присутствие |

## Ключевые структуры данных

- `QrData(sessionId: String, qrPassword: String?)`
  - из QR берутся `sessid`, `qrpass`, `password` или `studentpassword`
- `UserAccount(login: String, password: String, isSelected: Boolean = true)`
  - хранится в `EncryptedSharedPreferences`

## Важное замечание

Это приложение не вызывает собственный backend API. Оно работает как браузерный клиент Moodle:

- использует HTML-формы
- хранит cookies
- парсит HTML
- отправляет данные через обычные HTTP-запросы

Полная документация по сетевому слою находится в `docs/network-api.md` и `docs/openapi.yaml`.
