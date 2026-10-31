# FuckLMS

This Android application is a client for the Moodle LMS at `https://lms.uni-dubna.ru`. It does not expose its own backend API or use Retrofit. Instead, it performs browser-like HTTP requests to Moodle and parses HTML forms.

## Network flow

### 1) Login

Request:
- GET `https://lms.uni-dubna.ru/login/index.php`
- Extract hidden field `logintoken` from HTML
- POST to the same URL with:
  - `username`
  - `password`
  - `rememberusername=0`
  - `logintoken`

Headers:
- `User-Agent`: browser-like string
- `Referer`: `https://lms.uni-dubna.ru/login/index.php`
- `Origin`: `https://lms.uni-dubna.ru`
- `Content-Type: application/x-www-form-urlencoded`

Cookie handling:
- Moodle session cookies are stored in `CookieJar`
- They are reused for all subsequent authenticated requests

### 2) Attendance mark

Request to open attendance page:
- GET `https://lms.uni-dubna.ru/mod/attendance/attendance.php?sessid={sessionId}[&qrpass={qrPassword}]`
- Extract `sesskey` from the returned HTML

Submit attendance:
- POST `https://lms.uni-dubna.ru/mod/attendance/attendance.php?sessid={sessionId}&mark=1`
- Form fields:
  - `sessid`
  - `sesskey`
  - `status=1`
  - `submitbutton=Save`
  - optional `studentpassword`
  - optional `qrpass`

### 3) QR parsing

The app extracts data from a QR URL using query parameters:
- `sessid`
- `qrpass` or `password` or `studentpassword`

Data model:
- `QrData(sessionId: String, qrPassword: String?)`

## Local data model

`AccountManager.kt` stores the user list in encrypted shared preferences:

```json
[
  {
    "login": "student@example.com",
    "password": "secret",
    "isSelected": true
  }
]
```

Structure:
- `UserAccount(login: String, password: String, isSelected: Boolean = true)`

## Files involved

- `app/src/main/java/com/fucklms/app/MoodleClient.kt` — HTTP client and form submission logic
- `app/src/main/java/com/fucklms/app/AccountManager.kt` — encrypted local storage of accounts
- `app/src/main/java/com/fucklms/app/QrParser.kt` — parsing QR and session data
- `docs/openapi.yaml` — Swagger/OpenAPI outline of the network flow
- `docs/network-api.md` — readable markdown documentation of the actual endpoints and fields

## Notes

This app is not a traditional REST API client. It acts as a Moodle web client that imitates browser behavior: HTML pages, form POSTs, and cookie-based session handling.
