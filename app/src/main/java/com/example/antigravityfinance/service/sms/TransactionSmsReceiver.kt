package com.example.antigravityfinance.service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.antigravityfinance.data.model.Transaction
import com.example.antigravityfinance.data.model.TransactionStatus
import com.example.antigravityfinance.service.notification.NotificationIngestor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

data class ParsedSmsResult(
    val transaction: Transaction,
    val balance: Double?
)

class TransactionSmsReceiver : BroadcastReceiver() {
    companion object {
        /** Shortest plausible sender number; filters placeholders like "Unknown". */
        const val TAG = "TransactionSmsReceiver"
        private const val MIN_SENDER_DIGITS = 5
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        // A long bank SMS arrives split into parts. Parsing each part on its own loses
        // the amount, the account and the reference, so the parts are reassembled first.
        val body = messages.joinToString(" ") { it.messageBody.orEmpty() }.trim()
        if (body.isEmpty()) return

        val first = messages.first()
        val sender = first.originatingAddress
        if (sender.isNullOrBlank() || sender.equals("unknown", ignoreCase = true)) {
            Log.w(TAG, "REJECTED: SMS has no originating address")
            return
        }

        Log.i(TAG, "RECEIVED ${messages.size} part(s) from $sender: $body")
        parseAndProcessSms(context, body, sender, first.timestampMillis)
    }

    /**
     * Validates the sender before any state is written. Only the shape of the sender is
     * checked here: an unknown-but-well-formed sender still has to reach the detector,
     * which decides acceptance on the message content and its own sender scoring.
     *
     * shortcut: a whitelist is *not* used as a hard gate here, because that silently
     * discarded every bank the user had not added by hand. Replace with an explicit
     * allow-list only if the user-facing "trusted senders" setting is made mandatory.
     */
    private fun isSenderAcceptable(senderId: String): Boolean {
        val trimmed = senderId.trim()
        if (trimmed.isEmpty() || trimmed.equals("unknown", ignoreCase = true)) return false
        if (trimmed.any { it.isISOControl() }) return false
        val digits = trimmed.filter { it.isDigit() }
        return digits.length >= MIN_SENDER_DIGITS
    }

    fun parseAndProcessSms(
        context: Context, 
        body: String, 
        senderId: String = "Unknown", 
        timestamp: Long = System.currentTimeMillis()
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!isSenderAcceptable(senderId)) {
                Log.w(TAG, "REJECTED: malformed sender '$senderId'")
                return@launch
            }
            // The SMS receiver and the notification listener share one ingestion path so
            // the two sources cannot drift apart.
            NotificationIngestor.ingest(context, body, senderId, timestamp)
        }
    }
}

