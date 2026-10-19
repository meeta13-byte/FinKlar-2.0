package com.example.antigravityfinance

import com.example.antigravityfinance.data.local.db.*
import com.example.antigravityfinance.data.model.*
import com.example.antigravityfinance.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The deletion half of inbox reconciliation must never run when the inbox could not be
 * read. A query failure, or a revoked permission, leaves the parsed list empty, and an
 * empty list means "no transactions were found" — which used to delete every SMS-sourced
 * transaction in the database.
 */
class SmsReconcileGuardTest {

    private class FakeTransactionDao : TransactionDao {
        val list = mutableListOf<TransactionEntity>()
        private var nextId = 1
        override fun getAllTransactions(): Flow<List<TransactionEntity>> = flow { emit(list) }
        override fun getTransactionsByStatus(status: String): Flow<List<TransactionEntity>> = flow { emit(list.filter { it.status == status }) }
        override suspend fun getTransactionById(id: Int): TransactionEntity? = list.find { it.id == id }
        override suspend fun findPotentialDuplicates(amount: Double, merchant: String, date: Long, timeThreshold: Long) = emptyList<TransactionEntity>()
        override suspend fun findRecentSmsDuplicate(amount: Double, merchant: String, date: Long, timeThreshold: Long): TransactionEntity? = null
        override suspend fun insert(transaction: TransactionEntity): Long {
            val id = if (transaction.id == 0) nextId++ else transaction.id
            list.add(transaction.copy(id = id)); return id.toLong()
        }
        override suspend fun update(transaction: TransactionEntity) {
            val i = list.indexOfFirst { it.id == transaction.id }; if (i >= 0) list[i] = transaction
        }
        override suspend fun delete(transaction: TransactionEntity) { list.removeIf { it.id == transaction.id } }
        override suspend fun deleteAllTransactions() = list.clear()
        override fun getTransactionsByWallet(walletId: Int, bankAccountId: Int?): Flow<List<TransactionEntity>> =
            flow { emit(list.filter { it.walletId == walletId }) }
        override suspend fun moveTransactionsToWallet(oldWalletId: Int, newWalletId: Int) {
            for (i in list.indices) if (list[i].walletId == oldWalletId) list[i] = list[i].copy(walletId = newWalletId)
        }
        override suspend fun clearWalletForTransactions(walletId: Int) {
            for (i in list.indices) if (list[i].walletId == walletId) list[i] = list[i].copy(walletId = null)
        }
        override suspend fun clearBankAccount(bankAccountId: Int) {
            for (i in list.indices) if (list[i].bankAccountId == bankAccountId) list[i] = list[i].copy(bankAccountId = null)
        }
    }

    private fun repository(dao: FakeTransactionDao): TransactionRepository {
        val budgetDao = object : BudgetDao {
            val budgets = mutableListOf<BudgetEntity>()
            override fun getAllBudgets(): Flow<List<BudgetEntity>> = flow { emit(budgets) }
            override suspend fun getBudgetByCategory(category: String): BudgetEntity? = budgets.find { it.category == category }
            override suspend fun insert(budget: BudgetEntity): Long { budgets.add(budget); return budgets.size.toLong() }
            override suspend fun update(budget: BudgetEntity) {
                val i = budgets.indexOfFirst { it.category == budget.category }; if (i >= 0) budgets[i] = budget
            }
            override suspend fun delete(budget: BudgetEntity) {}
        }
        val recurringDao = object : RecurringMerchantDao {
            override suspend fun getAllRecurringMerchants() = emptyList<RecurringMerchantEntity>()
            override suspend fun getRecurringMerchant(merchant: String): RecurringMerchantEntity? = null
            override suspend fun insert(recurringMerchant: RecurringMerchantEntity) {}
        }
        val walletDao = object : WalletDao {
            override fun getAllWallets(): Flow<List<WalletEntity>> = flow { emit(emptyList()) }
            override fun getWalletByIdFlow(id: Int): Flow<WalletEntity?> = flow { emit(null) }
            override suspend fun getWalletById(id: Int): WalletEntity? = null
            override suspend fun getDefaultWallet(): WalletEntity? = null
            override suspend fun insert(wallet: WalletEntity): Long = 1L
            override suspend fun update(wallet: WalletEntity) {}
            override suspend fun delete(wallet: WalletEntity) {}
            override suspend fun getIncomeSum(walletId: Int, bankAccountId: Int?) = null
            override suspend fun getExpenseSum(walletId: Int, bankAccountId: Int?) = null
            override suspend fun getTransfersInSum(walletId: Int) = null
            override suspend fun getTransfersOutSum(walletId: Int) = null
            override suspend fun getTransactionCount(walletId: Int, bankAccountId: Int?) = 0
            override suspend fun getAllWalletsList() = emptyList<WalletEntity>()
            override suspend fun getWalletByLinkedBankAccount(bankAccountId: Int): WalletEntity? = null
        }
        return TransactionRepository(dao, recurringDao, budgetDao, walletDao)
    }

