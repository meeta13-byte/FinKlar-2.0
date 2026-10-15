package com.example.antigravityfinance

import com.example.antigravityfinance.service.sms.detection.SmsClassification
import com.example.antigravityfinance.service.sms.detection.SmsDetectionModule
import com.example.antigravityfinance.service.sms.detection.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real notification/SMS wording from several banks. Each sample here is a shape that
 * the detector used to get wrong, which is how transactions went missing.
 */
class BankSmsFormatsTest {

    private fun detect(body: String, sender: String) = kotlinx.coroutines.runBlocking {
        SmsDetectionModule.detect(body, sender, System.currentTimeMillis())
    }

    @Test
    fun hdfcDebit_isDebit_notIncome() {
        // "HDFC" contains the substring "cr", which used to mark this as a CREDIT.
        val result = detect(
            "Dear HDFC Bank Customer, Rs.1,250.50 has been debited from your HDFC Bank " +
                "A/c XXXXXX1234 towards SWIGGY on 13-06-26. Avl Bal: Rs.8,749.50",
            "AD-HDFCBK"
        )
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals(1250.50, result.amount!!, 0.0)
        assertEquals("1234", result.accountLastDigits)
        assertTrue(result.autoAdd)
    }

    @Test
    fun sbiCredit_neft_isCredit() {
        val result = detect(
            "Your A/c XXXXXX7890 has been credited with Rs.25,000.00 by salary. " +
                "UPI Ref No. 523418927561",
            "VK-SBIBNK"
        )
        assertEquals(TransactionType.CREDIT, result.transactionType)
        assertEquals(25000.0, result.amount!!, 0.0)
        assertTrue(result.autoAdd)
    }

    @Test
    fun iciciAtmWithdrawal_isDebit() {
        val result = detect(
            "ICICI Bank: INR 5,000.00 withdrawn at ATM WDL on 12-06-26. A/c XX5678. Avl bal INR 42,110.75",
            "AD-ICICIB"
        )
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals(5000.0, result.amount!!, 0.0)
        assertEquals("5678", result.accountLastDigits)
        assertTrue(result.autoAdd)
    }

    @Test
    fun axisCardSpend_isDebit() {
        val result = detect(
            "Rs.499.00 spent on CREDIT CARD ending 4567 at AMAZON on 13-06-26. Avl Lmt: Rs.9,500",
            "AD-AXISBK"
        )
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals(499.0, result.amount!!, 0.0)
        assertEquals("4567", result.accountLastDigits)
        assertTrue(result.autoAdd)
    }

    @Test
    fun phonePeUpiDebit_isDebit() {
        val result = detect("₹250.00 paid to Zomato from PhonePe. UPI Ref No. 523418927561", "PhonePe")
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals(250.0, result.amount!!, 0.0)
        assertTrue(result.autoAdd)
    }

    @Test
    fun paytmUpiDebit_isDebit() {
        val result = detect("Rs.150 paid to Amazon Pay. UPI Ref 5234189275", "PAYTM")
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals(150.0, result.amount!!, 0.0)
        assertTrue(result.autoAdd)
    }

    @Test
    fun crSuffixCreditFormat_isRead() {
        // No debit/credit verb and no currency symbol: the amount only exists in the
        // "Cr." ledger suffix. This was dropped entirely.
        val result = detect("A/c XX1234 Cr. 1200.00 NEFT Credited to your account", "SBINCR")
        assertEquals(TransactionType.CREDIT, result.transactionType)
        assertEquals(1200.0, result.amount!!, 0.0)
        assertTrue(result.autoAdd)
    }

    @Test
    fun drSuffixDebitFormat_isRead() {
        val result = detect("A/c XX1234 Dr. 500.00 UPI/CRDT txn", "HDFC")
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals(500.0, result.amount!!, 0.0)
    }

    @Test
    fun atmWdlShorthand_isRead() {
        // "WDL" only: several banks never spell out "withdrawn".
        val result = detect("A/c XXXXXX4321 ATM WDL Rs.2,000.00 on 11-06-26 Avl Bal Rs.30,100", "VI-HDFCBK")
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals(2000.0, result.amount!!, 0.0)
        assertEquals("4321", result.accountLastDigits)
    }

    @Test
    fun multiLineBody_isJoinedBeforeParsing() {
        // A single SMS split across two parts. Each part alone is unparseable.
        val parts = listOf(
            "Your HDFC Bank A/c XXXXXX1234 has been debited by Rs.850.00",
            "towards SWIGGY ORDER on 13-06-26. UPI Ref No. 523418927561. Avl Bal Rs.7,899.00"
        )
        val result = detect(parts.joinToString(" "), "AD-HDFCBK")
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals(850.0, result.amount!!, 0.0)
        assertTrue(result.autoAdd)
    }

    @Test
    fun loanAd_isStillRejected() {
        val result = detect(
            "Get pre-approved personal loan up to Rs.5 Lakhs. Apply now at https://x.com",
            "PROMOT"
        )
        assertEquals(SmsClassification.LOAN_AD, result.classification)
        assertTrue(!result.autoAdd)
    }

    @Test
    fun otp_isStillRejected() {
        val result = detect("Your OTP for NetBanking login is 987654. Do not share.", "AD-BANKOT")
        assertEquals(SmsClassification.OTP_OR_SECURITY_MESSAGE, result.classification)
        assertTrue(!result.autoAdd)
    }

    @Test
    fun friendChatMessage_isStillRejected() {
        val result = detect("Hey did you finish that report?", "+919999999999")
        assertEquals(SmsClassification.NON_TRANSACTION, result.classification)
        assertTrue(!result.autoAdd)
        assertNotNull(result.classificationReason)
    }
}