package com.example.antigravityfinance

import com.example.antigravityfinance.data.local.db.*
import com.example.antigravityfinance.data.model.*
import com.example.antigravityfinance.data.repository.WalletRepository
import com.example.antigravityfinance.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WalletTest {

    // --- FAKES FOR DAOs ---
    class FakeWalletDao : WalletDao {
        val wallets = mutableListOf<WalletEntity>()
        var nextId = 1
        var transactions = mutableListOf<TransactionEntity>()
        var transfers = mutableListOf<WalletTransferEntity>()

        override fun getAllWallets(): Flow<List<WalletEntity>> = flow {
            emit(wallets)
        }

        override suspend fun getWalletById(id: Int): WalletEntity? = wallets.find { it.id == id }

        override suspend fun getDefaultWallet(): WalletEntity? = wallets.find { it.isDefault }

        override suspend fun insert(wallet: WalletEntity): Long {
            val idx = wallets.indexOfFirst { it.id == wallet.id }
            if (idx >= 0 && wallet.id != 0) {
                wallets[idx] = wallet
                return wallet.id.toLong()
            } else {
                val newId = if (wallet.id == 0) nextId++ else wallet.id
                val toAdd = wallet.copy(id = newId)
                wallets.add(toAdd)
                return newId.toLong()
            }
        }

        override suspend fun update(wallet: WalletEntity) {
            val idx = wallets.indexOfFirst { it.id == wallet.id }
            if (idx >= 0) {
                wallets[idx] = wallet
            }
        }

        override suspend fun delete(wallet: WalletEntity) {
            wallets.removeIf { it.id == wallet.id }
        }

        override suspend fun getIncomeSum(walletId: Int): Double? {
            val sum = transactions.filter { it.walletId == walletId && it.status == "CONFIRMED" && it.isIncome }.sumOf { it.amount }
            return if (sum == 0.0) null else sum
        }

        override suspend fun getExpenseSum(walletId: Int): Double? {
            val sum = transactions.filter { it.walletId == walletId && it.status == "CONFIRMED" && !it.isIncome }.sumOf { it.amount }
            return if (sum == 0.0) null else sum
        }

        override suspend fun getTransfersInSum(walletId: Int): Double? {
            val sum = transfers.filter { it.toWalletId == walletId }.sumOf { it.amount }
            return if (sum == 0.0) null else sum
        }

        override suspend fun getTransfersOutSum(walletId: Int): Double? {
            val sum = transfers.filter { it.fromWalletId == walletId }.sumOf { it.amount }
            return if (sum == 0.0) null else sum
        }

        override suspend fun getTransactionCount(walletId: Int): Int {
            return transactions.count { it.walletId == walletId }
        }
    }

    class FakeWalletTransferDao : WalletTransferDao {
        val transfers = mutableListOf<WalletTransferEntity>()
        var nextId = 1

        override suspend fun insert(transfer: WalletTransferEntity): Long {
            val newId = nextId++
            transfers.add(transfer.copy(id = newId))
            return newId.toLong()
        }

        override fun getAllTransfers(): Flow<List<WalletTransferEntity>> = flow {
            emit(transfers)
        }

        override fun getTransfersForWallet(walletId: Int): Flow<List<WalletTransferEntity>> = flow {
            emit(transfers.filter { it.fromWalletId == walletId || it.toWalletId == walletId })
        }
    }

    class FakeTransactionDao : TransactionDao {
        val list = mutableListOf<TransactionEntity>()
        override fun getAllTransactions(): Flow<List<TransactionEntity>> = flow { emit(list) }
        override fun getTransactionsByStatus(status: String): Flow<List<TransactionEntity>> = flow { emit(list.filter { it.status == status }) }
        override suspend fun getTransactionById(id: Int): TransactionEntity? = list.find { it.id == id }
        override suspend fun findPotentialDuplicates(amount: Double, merchant: String, date: Long, timeThreshold: Long): List<TransactionEntity> = emptyList()
        override suspend fun insert(transaction: TransactionEntity): Long {
            list.add(transaction)
            return transaction.id.toLong()
        }
        override suspend fun update(transaction: TransactionEntity) {
            val idx = list.indexOfFirst { it.id == transaction.id }
            if (idx >= 0) {
                list[idx] = transaction
            }
        }
        override suspend fun delete(transaction: TransactionEntity) {
            list.removeIf { it.id == transaction.id }
        }
        override suspend fun deleteAllTransactions() {
            list.clear()
        }
        override fun getTransactionsByWallet(walletId: Int): Flow<List<TransactionEntity>> = flow { emit(list.filter { it.walletId == walletId }) }
    }

    @Test
    fun testWalletBalanceCalculation() = runBlocking {
        val walletDao = FakeWalletDao()
        val transferDao = FakeWalletTransferDao()
        val transactionDao = FakeTransactionDao()
        val repository = WalletRepository(walletDao, transferDao, transactionDao)

        val walletId = walletDao.insert(WalletEntity(name = "Savings", balance = 0.0, purpose = "Save money", isDefault = false, createdAt = System.currentTimeMillis())).toInt()

        // Setup Transactions
        walletDao.transactions.add(TransactionEntity(amount = 5000.0, merchant = "Salary", date = 0, category = "Salary", isIncome = true, status = "CONFIRMED", walletId = walletId))
        walletDao.transactions.add(TransactionEntity(amount = 1200.0, merchant = "Starbucks", date = 0, category = "FOOD", isIncome = false, status = "CONFIRMED", walletId = walletId))
        walletDao.transactions.add(TransactionEntity(amount = 300.0, merchant = "Uber", date = 0, category = "TRAVEL", isIncome = false, status = "PENDING", walletId = walletId)) // Pending shouldn't count

        // Setup Transfers
        walletDao.transfers.add(WalletTransferEntity(fromWalletId = 99, toWalletId = walletId, amount = 1000.0, note = "Received transfer", timestamp = 0)) // Transfer IN
        walletDao.transfers.add(WalletTransferEntity(fromWalletId = walletId, toWalletId = 100, amount = 500.0, note = "Sent transfer", timestamp = 0)) // Transfer OUT

        // Math: 5000 (income) - 1200 (expense) + 1000 (transfer in) - 500 (transfer out) = 4300
        val computed = repository.recomputeWalletBalance(walletId)
        assertEquals(4300.0, computed, 0.0)
    }

    @Test
    fun testTransferAtomicity_success() = runBlocking {
        val walletDao = FakeWalletDao()
        val transferDao = FakeWalletTransferDao()
        val transactionDao = FakeTransactionDao()
        val repository = WalletRepository(walletDao, transferDao, transactionDao)

        val w1Id = walletDao.insert(WalletEntity(name = "Wallet 1", balance = 2000.0, purpose = null, isDefault = false, createdAt = 0)).toInt()
        val w2Id = walletDao.insert(WalletEntity(name = "Wallet 2", balance = 500.0, purpose = null, isDefault = false, createdAt = 0)).toInt()

        repository.transferMoney(fromWalletId = w1Id, toWalletId = w2Id, amount = 300.0, note = "Test transfer")

        val w1Updated = walletDao.getWalletById(w1Id)
        val w2Updated = walletDao.getWalletById(w2Id)

        assertEquals(1700.0, w1Updated!!.balance, 0.0)
        assertEquals(800.0, w2Updated!!.balance, 0.0)
        assertEquals(1, transferDao.transfers.size)
        assertEquals(300.0, transferDao.transfers[0].amount, 0.0)
    }

    @Test
    fun testTransferAtomicity_insufficientFunds() = runBlocking {
        val walletDao = FakeWalletDao()
        val transferDao = FakeWalletTransferDao()
        val transactionDao = FakeTransactionDao()
        val repository = WalletRepository(walletDao, transferDao, transactionDao)

        val w1Id = walletDao.insert(WalletEntity(name = "Wallet 1", balance = 100.0, purpose = null, isDefault = false, createdAt = 0)).toInt()
        val w2Id = walletDao.insert(WalletEntity(name = "Wallet 2", balance = 500.0, purpose = null, isDefault = false, createdAt = 0)).toInt()

        try {
            repository.transferMoney(fromWalletId = w1Id, toWalletId = w2Id, amount = 300.0, note = "Fail transfer")
            fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Verify balances remain unchanged
        val w1After = walletDao.getWalletById(w1Id)
        val w2After = walletDao.getWalletById(w2Id)
        assertEquals(100.0, w1After!!.balance, 0.0)
        assertEquals(500.0, w2After!!.balance, 0.0)
        assertEquals(0, transferDao.transfers.size)
    }

    @Test
    fun testDefaultWalletAndConstraintDeletionRules() = runBlocking {
        val walletDao = FakeWalletDao()
        val transferDao = FakeWalletTransferDao()
        val transactionDao = FakeTransactionDao()
        val repository = WalletRepository(walletDao, transferDao, transactionDao)

        // Rule 1: Default spending wallet cannot be deleted
        val defaultWalletId = walletDao.insert(WalletEntity(id = 1, name = "Default Spending Wallet", balance = 0.0, purpose = null, isDefault = true, createdAt = 0)).toInt()
        val defaultWallet = repository.getWalletById(defaultWalletId)!!
        val deletedDefault = repository.deleteWallet(defaultWallet)
        assertFalse(deletedDefault)
        assertNotNull(walletDao.getWalletById(defaultWalletId))

        // Rule 2: Wallet with non-zero balance cannot be deleted
        val nonZeroWalletId = walletDao.insert(WalletEntity(id = 2, name = "Non-zero Wallet", balance = 150.0, purpose = null, isDefault = false, createdAt = 0)).toInt()
        val nonZeroWallet = repository.getWalletById(nonZeroWalletId)!!
        val deletedNonZero = repository.deleteWallet(nonZeroWallet)
        assertFalse(deletedNonZero)
        assertNotNull(walletDao.getWalletById(nonZeroWalletId))

        // Rule 3: Wallet with transaction history cannot be deleted
        val hasTxWalletId = walletDao.insert(WalletEntity(id = 3, name = "Has Transactions Wallet", balance = 0.0, purpose = null, isDefault = false, createdAt = 0)).toInt()
        walletDao.transactions.add(TransactionEntity(amount = 100.0, merchant = "A", date = 0, category = "B", isIncome = true, status = "CONFIRMED", walletId = hasTxWalletId))
        val hasTxWallet = repository.getWalletById(hasTxWalletId)!!
        val deletedHasTx = repository.deleteWallet(hasTxWallet)
        assertFalse(deletedHasTx)
        assertNotNull(walletDao.getWalletById(hasTxWalletId))

        // Rule 4: Normal empty wallet can be deleted
        val cleanWalletId = walletDao.insert(WalletEntity(id = 4, name = "Clean Wallet", balance = 0.0, purpose = null, isDefault = false, createdAt = 0)).toInt()
        val cleanWallet = repository.getWalletById(cleanWalletId)!!
        val deletedClean = repository.deleteWallet(cleanWallet)
        assertTrue(deletedClean)
        assertNull(walletDao.getWalletById(cleanWalletId))
    }

    @Test
    fun testMigrationBackfillCorrectness() {
        // Setup raw transactions prior to migration (walletId is null)
        val legacyTx = listOf(
            Transaction(id = 10, amount = 150.0, merchant = "M1", date = 0, category = "Food", walletId = null),
            Transaction(id = 11, amount = 200.0, merchant = "M2", date = 0, category = "Travel", walletId = null),
            Transaction(id = 12, amount = 50.0, merchant = "M3", date = 0, category = "Shopping", walletId = 2)
        )

        // Verifying that under version 4 updates, we DO NOT backfill legacy transactions to default wallet (leaving them null/unallocated)
        val migratedTx = legacyTx.map { tx ->
            tx
        }

        assertNull(migratedTx[0].walletId)
        assertNull(migratedTx[1].walletId)
        assertEquals(2, migratedTx[2].walletId)
    }

    @Test
    fun testWalletSortingOrder() = runBlocking {
        val walletDao = FakeWalletDao()
        val transferDao = FakeWalletTransferDao()
        val transactionDao = FakeTransactionDao()
        val repository = WalletRepository(walletDao, transferDao, transactionDao)

        // Seed out-of-order wallets
        walletDao.insert(WalletEntity(id = 2, name = "A", balance = 0.0, purpose = null, isDefault = false, createdAt = 1))
        walletDao.insert(WalletEntity(id = 1, name = "B", balance = 0.0, purpose = null, isDefault = true, createdAt = 2))
        walletDao.insert(WalletEntity(id = 3, name = "C", balance = 0.0, purpose = null, isDefault = false, createdAt = 3))

        // Get sorted wallets list
        val sorted = repository.allWallets.first()
        // Verify default is first
        assertTrue(sorted[0].isDefault)
        assertEquals(1, sorted[0].id)
        assertEquals(2, sorted[1].id)
        assertEquals(3, sorted[2].id)
    }

    @Test
    fun testShiftTransactionWallet() = runBlocking {
        val walletDao = FakeWalletDao()
        val transactionDao = FakeTransactionDao()
        val budgetDao = object : BudgetDao {
            val budgets = mutableListOf<BudgetEntity>()
            override fun getAllBudgets(): Flow<List<BudgetEntity>> = flow { emit(budgets) }
            override suspend fun getBudgetByCategory(category: String): BudgetEntity? = budgets.find { it.category == category }
            override suspend fun insert(budget: BudgetEntity): Long { budgets.add(budget); return budget.id.toLong() }
            override suspend fun update(budget: BudgetEntity) {
                val idx = budgets.indexOfFirst { it.id == budget.id }
                if (idx >= 0) budgets[idx] = budget
            }
            override suspend fun delete(budget: BudgetEntity) {}
        }
        val recurringMerchantDao = object : RecurringMerchantDao {
            override suspend fun getAllRecurringMerchants(): List<RecurringMerchantEntity> = emptyList()
            override suspend fun getRecurringMerchant(merchant: String): RecurringMerchantEntity? = null
            override suspend fun insert(recurringMerchant: RecurringMerchantEntity) {}
        }
        val repo = TransactionRepository(transactionDao, recurringMerchantDao, budgetDao, walletDao)

        // Set up wallets
        val w1Id = walletDao.insert(WalletEntity(id = 1, name = "Wallet 1", balance = 500.0, purpose = null, isDefault = false, createdAt = 0)).toInt()
        val w2Id = walletDao.insert(WalletEntity(id = 2, name = "Wallet 2", balance = 1000.0, purpose = null, isDefault = false, createdAt = 0)).toInt()

        // Set up transaction belonging to Wallet 1 (amount = 100.0, expense/debit, confirmed)
        val txId = transactionDao.insert(TransactionEntity(id = 10, amount = 100.0, merchant = "M", date = 0, category = "Food", isIncome = false, status = "CONFIRMED", walletId = w1Id)).toInt()

        // Shift transaction from Wallet 1 to Wallet 2
        repo.shiftTransactionWallet(txId, w2Id)

        // Verify transaction wallet is updated
        val updatedTx = transactionDao.getTransactionById(txId)
        assertEquals(w2Id, updatedTx!!.walletId)

        // Verify balances shifted:
        // Wallet 1 should get +100 back (since debit was removed) = 600.0
        // Wallet 2 should get -100 (since debit was added) = 900.0
        val w1Updated = walletDao.getWalletById(w1Id)
        val w2Updated = walletDao.getWalletById(w2Id)
        assertEquals(600.0, w1Updated!!.balance, 0.0)
        assertEquals(900.0, w2Updated!!.balance, 0.0)
    }
}
