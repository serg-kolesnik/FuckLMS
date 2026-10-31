# HTTP API and data model used by FuckLMS

## Overview

The application in this repository does not have a custom backend API. It connects directly to the Moodle LMS at `https://lms.uni-dubna.ru` and interacts with the platform using standard HTML form submissions and browser-like cookies.

The code that performs the network work is located here:

- `app/src/main/java/com/fucklms/app/MoodleClient.kt`
- `app/src/main/java/com/fucklms/app/QrParser.kt`
- `app/src/main/java/com/fucklms/app/AccountManager.kt`

## Endpoints actually used

### 1. Login page

URL:
- `GET https://lms.uni-dubna.ru/login/index.php`

Purpose:
- Load the login page
- Extract hidden field `logintoken`

Example response snippet:

```html
<input type="hidden" name="logintoken" value="abc123xyz" />
```

Fields submitted in the next request:

```text
username
password
rememberusername=0
logintoken
```

Request example:

```http
POST /login/index.php HTTP/1.1
Host: lms.uni-dubna.ru
User-Agent: Mozilla/5.0 (...)
Referer: https://lms.uni-dubna.ru/login/index.php
Origin: https://lms.uni-dubna.ru
Content-Type: application/x-www-form-urlencoded
Cookie: MoodleSession=...;

username=student
password=secret
rememberusername=0
logintoken=abc123xyz
```

### 2. Attendance page

URL:
- `GET https://lms.uni-dubna.ru/mod/attendance/attendance.php?sessid={sessionId}[&qrpass={qrPassword}]`

Purpose:
- Open the attendance page using a QR or session link
- Extract `sesskey` from the page HTML

Response example:

```html
<a href="attendance.php?sessid=123456&sesskey=ABCD1234">...</a>
```

The app extracts the value using regex:

```text
sesskey=([a-zA-Z0-9]+)
```

### 3. Submit attendance

URL:
- `POST https://lms.uni-dubna.ru/mod/attendance/attendance.php?sessid={sessionId}&mark=1`

Fields posted:

```text
sessid
sesskey
status=1
submitbutton=Save
studentpassword (optional)
qrpass (optional)
```

Request example:

```http
POST /mod/attendance/attendance.php?sessid=123456&mark=1 HTTP/1.1
Host: lms.uni-dubna.ru
User-Agent: Mozilla/5.0 (...)
Referer: https://lms.uni-dubna.ru/mod/attendance/attendance.php?sessid=123456
Origin: https://lms.uni-dubna.ru
Content-Type: application/x-www-form-urlencoded
Cookie: MoodleSession=...;

sessid=123456
sesskey=ABCD1234
status=1
submitbutton=Save
studentpassword=qrPassword123
qrpass=qrPassword123
```

## Headers used by the app

```http
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
Referer: <current page>
Origin: https://lms.uni-dubna.ru
Content-Type: application/x-www-form-urlencoded
```

## Cookie model

The app uses `OkHttp` with a custom `CookieJar` implementation. Cookies are stored per host and reapplied on future requests.

This is equivalent to a browser session model, not a token-based API model.

## Data structures used in requests

### `QrData`

Defined in `QrParser.kt`:

```kotlin
data class QrData(val sessionId: String, val qrPassword: String?)
```

This is built from QR query parameters:

- `sessid`
- `qrpass`
- or `password`
- or `studentpassword`

### `UserAccount`

Defined in `AccountManager.kt`:

```kotlin
data class UserAccount(
    val login: String,
    val password: String,
    val isSelected: Boolean = true
)
```

The account list is stored encrypted in `EncryptedSharedPreferences` using a JSON array:

```json
[
  {
    "login": "user@example.com",
    "password": "password",
    "isSelected": true
  }
]
```

## Success criteria used by the app

### Login

The app checks whether the returned HTML contains:

- `name="logintoken"`
- `loginerrormessage`

If found, it interprets this as invalid login or failed authentication.

### Attendance

The app checks the response HTML for markers like:

- `saved`
- `сохранено`
- `успешно`
- `already`
- `уже отмечен`

This determines whether attendance was successfully recorded or already marked.

## Important note

This repository does not implement a dedicated backend service API. The app behaves like a Moodle web automation client:

- loads HTML pages
- reads hidden fields
- submits form data
- stores session cookies
- parses the returned HTML

That is the real network contract used by the application.

## Related files

- `docs/openapi.yaml` — OpenAPI-like description of the Moodle interaction
- `README.md` — compact project overview with the HTTP flow
