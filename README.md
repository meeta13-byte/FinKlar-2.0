# FinKlar 2.0

FinKlar 2.0 is an offline-first personal finance manager for Android. It tracks transactions, budgets, savings goals, wallets and investments locally in an encrypted database, with automatic transaction detection from SMS, receipt OCR, voice input, and an AI financial assistant.

Package: `com.example.antigravityfinance` | Min SDK 24, Target/Compile SDK 36, JDK 17

---

## Key Features

* **Encrypted local storage:** Room + SQLCipher. Transactions, budgets, goals, investments, wallets stored on-device.
* **PIN lock gate:** Custom PIN pad on startup, backed by `SecurityHelper` + EncryptedSharedPreferences.
* **AI assistant:** Local rule-based answers + Gemini 1.5 Flash when an API key is configured (`AiAssistantService`).
* **Voice input:** Audio recording (`AudioRecorder`) + Sarvam `saaras:v3` speech-to-text (`SarvamService`).
* **SMS sync:** `TransactionSmsReceiver` + `SmsInboxScanner` + regex `SmsDetectionModule` extract amount / merchant / income-vs-expense. Deleting an SMS purges the mirrored entry on next sync.
* **Receipt OCR:** `OcrScanner` — mock receipts for demo + real Gemini Vision path via OkHttp (`scanReceiptReal`). Duplicate check (amount + merchant + time window) before insert.
* **Wallets, goals & streaks:** Multi-wallet transfers (`WalletRepository`), daily goal streaks, SIP / investment tracking with low-balance alerts.
* **Themes & localization:** Material 3 Dynamic vs Professional themes, light/dark, English + Hindi only.
* **Auth backend (optional):** Firebase Cloud Functions + Twilio OTP (`sendOTP` / `verifyOTP`, 6-digit, 5-min expiry, Firestore `otps` + `users` collections).

## Tech Stack

* **UI:** Jetpack Compose (BOM 2026.03.01), Material 3, Navigation3, Activity Compose
* **Language / build:** Kotlin 2.3.20, AGP 9.0.1, KSP 2.3.0, kotlinx-serialization-json 1.6.3
* **Architecture:** MVVM, Repository pattern (`TransactionRepository`, `BudgetRepository`, `GoalRepository`, `InvestmentRepository`, `WalletRepository`), Kotlin Flow/Coroutines
* **Database:** Room 2.8.4 + `sqlcipher-android` 4.6.1, `security-crypto`, `biometric`
* **AI / network:** `generativeai:0.7.0` (Gemini), OkHttp 4.12.0, Sarvam STT REST
* **Backend:** Firebase (`finklar-3c10d`, see `app/google-services.json`), Cloud Functions Node 18, `firebase-admin@11`, `firebase-functions@4`, `twilio@4`
* **Tests:** JUnit4, coroutines-test, Espresso, Compose UI Test (`WalletTest`, `SmsDetectionModuleTest`, `MainScreenViewModelTest`)

Project layout:

```
app/src/main/java/com/example/antigravityfinance/
  data/model/ data/local/db/ data/repository/
  service/ai/ service/ocr/ service/sms/ service/audio/ service/security/
  ui/screens/ ui/components/ ui/viewmodel/ theme/
  MainActivity.kt Navigation.kt
firebase_backend/functions/  # index.js, package.json
gradle/libs.versions.toml
```

## Setup

### 1. Prerequisites

* Android Studio Ladybug or newer
* Android SDK API 34+
* JDK 17+

### 2. Android app

```bash
git clone https://github.com/your-username/AntigravityFinance.git
cd AntigravityFinance
```

Open the root folder in Android Studio → **Sync Project with Gradle Files**.

API keys (never committed — loaded from `local.properties` or env in `app/build.gradle.kts`):

```properties
# local.properties (gitignored)
GEMINI_API_KEY=your_gemini_key
SARVAM_API_KEY=your_sarvam_key   # if used via Settings / Developer Options
```

Alternatively set `GEMINI_API_KEY` as an environment variable. You can also paste the Gemini key in-app under Settings → Developer Options.

Build APK: `Build → Build Bundle(s) / APK(s) → Build APK(s)`.

### 3. OTP backend (optional)

```bash
cd firebase_backend/functions
npm install
# configure Twilio (either method):
firebase functions:config:set twilio.sid="..." twilio.token="..." twilio.phone="..."
# or
export TWILIO_ACCOUNT_SID=... TWILIO_AUTH_TOKEN=... TWILIO_PHONE_NUMBER=...
npm run serve   # emulator
npm run deploy  # deploy --only functions
```

Without Twilio credentials `sendOTP` runs in mock mode and returns `mockOtp` for testing.

## Testing

```bash
./gradlew compileDebugSources
./gradlew testDebugUnitTest
```

## Security & Privacy

1. Offline-first: financial records stay on-device; analytics computed locally.
2. DB passphrase generated with secure RNG, stored in encrypted prefs / Keystore.
3. Firebase is used only for OTP verification metadata (`otps`, `users`), not for financial data.
4. Do not commit `local.properties`, `google-services.json` secrets, Twilio creds, or Gemini/Sarvam keys.