object SmsParser {
    fun parse(body: String): ParsedSmsResult? {
        val text = body.lowercase()

        val debitKeywords = listOf(
            "debited", "spent", "charged", "withdrawn", "withdrawal", "paid",
            "payment", "sent", "purchase", "dr", "debit"
        )
        val creditKeywords = listOf(
            "credited", "received", "deposited", "deposit", "refund", "salary",
            "cashback", "reversed", "reversal", "cr"
        )
        val isCredit = creditKeywords.any { keyword -> text.containsWord(keyword) }
        val isDebit = debitKeywords.any { keyword -> text.containsWord(keyword) } ||
            (text.containsWord("transferred") && !isCredit) ||
            (text.containsWord("transfer") && !isCredit)

        if (!isCredit && !isDebit) return null

        val amountRegexes = listOf(
            "(?:debited|credited|spent|paid|received|sent|charged|withdrawn|deposited|refund(?:ed)?|payment\\s+of|purchase\\s+of|txn\\s+(?:of|for)|transaction\\s+(?:of|for))\\s*(?:by|with|for|of)?\\s*(?:rs\\.?|inr|\\p{Sc})?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            "(?:rs\\.?|inr|\\p{Sc})\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:has\\s+been\\s+)?(?:debited|credited|spent|paid|received|sent|charged|withdrawn|deposited|refund(?:ed)?)",
            "(?:rs\\.?|inr|\\p{Sc})\\s*([\\d,]+(?:\\.\\d{1,2})?)"
        )
        var amount: Double? = null
        for (regex in amountRegexes) {
            val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(",", "")
                amount = amountStr?.toDoubleOrNull()
                if (amount != null) break
            }
        }
        if (amount == null) return null

        val isIncome = isCredit && !isDebit

        val balanceRegex = "(?:bal(?:ance)?|avl(?:\\.?\\s+bal(?:ance)?)?|available\\s+balance|avail\\s+bal|bal:)\\s*(?:is)?\\s*(?:rs\\.?|inr|\\p{Sc})?\\s*([\\d,]+(?:\\.\\d{1,2})?)"
        val balancePattern = Pattern.compile(balanceRegex, Pattern.CASE_INSENSITIVE)
        val balanceMatcher = balancePattern.matcher(text)
        var balance: Double? = null
        if (balanceMatcher.find()) {
            val balStr = balanceMatcher.group(1)?.replace(",", "")
            balance = balStr?.toDoubleOrNull()
        }

        var merchant = "Unknown Merchant"
        val merchantRegexes = listOf(
            "at\\s+([a-zA-Z0-9\\s\\.\\-_@]+?)(?:\\s+on|\\s+for|\\s+using|\\s+ending|\\.|$)",
            "paid\\s+to\\s+([a-zA-Z0-9\\s\\.\\-_@]+?)(?:\\s+on|\\s+for|\\s+using|\\.|$)",
            "to\\s+([a-zA-Z0-9\\s\\.\\-_@]+?)(?:\\s+on|\\s+for|\\s+using|\\.|$)",
            "from\\s+([a-zA-Z0-9\\s\\.\\-_@]+?)(?:\\s+on|\\s+for|\\s+using|\\.|$)",
            "towards\\s+([a-zA-Z0-9\\s\\.\\-_@]+?)(?:\\s+on|\\s+for|\\s+using|\\.|$)",
            "on\\s+([a-zA-Z0-9\\s\\.\\-_@]+?)(?:\\s+using|\\.|$)",
            "info:\\s*([a-zA-Z0-9\\s\\.\\-_@/]+?)(?:\\s+on|\\s+for|\\.|$)"
        )
        for (regex in merchantRegexes) {
            val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                var match = matcher.group(1)?.trim() ?: ""
                if (match.contains("@")) {
                    match = match.split("@")[0].trim()
                }
                val normalizedMatch = match.lowercase()
                if (
                    match.isNotEmpty() &&
                    !normalizedMatch.contains("account") &&
                    !normalizedMatch.contains("card") &&
                    !normalizedMatch.contains("avl bal")
                ) {
                    merchant = match
                    break
                }
            }
        }

        val category = AutoCategorizer.categorize(merchant)

        val transaction = Transaction(
            amount = amount,
            merchant = merchant,
            date = System.currentTimeMillis(),
            category = category,
            notes = "Parsed from SMS",
            account = if (text.contains("card")) "Credit Card" else "Bank Account",
            status = TransactionStatus.CONFIRMED,
            isIncome = isIncome,
            isRecurring = false,
            detectedFromSms = true
        )

        return ParsedSmsResult(transaction, balance)
    }

    private fun String.containsWord(keyword: String): Boolean {
        return Pattern.compile("\\b${Pattern.quote(keyword)}\\b", Pattern.CASE_INSENSITIVE)
            .matcher(this)
            .find()
    }
}

object AutoCategorizer {
    fun categorize(merchant: String): String {
        val m = merchant.lowercase()
        return when {
            m.contains("starbucks") || m.contains("cafe") || m.contains("restaurant") || m.contains("zomato") || m.contains("swiggy") || m.contains("food") || m.contains("canteen") -> "FOOD"
            m.contains("amazon") || m.contains("flipkart") || m.contains("myntra") || m.contains("shopping") || m.contains("mall") || m.contains("store") || m.contains("clothing") -> "SHOPPING"
            m.contains("grocery") || m.contains("supermarket") || m.contains("blinkit") || m.contains("zepto") || m.contains("bigbasket") || m.contains("mart") || m.contains("netflix") || m.contains("spotify") || m.contains("hotstar") || m.contains("cinema") || m.contains("theater") || m.contains("bookmyshow") || m.contains("entertainment") -> "LIVELIHOOD"
            m.contains("uber") || m.contains("ola") || m.contains("metro") || m.contains("petrol") || m.contains("fuel") || m.contains("travel") || m.contains("cab") || m.contains("train") -> "TRAVEL"
            m.contains("electricity") || m.contains("water") || m.contains("recharge") || m.contains("jio") || m.contains("airtel") || m.contains("bill") || m.contains("gas") || m.contains("rent") || m.contains("house") || m.contains("landlord") -> "COMPULSORY"
            m.contains("zerodha") || m.contains("groww") || m.contains("stock") || m.contains("mutual fund") || m.contains("sip") || m.contains("invest") -> "INVESTMENT"
            m.contains("salary") || m.contains("paycheck") || m.contains("employer") || m.contains("dividend") -> "SALARY"
            else -> "OTHERS"
        }
    }
}
