package com.example.antigravityfinance.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}

@Serializable
enum class TransactionCategory(val displayName: String) {
    TRAVEL("Travel"),
    FOOD("Food"),
    LIVELIHOOD("Livelihood"),
    COMPULSORY("Compulsory Expenses"),
    SHOPPING("Shopping"),
    INVESTMENT("Investments"),
    SALARY("Salary & Income"),
    OTHERS("Others")
}

@Serializable
enum class BudgetPeriod {
    WEEKLY,
    MONTHLY
}

@Serializable
enum class ThemeType {
    DYNAMIC,
    PROFESSIONAL
}

@Serializable
enum class LanguageType(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "Hindi")
}

@Serializable
enum class CurrencyType(val symbol: String, val code: String) {
    INR("₹", "INR"),
    USD("$", "USD"),
    EUR("€", "EUR")
}

@Serializable
data class Transaction(
    val id: Int = 0,
    val amount: Double,
    val merchant: String,
    val date: Long,
    val category: String,
    val notes: String = "",
    val account: String = "Cash",
    val status: TransactionStatus = TransactionStatus.CONFIRMED,
    val isIncome: Boolean = false,
    val isRecurring: Boolean = false,
    val detectedFromSms: Boolean = false,
    val walletId: Int? = null,
    /** Bank account this was read from; null for manual, OCR and voice entries. */
    val bankAccountId: Int? = null,
    /**
     * Which ingest path produced this row: SMS_RECEIVED, SMS_INBOX, NOTIFICATION, or
     * MANUAL. Only rows this app can re-verify against the inbox are SMS_INBOX.
     */
    val source: String = "MANUAL"
)

@Serializable
data class Wallet(
    val id: Int = 0,
    val name: String,
    val balance: Double,
    val purpose: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val investedAmount: Double = 0.0,
    val initialAmount: Double = 0.0,
    /** null = every bank account feeds this wallet (the default). */
    val linkedBankAccountId: Int? = null
)

/**
 * A bank account discovered from a transaction notification. Identity is the
 * (bankName, last4) pair because the full number is never available.
 */
@Serializable
data class BankAccount(
    val id: Int = 0,
    val bankName: String,
    val last4: String,
    val lastKnownBalance: Double? = null,
    val lastSeenAt: Long = System.currentTimeMillis()
) {
    val displayName: String get() = "$bankName ••••$last4"
}

@Serializable
data class WalletTransfer(
    val id: Int = 0,
    val fromWalletId: Int,
    val toWalletId: Int,
    val amount: Double,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)


@Serializable
data class Budget(
    val id: Int = 0,
    val category: String, // "All" or a specific TransactionCategory name
    val limitAmount: Double,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val spentAmount: Double = 0.0
)

@Serializable
data class SavingsGoal(
    val id: Int = 0,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val deadline: Long,
    val isCompleted: Boolean = false,
    val streakCount: Int = 0,
    val lastUpdatedStreak: Long = 0L
)

@Serializable
data class Investment(
    val id: Int = 0,
    val name: String,
    val type: String, // "Stock", "Mutual Fund", "SIP"
    val investedAmount: Double,
    val currentValuation: Double,
    val units: Double = 0.0,
    val averagePrice: Double = 0.0,
    val symbol: String = "" // e.g. "TCS", "RELIANCE"
)

@Serializable
data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SplitShare(
    val id: Int = 0,
    val transactionId: Int,
    val transactionAmount: Double,
    val transactionMerchant: String,
    val transactionDate: Long,
    val contactName: String,
    val shareAmount: Double,
    val isSettled: Boolean = false
)

