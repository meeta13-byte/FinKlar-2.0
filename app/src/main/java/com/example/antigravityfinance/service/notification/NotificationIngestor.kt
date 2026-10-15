package com.example.antigravityfinance.service.notification

import android.content.Context
import android.util.Log
import com.example.antigravityfinance.data.local.db.FinanceDatabase
import com.example.antigravityfinance.data.model.Transaction
import com.example.antigravityfinance.data.model.TransactionStatus
import com.example.antigravityfinance.data.repository.BankAccountRepository
import com.example.antigravityfinance.data.repository.TransactionRepository
import com.example.antigravityfinance.service.security.SecurityHelper
import com.example.antigravityfinance.service.sms.AutoCategorizer
import com.example.antigravityfinance.service.sms.detection.SmsDetectionModule
import com.example.antigravityfinance.service.sms.detection.TransactionType

/**
 * Single ingestion path shared by the SMS receiver, the inbox scanner and the
 * notification listener, so every source is parsed, scored, logged and stored identically.
 */
object NotificationIngestor {
    private const val TAG = "TxNotificationListener"

    suspend fun ingest(context: Context, body: String, senderId: String, timestamp: Long) {
        try {
            val securityHelper = SecurityHelper(context.applicationContext)
            val trustedSenders = securityHelper.getTrustedSenders()

            val result = SmsDetectionModule.detect(body, senderId, timestamp, trustedSenders)
            Log.i(TAG, "DETECTED ${result.classification} score=${result.confidenceScore} type=${result.transactionType} " +
                "amount=${result.amount} from $senderId")
            result.classificationReason.forEach { Log.d(TAG, "  reason: $it") }

            if (!result.autoAdd || result.amount == null) {
                Log.i(TAG, "REJECTED: autoAdd=${result.autoAdd} amount=${result.amount}")
                return
            }

            val db = FinanceDatabase.getDatabase(context)
            val repo = TransactionRepository(db.transactionDao(), db.recurringMerchantDao(), db.budgetDao(), db.walletDao())
            val bankAccountRepository = BankAccountRepository(db.bankAccountDao())

            result.availableBalance?.let { securityHelper.saveSyncedBalance(it) }

            val merchant = result.merchantOrSender.ifBlank { senderId }
            val bankAccountId = bankAccountRepository.recordDetection(result)

            // Only a previously notification-derived row counts as a duplicate.
            val duplicate = repo.findRecentSmsDuplicate(result.amount, merchant, timestamp)
            if (duplicate != null) {
                Log.i(TAG, "REJECTED: duplicate of transaction #${duplicate.id}")
                return
            }

            val tx = Transaction(
                amount = result.amount,
                merchant = merchant,
                date = timestamp,
                category = AutoCategorizer.categorize(merchant),
                notes = result.rawSms,
                account = result.bankName.ifBlank { "Bank SMS" },
                status = TransactionStatus.CONFIRMED,
                isIncome = result.transactionType == TransactionType.CREDIT,
                detectedFromSms = true,
                bankAccountId = bankAccountId
            )
            repo.insertTransaction(tx)
            Log.i(TAG, "STORED ${tx.amount} ${if (tx.isIncome) "credit" else "debit"} '$tx.merchant'")
        } catch (e: Exception) {
            Log.e(TAG, "FAILED to ingest from $senderId: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
}