package com.example.antigravityfinance.service.sms

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.antigravityfinance.data.model.Transaction
import com.example.antigravityfinance.data.model.TransactionStatus
import com.example.antigravityfinance.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first

import com.example.antigravityfinance.service.sms.detection.*

object SmsInboxScanner {
    
    data class SyncSmsResult(
        val addedCount: Int,
        val latestBalance: Double?,
        val maxTimestamp: Long
    )

    suspend fun reconcileInbox(
        context: Context,
        repository: TransactionRepository,
        bankAccountRepository: com.example.antigravityfinance.data.repository.BankAccountRepository
    ): SyncSmsResult {
        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        )
        if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.d("SmsInboxScanner", "SMS Permission not granted. Skipping reconciliation.")
            return SyncSmsResult(0, null, 0L)
        }

        val securityHelper = com.example.antigravityfinance.service.security.SecurityHelper(context.applicationContext)
        val trustedSenders = securityHelper.getTrustedSenders()

        var addedCount = 0
        var latestBalance: Double? = null
        var latestBalanceTimestamp: Long = 0
        var maxTimestamp: Long = 0

        val parsedSmsList = mutableListOf<Transaction>()
        var inboxReadSucceeded = false

        try {
            val inboxUri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            
            val cursor = context.contentResolver.query(
                inboxUri,
                projection,
                null,
                null,
                "date DESC"
            )

            if (cursor == null) {
                // A null cursor is not an empty inbox, it is an unreadable one.
                Log.w("SmsInboxScanner", "Inbox query returned no cursor")
                return SyncSmsResult(0, null, 0L)
            }

            cursor.use { c ->
                val bodyIndex = c.getColumnIndexOrThrow("body")
                val dateIndex = c.getColumnIndexOrThrow("date")
                val addressIndex = c.getColumnIndexOrThrow("address")
                
                while (c.moveToNext()) {
                    val body = c.getString(bodyIndex) ?: continue
                    val date = c.getLong(dateIndex)
                    val address = c.getString(addressIndex) ?: ""
                    
                    val result = SmsDetectionModule.detect(body, address, date, trustedSenders)
                    if (result.autoAdd && result.amount != null) {
                        var resolvedMerchant = result.merchantOrSender
                        if (resolvedMerchant == "Unknown Merchant" && address.isNotBlank()) {
                            val contactName = getContactName(context, address)
                            if (contactName != null) {
                                resolvedMerchant = contactName
                            }
                        }
                        val category = AutoCategorizer.categorize(resolvedMerchant)
                        val transaction = Transaction(
                            amount = result.amount,
                            merchant = resolvedMerchant,
                            date = date,
                            category = category,
                            notes = result.rawSms,
                            account = result.bankName.ifBlank { "Bank SMS" },
                            status = TransactionStatus.CONFIRMED,
                            isIncome = result.transactionType == TransactionType.CREDIT,
                            detectedFromSms = true,
                            bankAccountId = bankAccountRepository.recordDetection(result),
                            source = "SMS_INBOX"
                        )
                        parsedSmsList.add(transaction)
                        
                        if (date > maxTimestamp) {
                            maxTimestamp = date
                        }
                        
                        val balance = result.availableBalance
                        if (balance != null && date > latestBalanceTimestamp) {
                            latestBalance = balance
                            latestBalanceTimestamp = date
                        }
                    }
                }
            }
            inboxReadSucceeded = true
        } catch (e: Exception) {
            Log.e("SmsInboxScanner", "Error scanning SMS inbox for reconciliation", e)
        }

        if (!inboxReadSucceeded) {
            // The inbox could not be read, so an empty parse list means "unknown", not
            // "these SMS were deleted". Reconciling against it would remove every
            // SMS-sourced transaction in the database.
            Log.w("SmsInboxScanner", "Inbox unreadable; skipping reconciliation")
            return SyncSmsResult(0, null, maxTimestamp)
        }
        
        val dbTransactions = kotlinx.coroutines.runBlocking {
            repository.allTransactions.first()
        }
        
        // 1. Insert transactions that are in SMS but not in DB
        for (smsTx in parsedSmsList) {
            val exists = dbTransactions.any { dbTx ->
                dbTx.amount == smsTx.amount &&
                dbTx.isIncome == smsTx.isIncome &&
                dbTx.merchant.equals(smsTx.merchant, ignoreCase = true) &&
                Math.abs(dbTx.date - smsTx.date) < 120000
            }
            if (!exists) {
                kotlinx.coroutines.runBlocking {
                    repository.insertTransaction(smsTx)
                }
                addedCount++
            }
        }
        
        
        
        // 3. Delete only SMS-derived transactions that are no longer found in SMS.
        //
        // Restricted to rows that were actually read from the SMS inbox. Transactions the
        // notification listener stored carry the same detectedFromSms flag but have no SMS
        // behind them, because those banks post notifications instead of sending messages;
        // without this guard every one of them was deleted on the next app launch.
        for (dbTx in dbTransactions.filter { it.detectedFromSms && it.source == "SMS_INBOX" }) {
            val foundInSms = parsedSmsList.any { smsTx ->
                smsTx.amount == dbTx.amount &&
                smsTx.isIncome == dbTx.isIncome &&
                smsTx.merchant.equals(dbTx.merchant, ignoreCase = true) &&
                Math.abs(smsTx.date - dbTx.date) < 120000
            }
            if (!foundInSms) {
                Log.d("SmsInboxScanner", "Deleting an unverified transaction")
                kotlinx.coroutines.runBlocking {
                    repository.deleteTransaction(dbTx)
                }
            }
        }
        
        return SyncSmsResult(addedCount, latestBalance, maxTimestamp)
    }

    fun getContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        )
        if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val uri = Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (e: Exception) {
            Log.e("SmsInboxScanner", "Contact lookup failed: " + e.javaClass.simpleName)
        }
        return null
    }
}
