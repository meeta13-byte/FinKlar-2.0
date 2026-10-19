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

    /**
     * [source] records which path this message arrived by: SMS_RECEIVED for the broadcast
     * receiver, NOTIFICATION for the listener. Only SMS_INBOX rows are re-verifiable
     * against the inbox, so the two must not be conflated.
     */
    suspend fun ingest(
        context: Context,
        body: String,
        senderId: String,
        timestamp: Long,
        source: String
    ) {
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

            // Fall back to the sender's contact name before the raw sender id, so a payment to a
            // saved contact reads as that person rather than a phone number.
            val merchant = resolveMerchant(result.merchantOrSender, senderId) {
                com.example.antigravityfinance.service.sms.SmsInboxScanner.getContactName(context, senderId)
            }
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
                bankAccountId = bankAccountId,
                // Not SMS_INBOX: these banks post notifications rather than sending
                // messages, so the inbox reconciler cannot re-verify them.
                source = source
            )
            repo.insertTransaction(tx)
            Log.i(TAG, "STORED ${tx.amount} ${if (tx.isIncome) "credit" else "debit"} '$tx.merchant'")
        } catch (e: Exception) {
            Log.e(TAG, "FAILED to ingest from $senderId: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    /**
     * Picks the merchant to store: the name the detector found, else the sender's saved
     * contact name, else the sender id. Never leaves the literal "Unknown Merchant" in the
     * database when the sender is known.
     */
    fun resolveMerchant(detected: String, senderId: String, contactName: () -> String?): String {
        if (detected.isNotBlank() && detected != UNKNOWN_MERCHANT) return detected
        val senderUsable = senderId.isNotBlank() && senderId != "Unknown"
        if (!senderUsable) return detected.ifBlank { UNKNOWN_MERCHANT }
        val contact = contactName()
        return contact ?: senderId
    }

    private const val UNKNOWN_MERCHANT = "Unknown Merchant"
}