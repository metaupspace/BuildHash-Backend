 # Swagger Manual Test Cases — Phase 0 (Account & Identity)

Swagger UI: `http://localhost:8080/swagger-ui.html`

**Getting the OTP in dev**: `POST /auth/otp/send` publishes to a RabbitMQ queue (`otp.dispatch`)
rather than dispatching inline; a listener consumes it and calls `SmsOtpSender` (still a stub —
no real SMS provider in Phase 0), which logs the OTP. Read it from the application console log
right after calling send (delivery is near-instant locally, but requires `docker compose up` to
have RabbitMQ running — see `docker-compose.yml`):
```
OTP for +919876543210: 483920
```

**Using an access token in Swagger**: click **Authorize** (top right) and paste `<accessToken>` — Swagger sends it as
`Authorization: Bearer <accessToken>` automatically on every subsequent call. `/auth/**` endpoints don't need this.

---

## 1. `POST /auth/otp/send`

### 1.1 Happy path (new phone number)
Request:
```json
{ "phone": "+919876543210" }
```
Expected: `200 OK`
```json
{ "message": "OTP sent", "expiresInSeconds": 300, "existingUser": false }
```
Check the console log for the OTP value — you'll need it for section 2.
`existingUser` is a Bloom-filter hint (advisory only, can false-positive, never false-negatives
once a phone has completed a verify) — client UI can use it to show "Welcome back" vs "Create
account" copy before OTP verification completes.

### 1.1b Send OTP to an already-verified phone number
Repeat 1.1's request for the SAME phone number used in section 2.1 (after it has completed at
least one successful verify).
Expected: `200 OK`, `"existingUser": true`

### 1.2 Invalid phone format
Request:
```json
{ "phone": "not-a-phone" }
```
Expected: `400 Bad Request`, `code: VALIDATION_FAILED`

### 1.3 Resend cooldown
Repeat 1.1 immediately (same phone, within ~60 seconds).
Expected: `429 Too Many Requests`, `code: OTP_COOLDOWN`

### 1.4 Hourly rate limit
Send 1.1 six times in a row for the same phone (wait out the cooldown between each, or use a fresh phone number and hit send 6 times back-to-back — cooldown blocks calls 2-5 if too fast, so space them ~61s apart, or just trust the automated test `sendOtp_exceedingHourlyRateLimit_returnsTooManyRequests` which does this without waiting).
Expected on the 6th send: `429 Too Many Requests`, `code: OTP_RATE_LIMIT_EXCEEDED`

---

## 2. `POST /auth/otp/verify`

