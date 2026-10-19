package com.example.antigravityfinance.ui.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.antigravityfinance.data.local.db.FinanceDatabase
import com.example.antigravityfinance.data.local.db.SplitEntity
import com.example.antigravityfinance.data.model.*
import com.example.antigravityfinance.data.repository.*
import com.example.antigravityfinance.service.ai.AiAssistantService
import com.example.antigravityfinance.service.calendar.CalendarSync
import kotlinx.coroutines.Dispatchers
import com.example.antigravityfinance.service.ocr.OcrScanner
import com.example.antigravityfinance.service.security.SecurityHelper
import com.example.antigravityfinance.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import com.google.ai.client.generativeai.GenerativeModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.example.antigravityfinance.service.sms.detection.*
import com.example.antigravityfinance.ui.screens.translate

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FinanceDatabase.getDatabase(application)
    private val securityHelper = SecurityHelper(application)
    
    val transactionRepository = TransactionRepository(db.transactionDao(), db.recurringMerchantDao(), db.budgetDao(), db.walletDao())
    val budgetRepository = BudgetRepository(db.budgetDao())
    val goalRepository = GoalRepository(db.savingsGoalDao())
    val investmentRepository = InvestmentRepository(db.investmentDao())
    val walletRepository = WalletRepository(db.walletDao(), db.walletTransferDao(), db.transactionDao(), db)
    val bankAccountRepository = BankAccountRepository(db.bankAccountDao())

    /** Bank accounts discovered from parsed notifications/SMS. */
    val bankAccounts: StateFlow<List<BankAccount>> = bankAccountRepository.allBankAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // --- SECURITY FLOW STATE ---
    private val _isPinSet = MutableStateFlow(securityHelper.isPinSet())
    val isPinSet: StateFlow<Boolean> = _isPinSet.asStateFlow()

    private val _isAuthRequired = MutableStateFlow(securityHelper.isPinSet())
    val isAuthRequired: StateFlow<Boolean> = _isAuthRequired.asStateFlow()

    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    // Remaining lockout in milliseconds; > 0 means further PIN guesses are refused.
    private val _pinLockedForMs = MutableStateFlow(securityHelper.pinLockoutRemainingMs())
    val pinLockedForMs: StateFlow<Long> = _pinLockedForMs.asStateFlow()

    val biometricsEnabled: Boolean get() = securityHelper.isBiometricsEnabled()

    // --- USER REGISTRATION STATE ---
    private val _isRegistered = MutableStateFlow(securityHelper.isRegistered())
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private val _userName = MutableStateFlow(securityHelper.getUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow(securityHelper.getUserEmail())
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userPhone = MutableStateFlow(securityHelper.getUserPhone())
    val userPhone: StateFlow<String> = _userPhone.asStateFlow()

    // Firebase uid: the identifier that replaces the phone number.
    private val _userUid = MutableStateFlow(securityHelper.getUserUid())
    val userUid: StateFlow<String> = _userUid.asStateFlow()

    private val _userProfilePhotoPath = MutableStateFlow(securityHelper.getUserProfilePhotoPath())
    val userProfilePhotoPath: StateFlow<String> = _userProfilePhotoPath.asStateFlow()

    private val _googleSheetUrl = MutableStateFlow<String?>(securityHelper.getGoogleSheetUrl())
    val googleSheetUrl: StateFlow<String?> = _googleSheetUrl.asStateFlow()

    private val _isGoogleSheetsSyncing = MutableStateFlow(false)
    val isGoogleSheetsSyncing: StateFlow<Boolean> = _isGoogleSheetsSyncing.asStateFlow()

    /**
     * True when this install has completed sign-in. Registration is recorded locally by
     * completeRegistration(); there is no remote session to restore.
     *
     * This MUST depend only on isRegistered(). An earlier version also required
     * isTotpEnabled(), which the current passcode flow never sets, so onboarding could
     * never dismiss and the app appeared not to open.
     */
    val isVerifiedOnDevice: Boolean
        get() = securityHelper.isRegistered()

    /**
     * Stable user id for data that used to be keyed by phone number. Generated locally
     * once TOTP verification succeeds. Never the phone number.
     */
    val userId: String
        get() = securityHelper.getUserUid()

    /**
     * Completes registration after the first successful TOTP code. Stores the profile
     * alongside the locally generated uid, which is the identifier for this user.
     */
    fun completeRegistration(uid: String, name: String, email: String, phone: String) {
        securityHelper.saveUserRegistration(name, email, phone, uid)
        _isRegistered.value = true
        _userName.value = name
        _userEmail.value = email
        _userPhone.value = phone
        _userUid.value = uid
    }

    fun clearUserRegistrationForLogout() {
        securityHelper.clearUserRegistration()
        _isRegistered.value = false
        _userName.value = ""
        _userEmail.value = ""
        _userPhone.value = ""
    }

    // --- THEME & SETTINGS STATE ---
    private val _themeType = MutableStateFlow(securityHelper.getTheme())
    val themeType: StateFlow<ThemeType> = _themeType.asStateFlow()

    private val _isDarkMode = MutableStateFlow(securityHelper.getDarkMode())
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    private val _accentIndex = MutableStateFlow(securityHelper.getAccentIndex())
    val accentIndex: StateFlow<Int> = _accentIndex.asStateFlow()

    val customAccent: StateFlow<Color?> = _accentIndex.map { index ->
        getAccentColorFromIndex(index)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currency = MutableStateFlow(securityHelper.getCurrency())
    val currency: StateFlow<CurrencyType> = _currency.asStateFlow()

    private val _language = MutableStateFlow(securityHelper.getLanguage())
    val language: StateFlow<LanguageType> = _language.asStateFlow()

    private val _sarvamKey = MutableStateFlow(securityHelper.getSarvamKey())
    val sarvamKey: StateFlow<String> = _sarvamKey.asStateFlow()

    private val _geminiApiKey = MutableStateFlow(securityHelper.getGeminiApiKey())
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _isInvestmentsEnabled = MutableStateFlow(securityHelper.isInvestmentsEnabled())
    val isInvestmentsEnabled: StateFlow<Boolean> = _isInvestmentsEnabled.asStateFlow()

    private val _isInitialIncomeSet = MutableStateFlow(securityHelper.isInitialIncomeSet())
    val isInitialIncomeSet: StateFlow<Boolean> = _isInitialIncomeSet.asStateFlow()

    private val _rentAmount = MutableStateFlow(securityHelper.getRentAmount())
    val rentAmount: StateFlow<Double> = _rentAmount.asStateFlow()

    private val _emiAmount = MutableStateFlow(securityHelper.getEmiAmount())
    val emiAmount: StateFlow<Double> = _emiAmount.asStateFlow()

    private val _emiDay = MutableStateFlow(securityHelper.getEmiDay())
    val emiDay: StateFlow<Int> = _emiDay.asStateFlow()

    private val _sipAmount = MutableStateFlow(securityHelper.getSipAmount())
    val sipAmount: StateFlow<Double> = _sipAmount.asStateFlow()

    private val _sipDay = MutableStateFlow(securityHelper.getSipDay())
    val sipDay: StateFlow<Int> = _sipDay.asStateFlow()

    private val _otherMandatory = MutableStateFlow(securityHelper.getOtherMandatory())
    val otherMandatory: StateFlow<Double> = _otherMandatory.asStateFlow()

    private val _userIncomeOverride = MutableStateFlow(securityHelper.getUserIncomeOverride())
    val userIncomeOverride: StateFlow<Double> = _userIncomeOverride.asStateFlow()

    private val _lastWalletClearTimestamp = MutableStateFlow(securityHelper.getLastWalletClearTimestamp())
    val lastWalletClearTimestamp: StateFlow<Long> = _lastWalletClearTimestamp.asStateFlow()

    private val _setZeroTimestamp = MutableStateFlow(securityHelper.getSetZeroTimestamp())
    val setZeroTimestamp: StateFlow<Long> = _setZeroTimestamp.asStateFlow()

    private val _customCommitments = MutableStateFlow<List<CustomCommitment>>(loadCustomCommitmentsFromPref())
    val customCommitments: StateFlow<List<CustomCommitment>> = _customCommitments.asStateFlow()

    // --- ALERT / CALENDAR SYNC STATE ---
    private val _isCalendarSyncEnabled = MutableStateFlow(securityHelper.isCalendarSyncEnabled())
    val isCalendarSyncEnabled: StateFlow<Boolean> = _isCalendarSyncEnabled.asStateFlow()

    private fun loadCustomCommitmentsFromPref(): List<CustomCommitment> {
        val raw = securityHelper.getCustomCommitmentsJson() ?: return emptyList()
        val list = mutableListOf<CustomCommitment>()
        try {
            val jsonArray = org.json.JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    CustomCommitment(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        amount = obj.optDouble("amount", 0.0),
                        day = obj.optInt("day", 1),
                        notes = obj.optString("notes", ""),
                        reminderMinutesBefore = if (obj.has("reminderMinutesBefore") && !obj.isNull("reminderMinutesBefore")) {
                            obj.optInt("reminderMinutesBefore")
                        } else {
                            null
                        },
                        calendarEventId = if (obj.has("calendarEventId") && !obj.isNull("calendarEventId")) {
                            obj.optLong("calendarEventId")
                        } else {
                            null
                        }
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("FinanceViewModel", "Operation failed: " + e.javaClass.simpleName)
        }
        return list
    }

    fun addOrUpdateCommitment(commitment: CustomCommitment) {
        val current = _customCommitments.value.toMutableList()
        val index = current.indexOfFirst { it.id == commitment.id }
        val previous = if (index >= 0) current[index] else null
        if (index >= 0) {
            current[index] = commitment
        } else {
            current.add(commitment)
        }
        _customCommitments.value = current
        saveCustomCommitmentsToPref(current)
        syncCommitmentToCalendar(commitment, previous)
    }

    fun deleteCommitment(commitmentId: String) {
        val existing = _customCommitments.value.firstOrNull { it.id == commitmentId }
        val current = _customCommitments.value.filter { it.id != commitmentId }
        _customCommitments.value = current
        saveCustomCommitmentsToPref(current)
        // Deleting an alert removes its calendar event too.
        existing?.let { CalendarSync.deleteEvent(getApplication(), it.calendarEventId) }
    }

    /** Sets or clears the reminder time for an alert, mirroring it to the calendar. */
    fun setCommitmentReminder(commitmentId: String, minutesBefore: Int?) {
        val existing = _customCommitments.value.firstOrNull { it.id == commitmentId } ?: return
        addOrUpdateCommitment(existing.copy(reminderMinutesBefore = minutesBefore))
    }

    private fun syncCommitmentToCalendar(commitment: CustomCommitment, previous: CustomCommitment?) {
        if (!securityHelper.isCalendarSyncEnabled()) {
            // Feature off: remove any event created earlier so nothing is left behind.
            val eventId = commitment.calendarEventId ?: return
            CalendarSync.deleteEvent(getApplication(), eventId)
            setCalendarEventId(commitment.id, null)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val eventId = CalendarSync.upsertEvent(
                context = getApplication(),
                eventId = commitment.calendarEventId,
                title = commitment.notes.ifBlank { "FinKlar alert" },
                startTimeMillis = nextOccurrenceOf(commitment.day),
                reminderMinutesBefore = commitment.reminderMinutesBefore
            ) ?: return@launch

            setCalendarEventId(commitment.id, eventId)
            if (previous?.calendarEventId != null && previous.calendarEventId != eventId) {
                CalendarSync.deleteEvent(getApplication(), previous.calendarEventId)
            }
        }
    }

    private fun setCalendarEventId(commitmentId: String, eventId: Long?) {
        val updated = _customCommitments.value.map {
            if (it.id == commitmentId) it.copy(calendarEventId = eventId) else it
        }
        _customCommitments.value = updated
        saveCustomCommitmentsToPref(updated)
    }

    /** Turns calendar sync on or off, creating or removing events to match. */
    fun setCalendarSyncEnabled(enabled: Boolean) {
        securityHelper.saveCalendarSyncEnabled(enabled)
        _isCalendarSyncEnabled.value = enabled
        if (enabled) {
            // Push every alert that has no event yet.
            _customCommitments.value.filter { it.calendarEventId == null }
                .forEach { syncCommitmentToCalendar(it, null) }
        } else {
            _customCommitments.value.forEach {
                CalendarSync.deleteEvent(getApplication(), it.calendarEventId)
            }
            val cleared = _customCommitments.value.map { it.copy(calendarEventId = null) }
            _customCommitments.value = cleared
            saveCustomCommitmentsToPref(cleared)
        }
    }

    /** Next calendar date whose day-of-month is [day], used as the alert start time. */
    private fun nextOccurrenceOf(day: Int): Long {
        val calendar = java.util.Calendar.getInstance()
        val daysInMonth = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val today = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val target = day.coerceIn(1, daysInMonth)
        if (target <= today) calendar.add(java.util.Calendar.MONTH, 1)
        calendar.set(java.util.Calendar.DAY_OF_MONTH, target)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 9)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun saveCustomCommitmentsToPref(list: List<CustomCommitment>) {
        val jsonArray = org.json.JSONArray()
        list.forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("amount", it.amount)
            obj.put("day", it.day)
            obj.put("notes", it.notes)
            it.reminderMinutesBefore?.let { m -> obj.put("reminderMinutesBefore", m) }
            it.calendarEventId?.let { e -> obj.put("calendarEventId", e) }
            jsonArray.put(obj)
        }
        securityHelper.saveCustomCommitmentsJson(jsonArray.toString())
    }

    fun saveSetZeroTimestamp(timestamp: Long) {
        securityHelper.saveSetZeroTimestamp(timestamp)
        _setZeroTimestamp.value = timestamp
    }

    // --- REPOSITORIES DATA STREAMS ---
    val allTransactions: StateFlow<List<Transaction>> = transactionRepository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingTransactions: StateFlow<List<Transaction>> = transactionRepository.pendingTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgets: StateFlow<List<Budget>> = budgetRepository.allBudgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goals: StateFlow<List<SavingsGoal>> = goalRepository.allGoals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val investments: StateFlow<List<Investment>> = investmentRepository.allInvestments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSplits: StateFlow<List<SplitShare>> = db.splitDao().getAllSplits().map { list ->
        list.map { it.toDomain() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWallets: StateFlow<List<Wallet>> = walletRepository.allWallets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransfers: StateFlow<List<WalletTransfer>> = walletRepository.allTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // --- OCR SCANNING WORKFLOW STATE ---
    private val _isOcrScanning = MutableStateFlow(false)
    val isOcrScanning: StateFlow<Boolean> = _isOcrScanning.asStateFlow()

    private val _scannedTransaction = MutableStateFlow<Transaction?>(null)
    val scannedTransaction: StateFlow<Transaction?> = _scannedTransaction.asStateFlow()

    private val _showDuplicateWarning = MutableStateFlow(false)
    val showDuplicateWarning: StateFlow<Boolean> = _showDuplicateWarning.asStateFlow()

    private val _duplicateMatchingTransaction = MutableStateFlow<Transaction?>(null)
    val duplicateMatchingTransaction: StateFlow<Transaction?> = _duplicateMatchingTransaction.asStateFlow()

    // --- AI CHATBOT STATE ---
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage("welcome", "Hello! I am FinKlar 2.0, your AI finance assistant. Ask me anything about your budgets, transactions, or investments.", false)
        )
    )
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    // --- SMS SYNC STATE ---
    private val _smsSyncedBalance = MutableStateFlow<Double?>(securityHelper.getSyncedBalance())
    val smsSyncedBalance: StateFlow<Double?> = _smsSyncedBalance.asStateFlow()
    
    private val _isSmsScanning = MutableStateFlow(false)
    val isSmsScanning: StateFlow<Boolean> = _isSmsScanning.asStateFlow()



    private val _trustedSenders = MutableStateFlow<Set<String>>(securityHelper.getTrustedSenders())
    val trustedSenders: StateFlow<Set<String>> = _trustedSenders.asStateFlow()

    // --- INITIALIZATION ---
    init {
        // Registration is stored locally, so a returning user is not sent back to onboarding.
        restoreLocalSession()

        // Pre-load Gemini API key from the BuildConfig constant (sourced from local.properties)
        // if the user has not already saved one. Empty when no key is configured at build time.
        if (securityHelper.getGeminiApiKey().isBlank()) {
            val builtInGeminiKey = com.example.antigravityfinance.BuildConfig.GEMINI_API_KEY
            if (builtInGeminiKey.isNotBlank()) {
                securityHelper.saveGeminiApiKey(builtInGeminiKey)
                _geminiApiKey.value = builtInGeminiKey
            }
        }

        viewModelScope.launch {
            budgetRepository.allBudgets.first().let { current ->
                if (current.isEmpty()) {
                    budgetRepository.insertBudget(Budget(category = "All", limitAmount = 25000.0))
                    budgetRepository.insertBudget(Budget(category = "FOOD", limitAmount = 8000.0))
                    budgetRepository.insertBudget(Budget(category = "SHOPPING", limitAmount = 5000.0))
                }
            }

            walletRepository.allWallets.first().let { current ->
                if (current.isEmpty()) {
                    val now = System.currentTimeMillis()
                    walletRepository.insertWallet(
                        Wallet(
                            name = "Current Wallet",
                            balance = 0.0,
                            purpose = "Current Wallet",
                            isDefault = true,
                            createdAt = now,
                            investedAmount = 0.0,
                            initialAmount = 0.0
                        )
                    )
                }
            }

            // Auto-confirm all pending transactions in the database
            transactionRepository.allTransactions.first().forEach { tx ->
                if (tx.status == TransactionStatus.PENDING) {
                    transactionRepository.confirmTransaction(tx.id)
                }
            }
            reconcileSmsInbox()
        }
    }

    // --- SECURITY ACTIONS ---
    fun setPin(pin: String) {
        securityHelper.savePin(pin)
        _isPinSet.value = true
        _isAuthRequired.value = false
    }

    fun submitPin(pin: String): Boolean {
        // Refuse guesses while locked out rather than spending an attempt.
        if (securityHelper.isPinLockedOut()) {
            _pinError.value = true
            _pinLockedForMs.value = securityHelper.pinLockoutRemainingMs()
            return false
        }
        val ok = securityHelper.verifyPin(pin)
        _pinError.value = !ok
        if (ok) {
            _isAuthRequired.value = false
            _pinLockedForMs.value = 0L
        } else {
            _pinLockedForMs.value = securityHelper.pinLockoutRemainingMs()
        }
        return ok
    }

    /**
     * Unlocks after a successful platform biometric prompt. Callers MUST only invoke
     * this from a real BiometricPrompt success callback — it performs no verification
     * of its own and must never be wired to a fixed PIN.
     */
    fun unlockViaBiometrics() {
        if (!securityHelper.isPinSet()) return
        _isAuthRequired.value = false
        _pinError.value = false
        _pinLockedForMs.value = 0L
    }

    /** Attempts remaining before the PIN gate locks out. */
    fun pinAttemptsRemaining(): Int = securityHelper.pinAttemptsRemaining()

    fun logout() {
        if (securityHelper.isPinSet()) {
            _isAuthRequired.value = true
        }
    }

    fun removePin() {
        securityHelper.clearPin()
        securityHelper.setBiometricsEnabled(false)
        _isPinSet.value = false
        _isAuthRequired.value = false
    }

    // --- SETTINGS ACTIONS ---
    fun updateTheme(theme: ThemeType) {
        securityHelper.saveTheme(theme)
        _themeType.value = theme
    }

    fun updateDarkMode(isDark: Boolean?) {
        securityHelper.saveDarkMode(isDark)
        _isDarkMode.value = isDark
    }

    fun updateAccentIndex(index: Int) {
        securityHelper.saveAccentIndex(index)
        _accentIndex.value = index
    }

    fun updateCurrency(currency: CurrencyType) {
        securityHelper.saveCurrency(currency)
        _currency.value = currency
    }

    fun updateLanguage(language: LanguageType) {
        securityHelper.saveLanguage(language)
        _language.value = language
    }

    fun updateInvestmentsEnabled(enabled: Boolean) {
        securityHelper.saveInvestmentsEnabled(enabled)
        _isInvestmentsEnabled.value = enabled
    }

    fun updateSarvamKey(key: String) {
        securityHelper.saveSarvamKey(key)
        _sarvamKey.value = securityHelper.getSarvamKey()
    }

    fun updateGeminiApiKey(key: String) {
        securityHelper.saveGeminiApiKey(key)
        _geminiApiKey.value = securityHelper.getGeminiApiKey()
    }

    fun updateRentAmount(amount: Double) {
        securityHelper.saveRentAmount(amount)
        _rentAmount.value = amount
    }

    fun updateEmiAmount(amount: Double) {
        securityHelper.saveEmiAmount(amount)
        _emiAmount.value = amount
    }

    fun updateEmiDay(day: Int) {
        securityHelper.saveEmiDay(day)
        _emiDay.value = day
    }

    fun updateSipAmount(amount: Double) {
        securityHelper.saveSipAmount(amount)
        _sipAmount.value = amount
    }

    fun updateSipDay(day: Int) {
        securityHelper.saveSipDay(day)
        _sipDay.value = day
    }

    fun updateOtherMandatory(amount: Double) {
        securityHelper.saveOtherMandatory(amount)
        _otherMandatory.value = amount
    }

    fun updateUserIncomeOverride(income: Double) {
        securityHelper.saveUserIncomeOverride(income)
        _userIncomeOverride.value = income
    }

    fun clearTransactionsFromWallet() {
        viewModelScope.launch {
            transactionRepository.deleteAllTransactions()
            securityHelper.saveLastWalletClearTimestamp(0L)
            _lastWalletClearTimestamp.value = 0L
            securityHelper.saveSetZeroTimestamp(0L)
            _setZeroTimestamp.value = 0L
            securityHelper.clearSyncedBalance()
            _smsSyncedBalance.value = null
        }
    }

    // --- TRANSACTION CONFIRMATION WORKFLOW ACTIONS ---
    fun handleMockOcrTrigger(type: OcrScanner.MockReceiptType) {
        viewModelScope.launch {
            _isOcrScanning.value = true
            val transaction = OcrScanner.scanReceiptMock(type)
            _isOcrScanning.value = false
            
            // Auto-confirm and insert directly
            val finalTx = transaction.copy(status = TransactionStatus.CONFIRMED)
            transactionRepository.insertTransaction(finalTx)
        }
    }

    fun handleRealOcrTrigger(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            val apiKey = securityHelper.getGeminiApiKey()
            if (apiKey.isBlank()) {
                android.widget.Toast.makeText(getApplication(), "Gemini API Key is missing! Go to settings to set it.", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            
            _isOcrScanning.value = true
            android.widget.Toast.makeText(getApplication(), "Scanning receipt using Gemini AI...", android.widget.Toast.LENGTH_SHORT).show()
            try {
                val transactions = OcrScanner.scanReceiptReal(bitmap, apiKey)
                _isOcrScanning.value = false
                if (transactions.isEmpty()) {
                    android.widget.Toast.makeText(getApplication(), "No transactions detected in the image.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    for (transaction in transactions) {
                        // Auto-confirm and insert directly
                        val finalTx = transaction.copy(status = TransactionStatus.CONFIRMED)
                        transactionRepository.insertTransaction(finalTx)
                    }
                    android.widget.Toast.makeText(getApplication(), "Found and added ${transactions.size} transactions!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                _isOcrScanning.value = false
                android.widget.Toast.makeText(getApplication(), "Gemini OCR failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private val _isPdfParsing = MutableStateFlow(false)
    val isPdfParsing: StateFlow<Boolean> = _isPdfParsing.asStateFlow()

    private val _pdfParsingProgress = MutableStateFlow("")
    val pdfParsingProgress: StateFlow<String> = _pdfParsingProgress.asStateFlow()

    fun importTransactionsFromPdf(pdfUri: android.net.Uri) {
        viewModelScope.launch {
            val apiKey = securityHelper.getGeminiApiKey()
            if (apiKey.isBlank()) {
                android.widget.Toast.makeText(getApplication(), "Gemini API Key is missing! Go to settings to set it.", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            
            _isPdfParsing.value = true
            _pdfParsingProgress.value = "Reading PDF document...".translate(language.value)
            
            try {
                val bitmaps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    renderPdfToBitmaps(getApplication(), pdfUri)
                }
                
                if (bitmaps.isEmpty()) {
                    _isPdfParsing.value = false
                    android.widget.Toast.makeText(getApplication(), "Failed to read PDF or document is empty.", android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                var totalImported = 0
                val defaultWallet = walletRepository.getDefaultWallet()
                val walletId = defaultWallet?.id
                
                for (index in bitmaps.indices) {
                    _pdfParsingProgress.value = "Extracting transactions: page ${index + 1} of ${bitmaps.size}...".translate(language.value)
                    val bitmap = bitmaps[index]
                    val parsedList = OcrScanner.scanReceiptReal(bitmap, apiKey)
                    
                    if (parsedList.isNotEmpty()) {
                        for (tx in parsedList) {
                            val finalTx = tx.copy(
                                status = TransactionStatus.CONFIRMED,
                                walletId = walletId
                            )
                            transactionRepository.insertTransaction(finalTx)
                            totalImported++
                        }
                    }
                }
                
                // If a default/current wallet exists, recompute its balance
                if (walletId != null) {
                    val updatedBal = walletRepository.recomputeWalletBalance(walletId)
                    val wallet = walletRepository.getWalletById(walletId)
                    if (wallet != null) {
                        walletRepository.updateWallet(wallet.copy(balance = updatedBal))
                    }
                }
                
                _isPdfParsing.value = false
                android.widget.Toast.makeText(getApplication(), "Successfully imported $totalImported transactions!", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                _isPdfParsing.value = false
                android.widget.Toast.makeText(getApplication(), "Failed to process statement PDF: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderPdfToBitmaps(context: android.content.Context, pdfUri: android.net.Uri): List<android.graphics.Bitmap> {
        val bitmaps = mutableListOf<android.graphics.Bitmap>()
        try {
            val fileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
            if (fileDescriptor != null) {
                val renderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
                val pageCount = renderer.pageCount.coerceAtMost(3) // Limit to first 3 pages
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val width = 1080
                    val height = (width * page.height / page.width)
                    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                    page.close()
                }
                renderer.close()
                fileDescriptor.close()
            }
        } catch (e: java.lang.Exception) {
            android.util.Log.w("FinanceViewModel", "Operation failed: " + e.javaClass.simpleName)
        }
        return bitmaps
    }

    fun confirmScannedTransaction() {
        val tx = _scannedTransaction.value ?: return
        viewModelScope.launch {
            transactionRepository.insertTransaction(tx.copy(status = TransactionStatus.CONFIRMED))
            _scannedTransaction.value = null
            _showDuplicateWarning.value = false
            _duplicateMatchingTransaction.value = null
        }
    }

    fun cancelScannedTransaction() {
        val tx = _scannedTransaction.value ?: return
        viewModelScope.launch {
            transactionRepository.insertTransaction(tx.copy(status = TransactionStatus.CANCELLED))
            _scannedTransaction.value = null
            _showDuplicateWarning.value = false
            _duplicateMatchingTransaction.value = null
        }
    }

    fun ignoreScannedTransaction() {
        val tx = _scannedTransaction.value ?: return
        viewModelScope.launch {
            transactionRepository.insertTransaction(tx.copy(status = TransactionStatus.PENDING))
            _scannedTransaction.value = null
            _showDuplicateWarning.value = false
            _duplicateMatchingTransaction.value = null
        }
    }

    fun forceConfirmDuplicate() {
        confirmScannedTransaction()
    }

    fun dismissDuplicateWarning() {
        _scannedTransaction.value = null
        _showDuplicateWarning.value = false
        _duplicateMatchingTransaction.value = null
    }

    fun confirmPendingTransaction(id: Int) {
        viewModelScope.launch {
            transactionRepository.confirmTransaction(id)
        }
    }

    fun cancelPendingTransaction(id: Int) {
        viewModelScope.launch {
            transactionRepository.cancelTransaction(id)
        }
    }

    fun updateTransactionCategory(id: Int, newCategory: String) {
        viewModelScope.launch {
            transactionRepository.updateTransactionCategory(id, newCategory)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transaction)
        }
    }

    fun undoDeleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.insertTransaction(transaction)
        }
    }

    fun shiftTransactionWallet(transactionId: Int, targetWalletId: Int?) {
        viewModelScope.launch {
            transactionRepository.shiftTransactionWallet(transactionId, targetWalletId)
        }
    }

    fun addManualTransaction(amount: Double, merchant: String, isIncome: Boolean, category: String, walletId: Int? = null) {
        viewModelScope.launch {
            val newTx = Transaction(
                amount = amount,
                merchant = merchant,
                date = System.currentTimeMillis(),
                category = category,
                isIncome = isIncome,
                status = TransactionStatus.CONFIRMED,
                walletId = walletId
            )
            transactionRepository.insertTransaction(newTx)
        }
    }

    fun processVoiceTransaction(text: String, callback: (Transaction?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val apiKey = securityHelper.getGeminiApiKey()
            if (apiKey.isNotBlank()) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val prompt = """
                        Analyze this spoken financial transaction: "$text".
                        Extract:
                        1. Amount (floating point number)
                        2. Merchant (string)
                        3. Suggested Category (one of: FOOD, SHOPPING, LIVELIHOOD, COMPULSORY, TRAVEL, INVESTMENT, OTHERS)
                        4. isIncome (boolean, true if receiving money/salary/credit, false if spending/paying/debit)
                        5. Account/Payment Method (string, e.g., Bank Account, Credit Card, GPay, Wallet, etc. If no payment method or account is mentioned, set this to "Cash")
                        
                        Respond ONLY with a valid JSON object matching this schema:
                        {
                          "amount": 0.0,
                          "merchant": "Name",
                          "category": "FOOD",
                          "isIncome": false,
                          "account": "Cash"
                        }
                    """.trimIndent()

                    val jsonRequest = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", prompt)
                                    })
                                })
                            })
                        })
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = jsonRequest.toString().toRequestBody(mediaType)
                    
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                        .addHeader("x-goog-api-key", apiKey)
                        .post(requestBody)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string() ?: ""
                            val jsonResponse = JSONObject(responseBody)
                            val candidates = jsonResponse.getJSONArray("candidates")
                            if (candidates.length() > 0) {
                                val contentObj = candidates.getJSONObject(0).getJSONObject("content")
                                val parts = contentObj.getJSONArray("parts")
                                if (parts.length() > 0) {
                                    val jsonText = parts.getJSONObject(0).getString("text").trim()
                                    val cleanedJson = if (jsonText.startsWith("```json")) {
                                        jsonText.substringAfter("```json").substringBefore("```").trim()
                                    } else if (jsonText.startsWith("```")) {
                                        jsonText.substringAfter("```").substringBefore("```").trim()
                                    } else {
                                        jsonText
                                    }
                                    val json = org.json.JSONObject(cleanedJson)
                                    val amount = json.optDouble("amount", 0.0)
                                    val merchant = json.optString("merchant", "Voice Entry")
                                    val category = json.optString("category", "OTHERS")
                                    val isIncome = json.optBoolean("isIncome", false)
                                    val account = json.optString("account", "Cash")
                                    
                                    val tx = Transaction(
                                        amount = amount,
                                        merchant = merchant,
                                        date = System.currentTimeMillis(),
                                        category = category,
                                        notes = "Parsed from voice: $text",
                                        isIncome = isIncome,
                                        account = account,
                                        status = TransactionStatus.CONFIRMED
                                    )
                                    transactionRepository.insertTransaction(tx)
                                    
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        callback(tx)
                                    }
                                    return@launch
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FinanceViewModel", "Operation failed: " + e.javaClass.simpleName)
                }
            }
            
            // Fallback rule-based parsing
            val tx = parseVoiceFallback(text)
            if (tx != null) {
                transactionRepository.insertTransaction(tx)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                callback(tx)
            }
        }
    }

    fun transcribeAndTranslateAudio(audioFile: java.io.File, callback: (String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val key = securityHelper.getSarvamKey()
            if (key.isBlank()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    callback(null)
                }
                return@launch
            }
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", "saaras:v3")
                    .addFormDataPart("mode", "translate")
                    .addFormDataPart(
                        "file", 
                        audioFile.name, 
                        audioFile.asRequestBody("audio/mp4".toMediaType())
                    )
                    .build()

                val request = Request.Builder()
                    .url("https://api.sarvam.ai/speech-to-text")
                    .post(requestBody)
                    .addHeader("api-subscription-key", key)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errMsg = "Sarvam API error: ${response.code} ${response.message}"
                        System.err.println(errMsg)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(getApplication(), errMsg, android.widget.Toast.LENGTH_LONG).show()
                            callback(null)
                        }
                        return@launch
                    }
                    val body = response.body?.string() ?: ""
                    val json = org.json.JSONObject(body)
                    val transcript = json.optString("transcript", "")
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        callback(transcript.ifBlank { null })
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("FinanceViewModel", "Operation failed: " + e.javaClass.simpleName)
                val errMsg = "Voice translation connection failed: ${e.localizedMessage}"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), errMsg, android.widget.Toast.LENGTH_LONG).show()
                    callback(null)
                }
            }
        }
    }

    private fun parseVoiceFallback(text: String): Transaction? {
        val lowercase = text.lowercase()
        val amountRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:rupees|rs|inr)?|rs\\.?\\s*(\\d+(?:\\.\\d+)?)")
        var amount = 0.0
        val match = amountRegex.find(lowercase)
        if (match != null) {
            val val1 = match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val val2 = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            amount = val1 ?: val2 ?: 0.0
        }
        
        var isIncome = lowercase.contains("received") || lowercase.contains("salary") || lowercase.contains("credited")
        var merchant = "Voice Entry"
        if (lowercase.contains("at ")) {
            merchant = text.substringAfter("at ").substringBefore(" on").substringBefore(" for").trim()
        } else if (lowercase.contains("to ")) {
            merchant = text.substringAfter("to ").substringBefore(" on").substringBefore(" for").trim()
        } else if (lowercase.contains("from ")) {
            merchant = text.substringAfter("from ").substringBefore(" on").substringBefore(" for").trim()
        }
        
        var category = "OTHERS"
        if (lowercase.contains("starbucks") || lowercase.contains("coffee") || lowercase.contains("food") || lowercase.contains("domino") || lowercase.contains("pizza") || lowercase.contains("restaurant")) {
            category = "FOOD"
        } else if (lowercase.contains("salary") || lowercase.contains("income")) {
            category = "SALARY"
            isIncome = true
        } else if (lowercase.contains("shopping") || lowercase.contains("clothes") || lowercase.contains("amazon") || lowercase.contains("flipkart")) {
            category = "SHOPPING"
        } else if (lowercase.contains("travel") || lowercase.contains("cab") || lowercase.contains("taxi") || lowercase.contains("uber") || lowercase.contains("ola") || lowercase.contains("petrol") || lowercase.contains("fuel")) {
            category = "TRAVEL"
        }
        
        if (amount > 0.0) {
            return Transaction(
                amount = amount,
                merchant = merchant,
                date = System.currentTimeMillis(),
                category = category,
                notes = "Parsed from voice (fallback): $text",
                isIncome = isIncome,
                status = TransactionStatus.CONFIRMED
            )
        }
        return null
    }

    fun addInitialIncome(amount: Double) {
        viewModelScope.launch {
            val initialIncomeTx = Transaction(
                amount = amount,
                merchant = "Initial Income Setup",
                date = System.currentTimeMillis(),
                category = "SALARY",
                isIncome = true,
                status = TransactionStatus.CONFIRMED,
                notes = "Initial bank balance setup"
            )
            transactionRepository.insertTransaction(initialIncomeTx)
            securityHelper.setInitialIncomeSet(true)
            _isInitialIncomeSet.value = true
        }
    }

    fun addGoal(name: String, target: Double) {
        viewModelScope.launch {
            goalRepository.insertGoal(
                SavingsGoal(
                    name = name,
                    targetAmount = target,
                    deadline = System.currentTimeMillis() + 86400000L * 90
                )
            )
        }
    }

    fun depositToGoal(goal: SavingsGoal, amount: Double) {
        viewModelScope.launch {
            goalRepository.updateGoal(goal.copy(currentAmount = goal.currentAmount + amount))
        }
    }

    fun addInvestment(name: String, symbol: String, type: String, amount: Double) {
        viewModelScope.launch {
            investmentRepository.insertInvestment(
                Investment(
                    name = name,
                    symbol = symbol.uppercase(),
                    type = type,
                    investedAmount = amount,
                    currentValuation = amount * 1.05
                )
            )
        }
    }

    fun splitTransaction(transaction: Transaction, selectedContacts: List<String>) {
        viewModelScope.launch {
            if (selectedContacts.isEmpty()) return@launch
            val totalParts = selectedContacts.size + 1
            val shareAmount = transaction.amount / totalParts
            selectedContacts.forEach { contact ->
                db.splitDao().insert(
                    SplitEntity(
                        transactionId = transaction.id,
                        transactionAmount = transaction.amount,
                        transactionMerchant = transaction.merchant,
                        transactionDate = transaction.date,
                        contactName = contact,
                        shareAmount = shareAmount,
                        isSettled = false
                    )
                )
            }
        }
    }

    fun settleSplit(splitId: Int) {
        viewModelScope.launch {
            val splitDao = db.splitDao()
            val entity = splitDao.getSplitById(splitId)
            if (entity != null) {
                splitDao.update(entity.copy(isSettled = true))
            }
        }
    }

    // --- SMS SIMULATION TRIGGER ---
    fun simulateIncomingSms(body: String) {
        viewModelScope.launch {
            val parsedResult = com.example.antigravityfinance.service.sms.SmsParser.parse(body)
            if (parsedResult != null) {
                val parsed = parsedResult.transaction
                val balance = parsedResult.balance
                
                if (balance != null) {
                    securityHelper.saveSyncedBalance(balance)
                    _smsSyncedBalance.value = balance
                }
                
                val statusToInsert = TransactionStatus.CONFIRMED
                val duplicate = transactionRepository.checkForDuplicate(parsed.amount, parsed.merchant, parsed.date)
                val notesToInsert = if (duplicate != null) "SMS matches Transaction #${duplicate.id} (${duplicate.merchant})" else parsed.notes
                
                val finalTx = parsed.copy(status = statusToInsert, notes = notesToInsert)
                transactionRepository.insertTransaction(finalTx)
            }
        }
    }

    // --- SMS INBOX SCANNING TRIGGER ---
    fun scanSmsInbox(onComplete: (addedCount: Int, balance: Double?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isSmsScanning.value = true
            val result = com.example.antigravityfinance.service.sms.SmsInboxScanner.reconcileInbox(getApplication(), transactionRepository, bankAccountRepository)
            _isSmsScanning.value = false
            
            if (result.latestBalance != null) {
                securityHelper.saveSyncedBalance(result.latestBalance)
                _smsSyncedBalance.value = result.latestBalance
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete(result.addedCount, result.latestBalance)
            }
        }
    }

    fun reconcileSmsInbox() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isSmsScanning.value = true
            val result = com.example.antigravityfinance.service.sms.SmsInboxScanner.reconcileInbox(getApplication(), transactionRepository, bankAccountRepository)
            _isSmsScanning.value = false
            
            if (result.latestBalance != null) {
                securityHelper.saveSyncedBalance(result.latestBalance)
                _smsSyncedBalance.value = result.latestBalance
            }
        }
    }

    // --- AI CHAT CONVERSATION ---
    fun sendMessageToAssistant(text: String) {
        if (text.isBlank()) return
        
        val userMsg = ChatMessage(UUID.randomUUID().toString(), text, true)
        _chatHistory.value = _chatHistory.value + userMsg
        
        viewModelScope.launch {
            _isAiThinking.value = true
            val response = AiAssistantService.askAssistant(
                query = text,
                transactions = allTransactions.value,
                budgets = budgets.value,
                goals = goals.value,
                investments = investments.value,
                apiKey = securityHelper.getGeminiApiKey(),
                setZeroTimestamp = setZeroTimestamp.value,
                currencySymbol = currency.value.symbol,
                languageCode = language.value.code
            )
            _isAiThinking.value = false
            val aiMsg = ChatMessage(UUID.randomUUID().toString(), response, false)
            _chatHistory.value = _chatHistory.value + aiMsg
        }
    }

    fun clearChat() {
        _chatHistory.value = listOf(
            ChatMessage("welcome", "Chat log cleared. Ask me a new financial query!", false)
        )
    }

    // --- AI SAVINGS INSIGHTS ---
    private val _savingsInsights = MutableStateFlow<List<String>>(emptyList())
    val savingsInsights: StateFlow<List<String>> = _savingsInsights.asStateFlow()

    private val _isInsightsLoading = MutableStateFlow(false)
    val isInsightsLoading: StateFlow<Boolean> = _isInsightsLoading.asStateFlow()

    fun generateSavingsInsights() {
        if (_isInsightsLoading.value) return
        viewModelScope.launch {
            _isInsightsLoading.value = true
            val tips = AiAssistantService.generateSavingsInsights(
                transactions = allTransactions.value,
                budgets = budgets.value,
                goals = goals.value,
                investments = investments.value,
                apiKey = securityHelper.getGeminiApiKey(),
                setZeroTimestamp = setZeroTimestamp.value,
                currencySymbol = currency.value.symbol,
                languageCode = language.value.code
            )
            _savingsInsights.value = tips
            _isInsightsLoading.value = false
        }
    }



    fun addTrustedSender(senderId: String) {
        if (senderId.isBlank()) return
        securityHelper.saveTrustedSender(senderId)
        _trustedSenders.value = securityHelper.getTrustedSenders()
    }

    /**
     * Re-reads the stored profile on startup. Everything is local, so this only has to
     * repopulate the state flows; there is no remote session to validate.
     */
    private fun restoreLocalSession() {
        if (!securityHelper.isRegistered()) return
        _userName.value = securityHelper.getUserName()
        _userEmail.value = securityHelper.getUserEmail()
        _userPhone.value = securityHelper.getUserPhone()
        _userUid.value = securityHelper.getUserUid()
        _isRegistered.value = true
    }

    fun logoutUser() {
        viewModelScope.launch {
            db.clearAllTables()
            securityHelper.clearPin()
            securityHelper.setInitialIncomeSet(false)
            securityHelper.saveSyncedBalance(0.0)
            
            // Clear SMS persisted records
            securityHelper.saveReviewNeededQueue(emptyList())
            securityHelper.saveIgnoredSmsList(emptyList())
            
            _isPinSet.value = false
            _isAuthRequired.value = false
            _isInitialIncomeSet.value = false
        }
    }

    // --- HELPERS ---
    private fun getAccentColorFromIndex(index: Int): Color? {
        return when (index) {
            0 -> AccentEmerald
            1 -> AccentOrange
            2 -> AccentIndigo
            3 -> AccentCrimson
            4 -> AccentSky
            else -> null
        }
    }

    // --- BANK ACCOUNT ACTIONS ---

    /** Transactions visible in [walletId], honouring a wallet's bank account restriction. */
    fun transactionsForWallet(walletId: Int): Flow<List<Transaction>> =
        walletRepository.getTransactionsForWallet(walletId)

    /** Balances of a wallet follow the same restriction as its transaction list. */
    fun recomputeWalletBalance(walletId: Int) {
        viewModelScope.launch { walletRepository.updateWalletBalance(walletId) }
    }

    /** [bankAccountId] = null means "all bank accounts" feed this wallet. */
    fun setWalletBankAccount(walletId: Int, bankAccountId: Int?) {
        viewModelScope.launch {
            walletRepository.setLinkedBankAccount(walletId, bankAccountId)
            // A wallet that was previously linked to this account now needs its balance
            // recomputed against the new filter.
            allWallets.value.forEach { wallet ->
                if (wallet.id != walletId && wallet.linkedBankAccountId != null) {
                    walletRepository.updateWalletBalance(wallet.id)
                }
            }
        }
    }

    fun deleteBankAccount(account: BankAccount) {
        viewModelScope.launch {
            allWallets.value.filter { it.linkedBankAccountId == account.id }.forEach {
                walletRepository.setLinkedBankAccount(it.id, null)
            }
            db.transactionDao().clearBankAccount(account.id)
            db.bankAccountDao().delete(account.id)
        }
    }

    // --- WALLET ACTIONS ---
    fun addWallet(name: String, purpose: String?, investedAmount: Double = 0.0, initialAmount: Double = 0.0, isDefault: Boolean = false) {
        viewModelScope.launch {
            val walletId = walletRepository.insertWallet(
                Wallet(
                    name = name,
                    balance = 0.0,
                    purpose = purpose,
                    isDefault = isDefault,
                    createdAt = System.currentTimeMillis(),
                    investedAmount = investedAmount,
                    initialAmount = initialAmount
                )
            )
            if (isDefault) {
                walletRepository.setDefaultWallet(walletId)
            }
        }
    }

    fun updateWallet(wallet: Wallet) {
        viewModelScope.launch {
            walletRepository.updateWallet(wallet)
        }
    }

    fun deleteWallet(wallet: Wallet, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val success = walletRepository.deleteWallet(wallet)
            if (success) {
                onSuccess()
            } else {
                onError("Failed to delete wallet.")
            }
        }
    }

    fun setDefaultWallet(walletId: Int) {
        viewModelScope.launch {
            walletRepository.setDefaultWallet(walletId)
        }
    }

    fun transferMoney(
        fromWalletId: Int,
        toWalletId: Int,
        amount: Double,
        note: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                walletRepository.transferMoney(fromWalletId, toWalletId, amount, note)
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to perform transfer.")
            }
        }
    }

    fun updateUserProfilePhotoPath(path: String) {
        securityHelper.saveUserProfilePhotoPath(path)
        _userProfilePhotoPath.value = path
    }

    fun addDirectReceivable(name: String, amount: Double, date: Long) {
        viewModelScope.launch {
            db.splitDao().insert(
                com.example.antigravityfinance.data.local.db.SplitEntity(
                    transactionId = 0,
                    transactionAmount = amount,
                    transactionMerchant = "Direct Receivable",
                    transactionDate = date,
                    contactName = name,
                    shareAmount = amount,
                    isSettled = false
                )
            )
        }
    }

    fun commitToGoogleSheets(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isGoogleSheetsSyncing.value = true
            try {
                // Collect transactions
                val txList = transactionRepository.allTransactions.first()
                val walletList = walletRepository.allWallets.first()
                
                val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val sdfTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                
                val jsonArray = org.json.JSONArray()
                txList.forEach { tx ->
                    val walletName = walletList.find { it.id == tx.walletId }?.name ?: "Cash"
                    val item = org.json.JSONObject().apply {
                        put("category", tx.category)
                        put("amount", tx.amount)
                        put("date", sdfDate.format(java.util.Date(tx.date)))
                        put("time", sdfTime.format(java.util.Date(tx.date)))
                        put("wallet", walletName)
                        put("type", if (tx.isIncome) "Income" else "Expense")
                    }
                    jsonArray.put(item)
                }

                android.util.Log.d("FinKlarGoogleSheets", "Committing ${jsonArray.length()} rows to Google Sheets")
                
                // Simulate network sync
                kotlinx.coroutines.delay(1500)
                
                val userEmail = securityHelper.getUserEmail().ifBlank { "user@example.com" }
                val mockSheetUrl = "https://docs.google.com/spreadsheets/d/1" + 
                                   java.util.UUID.nameUUIDFromBytes(userEmail.toByteArray()).toString().replace("-", "") + 
                                   "/edit#gid=0"
                
                securityHelper.saveGoogleSheetUrl(mockSheetUrl)
                _googleSheetUrl.value = mockSheetUrl
                onSuccess(mockSheetUrl)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to sync to Google Sheets")
            } finally {
                _isGoogleSheetsSyncing.value = false
            }
        }
    }

    // Backup state for wallet deletion undo
    private var lastDeletedWallet: Wallet? = null
    private var lastDeletedWalletTxIds: List<Int> = emptyList()
    private var lastDeletedWalletTransfers: List<WalletTransfer> = emptyList()

    fun deleteWalletWithUndo(
        wallet: Wallet,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Fetch transactions and transfers associated with this wallet
                val txs = transactionRepository.allTransactions.first()
                    .filter { it.walletId == wallet.id }
                    .map { it.id }
                val transfers = walletRepository.allTransfers.first()
                    .filter { it.fromWalletId == wallet.id || it.toWalletId == wallet.id }

                lastDeletedWallet = wallet
                lastDeletedWalletTxIds = txs
                lastDeletedWalletTransfers = transfers

                val success = walletRepository.deleteWallet(wallet)
                if (success) {
                    onSuccess("Wallet \"${wallet.name}\" deleted")
                } else {
                    onError("Failed to delete wallet.")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "An error occurred during deletion.")
            }
        }
    }

    fun undoDeleteWallet(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val wallet = lastDeletedWallet ?: return
        viewModelScope.launch {
            try {
                // Re-insert wallet
                val newWalletId = walletRepository.insertWallet(wallet)

                // Restore transactions
                lastDeletedWalletTxIds.forEach { txId ->
                    val tx = transactionRepository.getTransactionById(txId)
                    if (tx != null) {
                        transactionRepository.updateTransaction(tx.copy(walletId = newWalletId))
                    }
                }

                // Restore transfers
                lastDeletedWalletTransfers.forEach { transfer ->
                    val restoredFrom = if (transfer.fromWalletId == wallet.id) newWalletId else transfer.fromWalletId
                    val restoredTo = if (transfer.toWalletId == wallet.id) newWalletId else transfer.toWalletId
                    walletRepository.insertTransfer(
                        WalletTransfer(
                            fromWalletId = restoredFrom,
                            toWalletId = restoredTo,
                            amount = transfer.amount,
                            note = transfer.note,
                            timestamp = transfer.timestamp
                        )
                    )
                }

                // Recompute balance
                val newBalance = walletRepository.recomputeWalletBalance(newWalletId)
                val updatedWallet = walletRepository.getWalletById(newWalletId)
                if (updatedWallet != null) {
                    walletRepository.updateWallet(updatedWallet.copy(balance = newBalance))
                }

                lastDeletedWallet = null
                lastDeletedWalletTxIds = emptyList()
                lastDeletedWalletTransfers = emptyList()

                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to restore wallet.")
            }
        }
    }
}

data class CustomCommitment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val amount: Double,
    val day: Int,
    val notes: String = "",
    /** Minutes before the due day at which the alert fires; null = no reminder. */
    val reminderMinutesBefore: Int? = null,
    /** Calendar event created for this alert, kept in sync on every edit and delete. */
    val calendarEventId: Long? = null
)
