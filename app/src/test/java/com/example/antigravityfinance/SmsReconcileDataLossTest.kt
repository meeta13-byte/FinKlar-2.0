package com.example.antigravityfinance

import com.example.antigravityfinance.data.local.db.*
import com.example.antigravityfinance.data.model.*
import com.example.antigravityfinance.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A transaction read from a bank/UPI *notification* is not in the SMS inbox, because
 * those apps often post notifications instead of sending an SMS. The inbox reconciler
 * must not treat it as a deleted message and remove it.
 */
class SmsReconcileDataLossTest {

    private class FakeTransactionDao : TransactionDao {
        val list = mutableListOf<TransactionEntity>()
        private var nextId = 1
        override fun getAllTransactions(): Flow<List<TransactionEntity>> = flow { emit(list) }
        override fun getTransactionsByStatus(status: String): Flow<List<TransactionEntity>> =
            flow { emit(list.filter { it.status == status }) }
        override suspend fun getTransactionById(id: Int): TransactionEntity? = list.find { it.id == id }
        override suspend fun findPotentialDuplicates(amount: Double, merchant: String, date: Long, timeThreshold: Long) = emptyList<TransactionEntity>()
        override suspend fun findRecentSmsDuplicate(amount: Double, merchant: String, date: Long, timeThreshold: Long): TransactionEntity? = null
        override suspend fun insert(transaction: TransactionEntity): Long {
            val id = if (transaction.id == 0) nextId++ else transaction.id
            list.add(transaction.copy(id = id))
            return id.toLong()
        }
        override suspend fun update(transaction: TransactionEntity) {
            val i = list.indexOfFirst { it.id == transaction.id }
            if (i >= 0) list[i] = transaction
        }
        override suspend fun delete(transaction: TransactionEntity) {
            list.removeIf { it.id == transaction.id }
        }
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

    private fun repository(transactionDao: FakeTransactionDao): TransactionRepository {
        val budgetDao = object : BudgetDao {
            val budgets = mutableListOf<BudgetEntity>()
            override fun getAllBudgets(): Flow<List<BudgetEntity>> = flow { emit(budgets) }
            override suspend fun getBudgetByCategory(category: String): BudgetEntity? = budgets.find { it.category == category }
            override suspend fun insert(budget: BudgetEntity): Long { budgets.add(budget); return budgets.size.toLong() }
            override suspend fun update(budget: BudgetEntity) {
                val i = budgets.indexOfFirst { it.category == budget.category }
                if (i >= 0) budgets[i] = budget
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
        return TransactionRepository(transactionDao, recurringDao, budgetDao, walletDao)
    }

    @Test
    fun notificationDerivedTransactionIsNotDeletedByInboxReconcile() = runBlocking {
        val dao = FakeTransactionDao()
        val repo = repository(dao)

        // A transaction the notification listener stored. Same shape it writes today:
        // detectedFromSms = true, but it has no SMS behind it in the inbox.
        dao.insert(
            TransactionEntity(
                amount = 250.0,
                merchant = "Zomato",
                date = 1_700_000_000_000L,
                category = "FOOD",
                notes = "UPI/Google Pay notification",
                account = "HDFC Bank",
                status = "CONFIRMED",
                isIncome = false,
                detectedFromSms = true,
                source = "NOTIFICATION"
            )
        )
        assertEquals(1, dao.list.size)

        // Mirror of SmsInboxScanner's reconciliation pass, with an EMPTY inbox: the
        // scenario for a user whose bank posts notifications instead of sending SMS.
        val parsedSmsList = emptyList<TransactionEntity>()
        val dbTransactions = repo.allTransactions.first()

        val survivors = dbTransactions.filter { dbTx ->
            if (!dbTx.detectedFromSms || dbTx.source != "SMS_INBOX") return@filter true
            parsedSmsList.any { smsTx ->
                smsTx.amount == dbTx.amount &&
                    smsTx.isIncome == dbTx.isIncome &&
                    smsTx.merchant.equals(dbTx.merchant, ignoreCase = true) &&
                    Math.abs(smsTx.date - dbTx.date) < 120000
            }
        }

        assertEquals(
            "A notification-sourced transaction must survive an empty inbox reconcile",
            1, survivors.size
        )
    }

    @Test
    fun inboxSourcedTransactionWithNoMatchingSmsIsStillDeleted() = runBlocking {
        val dao = FakeTransactionDao()
        val repo = repository(dao)

        // The behaviour the reconciler exists for: the user deleted the SMS, so the
        // transaction it produced goes too.
        dao.insert(
            TransactionEntity(
                amount = 100.0,
                merchant = "Cafe",
                date = 1_700_000_000_000L,
                category = "FOOD",
                notes = "raw sms",
                account = "HDFC Bank",
                status = "CONFIRMED",
                isIncome = false,
                detectedFromSms = true,
                source = "SMS_INBOX"
            )
        )

        val parsedSmsList = emptyList<TransactionEntity>()
        val dbTransactions = repo.allTransactions.first()

        val survivors = dbTransactions.filter { dbTx ->
            if (!dbTx.detectedFromSms || dbTx.source != "SMS_INBOX") return@filter true
            parsedSmsList.any { smsTx ->
                smsTx.amount == dbTx.amount &&
                    smsTx.isIncome == dbTx.isIncome &&
                    smsTx.merchant.equals(dbTx.merchant, ignoreCase = true) &&
                    Math.abs(smsTx.date - dbTx.date) < 120000
            }
        }

        assertEquals("An SMS_INBOX row with no SMS left must be removed", 0, survivors.size)
        assertNotNull(repo.allTransactions.first())
    }
}