### 2.1 Happy path (first-time login — creates the account)
Request (use the OTP from section 1.1's log line):
```json
{ "phone": "+919876543210", "otp": "483920", "deviceFingerprint": "Chrome on MacBook" }
```
Expected: `200 OK`
```json
{ "accessToken": "eyJ...", "refreshToken": "eyJ...", "tokenType": "Bearer", "expiresInSeconds": 900 }
```
Save both tokens — used throughout the rest of this document.

### 2.2 OTP expired / never requested
Request:
```json
{ "phone": "+919999999999", "otp": "123456" }
```
Expected: `400 Bad Request`, `code: OTP_EXPIRED`

### 2.3 Wrong OTP, three times in a row, then locked out
Send a fresh OTP to a phone (section 1.1), then call verify with an intentionally wrong code three times:
```json
{ "phone": "+919876500001", "otp": "000000" }
```
Expected each of the 3 calls: `401 Unauthorized`, `code: OTP_INCORRECT`

Then call verify a 4th time — **even with the correct OTP**:
Expected: `423 Locked`, `code: OTP_LOCKED`

(The 3rd wrong attempt itself still returns 401 — lockout kicks in starting from the attempt *after* the 3rd failure.)

Send a new OTP to the same phone to clear the lockout and try again.

---

## 3. `POST /auth/google`

### 3.1 Happy path
Requires a real Google ID token from a client SDK signed for the `GOOGLE_CLIENT_ID` configured in your environment —
not practical to hand-craft in Swagger. Covered instead by the automated test `googleSignIn_happyPath_issuesTokens`,
which mocks the verifier. Request shape for reference:
```json
{ "idToken": "<google-id-token>", "deviceFingerprint": "Pixel 8" }
```
Expected: `200 OK` with the same `AuthTokensResponse` shape as section 2.1.

### 3.2 Invalid/garbage token
Request:
```json
{ "idToken": "garbage" }
```
Expected: `401 Unauthorized`, `code: INVALID_GOOGLE_TOKEN`

---

## 4. `POST /auth/guest`

### 4.1 Happy path
No request body needed — just hit **Execute**.
Expected: `200 OK`
```json
{ "accessToken": "eyJ...", "refreshToken": null, "tokenType": "Bearer", "expiresInSeconds": 86400 }
```
Note `refreshToken` is `null` — the guest token is intentionally non-refreshable; re-call `/auth/guest` for a new one.

Authorize Swagger with this token and try `GET /users/me` — expect `401` (guest role can't reach `/users/**`).

---

## 5. `POST /auth/refresh`

### 5.1 Happy path (rotation)
Using the `refreshToken` from section 2.1:
```json
{ "refreshToken": "<refreshToken from 2.1>" }
```
Expected: `200 OK`, a brand-new `accessToken` **and** `refreshToken` (both different from the ones you sent in).

### 5.2 Reusing an old (already-rotated) refresh token
Immediately call 5.1 again with the *same original* `refreshToken` you just rotated away.
Expected: `401 Unauthorized`, `code: REFRESH_TOKEN_REUSE_DETECTED`
(The device is now revoked — even the token issued in 5.1 will stop working after this.)

### 5.3 Garbage token
```json
{ "refreshToken": "not-a-real-jwt" }
```
Expected: `401 Unauthorized`, `code: INVALID_TOKEN`

---

## 6. `GET /users/me`

### 6.1 Happy path
Authorize with an access token from section 2.1, then Execute.
Expected: `200 OK`
```json
{ "id": "...", "phone": "+919876543210", "email": null, "name": null, "businessName": null, "gstNumber": null, "gstinStatus": null }
```

### 6.2 No/invalid token
Remove authorization (or use a garbage token) and Execute.
Expected: `401 Unauthorized`, `code: UNAUTHENTICATED`

---

## 7. `PUT /users/me`

### 7.1 Happy path — valid GSTIN
```json
{ "name": "Ramesh Sharma", "businessName": "Sharma Traders", "gstNumber": "27AAAPZ1234C1Z5" }
```
Expected: `200 OK`, echoes back the fields, `gstinStatus: "PENDING"` (format-valid, not externally verified yet).

### 7.2 Partial update
```json
{ "businessName": "Sharma Traders & Sons" }
```
Expected: `200 OK` — `name` and `gstNumber` from 7.1 remain unchanged (`null` fields are left as-is, not cleared).

### 7.3 Invalid GST number format
```json
{ "gstNumber": "NOT-A-GSTIN" }
```
Expected: `400 Bad Request`, `code: VALIDATION_FAILED`, message mentions `gstNumber`

---

## 8. `GET /users/me/devices`

### 8.1 Happy path
Authorize with a valid access token, Execute.
Expected: `200 OK`, an array containing at least the device you logged in with (`deviceFingerprint` matches what you sent in section 2.1).

---

## 9. `DELETE /users/me/devices/{deviceId}`

### 9.1 Happy path — revoke your own device
Use the `id` from section 8.1's response.
Expected: `204 No Content`

Then retry `POST /auth/refresh` (section 5.1) with that device's refresh token.
Expected: `401 Unauthorized`, `code: INVALID_REFRESH_TOKEN`

### 9.2 Revoke a device that isn't yours
Log in as a second user (different phone number) to get a second access token. Using **user B's** access token, try
to `DELETE /users/me/devices/{deviceId}` with **user A's** device ID from section 8.1.
Expected: `404 Not Found` (doesn't confirm the device exists at all to a non-owner)

---

## 10. `POST /users/me/logout-all-devices`

### 10.1 Happy path
Log in, note the `refreshToken`, then Execute this endpoint with the access token.
Expected: `200 OK`, a brand-new `AuthTokensResponse` for the current device.

Then retry `POST /auth/refresh` with the **original** (pre-logout-all) refresh token.
Expected: `401 Unauthorized` — that session was bulk-invalidated.

Then retry `POST /auth/refresh` with the **new** refresh token from this response.
Expected: `200 OK` — the current device got a fresh session, so you're not locked out of the action you just took.

---

## 11. `GET /users/me/login-history`

### 11.1 Happy path
After at least one OTP login, Execute with a valid access token.
Expected: `200 OK`, an array with at least one entry:
```json
[{ "id": "...", "eventType": "OTP", "ipAddress": "...", "deviceFingerprint": "Chrome on MacBook", "createdAt": "..." }]
```

### 11.2 No token
Expected: `401 Unauthorized`, `code: UNAUTHENTICATED`

---

## 12. `GET /actuator/health`

### 12.1 Happy path
No auth needed.
Expected: `200 OK`, `{"status":"UP"}` (or with component details if `management.endpoint.health.show-details` is enabled, as it is in the `dev` profile).