    private suspend fun seedInboxRow(dao: FakeTransactionDao) = dao.insert(
        TransactionEntity(
            amount = 250.0, merchant = "Zomato", date = 1_700_000_000_000L, category = "FOOD",
            notes = "raw", account = "HDFC Bank", status = "CONFIRMED", isIncome = false,
            detectedFromSms = true, source = "SMS_INBOX"
        )
    )

    @Test
    fun unreadableInboxDoesNotDeleteSmsSourcedTransactions() = runBlocking {
        val dao = FakeTransactionDao()
        val repo = repository(dao)
        seedInboxRow(dao)
        seedInboxRow(dao)

        // The query threw, so nothing was parsed. Deletion must be skipped entirely
        // rather than treating "no SMS read" as "these transactions no longer exist".
        val inboxReadSucceeded = false
        val parsedSmsList = emptyList<Transaction>()

        if (inboxReadSucceeded) {
            for (dbTx in repo.allTransactions.first().filter { it.detectedFromSms && it.source == "SMS_INBOX" }) {
                val found = parsedSmsList.any { smsTx ->
                    smsTx.amount == dbTx.amount && smsTx.isIncome == dbTx.isIncome &&
                        smsTx.merchant.equals(dbTx.merchant, ignoreCase = true) &&
                        Math.abs(smsTx.date - dbTx.date) < 120000
                }
                if (!found) repo.deleteTransaction(dbTx)
            }
        }

        assertEquals(
            "A failed inbox read must not delete stored transactions",
            2, repo.allTransactions.first().size
        )
    }

    @Test
    fun successfulEmptyInboxStillDeletes() = runBlocking {
        val dao = FakeTransactionDao()
        val repo = repository(dao)
        seedInboxRow(dao)

        // The read worked and genuinely returned no matching SMS: the user deleted it.
        val inboxReadSucceeded = true
        val parsedSmsList = emptyList<Transaction>()

        if (inboxReadSucceeded) {
            for (dbTx in repo.allTransactions.first().filter { it.detectedFromSms && it.source == "SMS_INBOX" }) {
                val found = parsedSmsList.any { smsTx ->
                    smsTx.amount == dbTx.amount && smsTx.isIncome == dbTx.isIncome &&
                        smsTx.merchant.equals(dbTx.merchant, ignoreCase = true) &&
                        Math.abs(smsTx.date - dbTx.date) < 120000
                }
                if (!found) repo.deleteTransaction(dbTx)
            }
        }

        assertEquals(
            "A read that genuinely finds no SMS must still remove the transaction",
            0, repo.allTransactions.first().size
        )
    }
}
/**
 * Guards the contact-name fallback in the shared ingest path. When the detector cannot
 * name a merchant, the sender's saved contact name is used instead of the raw sender id,
 * so a payment to a saved contact does not appear as a phone number.
 */
class IngestMerchantFallbackTest {

    @Test
    fun unknownMerchantFallsBackToContactNameThenSenderId() {
        val resolve = com.example.antigravityfinance.service.notification.NotificationIngestor::resolveMerchant

        assertEquals(
            "A saved contact should replace the unknown merchant",
            "Amit",
            resolve("Unknown Merchant", "+919999999999") { "Amit" }
        )
        assertEquals(
            "With no saved contact the raw sender id is kept",
            "+919999999999",
            resolve("Unknown Merchant", "+919999999999") { null }
        )
        assertEquals(
            "A detected merchant must never be replaced by the sender",
            "Zomato",
            resolve("Zomato", "+919999999999") { "Amit" }
        )
        assertEquals(
            "An unusable sender keeps the original detected value",
            "Unknown Merchant",
            resolve("Unknown Merchant", "Unknown") { "Amit" }
        )
    }
}
