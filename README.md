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
* **Phone sign-in:** Firebase Phone Authentication (`PhoneAuthViewModel`) — SMS code, auto-verification, 60s timeout, and resend.

## Tech Stack

* **UI:** Jetpack Compose (BOM 2026.03.01), Material 3, Navigation3, Activity Compose
* **Language / build:** Kotlin 2.3.20, AGP 9.0.1, KSP 2.3.0, kotlinx-serialization-json 1.6.3
* **Architecture:** MVVM, Repository pattern (`TransactionRepository`, `BudgetRepository`, `GoalRepository`, `InvestmentRepository`, `WalletRepository`), Kotlin Flow/Coroutines
* **Database:** Room 2.8.4 + `sqlcipher-android` 4.6.1, `security-crypto`, `biometric`
* **AI / network:** `generativeai:0.7.0` (Gemini), OkHttp 4.12.0, Sarvam STT REST
* **Auth:** Firebase Phone Auth via `firebase-auth` (version from Firebase BoM `35.0.0`), project `finklar-b84a9` (see `app/google-services.json`)
* **Backend:** Firebase project `finklar-b84a9`. The custom Twilio OTP Cloud Functions are **no longer used**; sign-in is Firebase Phone Auth. The legacy functions remain in `firebase_backend/functions/` for reference only.
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

### 3. Phone sign-in (Firebase Phone Auth)

Sign-in uses **Firebase Phone Authentication** — no custom backend and no Twilio.

1. `app/google-services.json` must point at your Firebase project. This file is currently tracked in git; see the security note below.
2. In the Firebase console, enable **Authentication → Sign-in method → Phone**.
3. Register the app's SHA-1 **and** SHA-256 signing fingerprints (both debug and release) under **Project settings → Your apps**.
4. Build and run; the onboarding screen asks for an E.164 phone number (default `+91`) and a 6-digit code.

Phone numbers must be entered in E.164 format. For testing without spending SMS credit, add a test number under **Authentication → Phone → Test numbers** in the Firebase console.

The user identifier is the Firebase Auth **uid** (`FirebaseAuth.getInstance().currentUser?.uid`), which replaces the phone number anywhere data was previously keyed by it. `FinanceViewModel.userId` exposes it, and it is stored on sign-in by `SecurityHelper`.

#### Optional: any custom Cloud Functions

If you later call your own backend, update `FUNCTIONS_BASE_URL` in `FinanceViewModel.kt`. It still points at the **old** project `finklar-3c10d` and needs your new project's values.

> The legacy Twilio OTP functions in `firebase_backend/functions/` are no longer called by the app. They can be deleted once you are sure you do not need them.

## Testing

```bash
./gradlew compileDebugSources
./gradlew testDebugUnitTest
```

## Security & Privacy

1. Offline-first: financial records stay on-device; analytics computed locally.
2. DB passphrase generated with secure RNG, stored in encrypted prefs / Keystore.
3. Firebase is used only for Phone Authentication (the account and its uid); financial data never leaves the device.
4. Do not commit `local.properties`, `google-services.json`, or Gemini/Sarvam keys. **Note: `app/google-services.json` is currently tracked in git and contains a live Firebase API key — see the security note in the handover summary.**
