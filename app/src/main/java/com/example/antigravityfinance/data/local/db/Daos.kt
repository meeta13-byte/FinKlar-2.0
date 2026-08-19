package com.example.antigravityfinance.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY date DESC")
    fun getTransactionsByStatus(status: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Int): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE amount = :amount AND merchant = :merchant AND ABS(date - :date) < :timeThreshold")
    suspend fun findPotentialDuplicates(amount: Double, merchant: String, date: Long, timeThreshold: Long): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity): Unit

    @Delete
    suspend fun delete(transaction: TransactionEntity): Unit

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions(): Unit

    @Query("SELECT * FROM transactions WHERE walletId = :walletId ORDER BY date DESC")
    fun getTransactionsByWallet(walletId: Int): Flow<List<TransactionEntity>>
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE category = :category LIMIT 1")
    suspend fun getBudgetByCategory(category: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity): Unit

    @Delete
    suspend fun delete(budget: BudgetEntity): Unit
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY deadline ASC")
    fun getAllGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getGoalById(id: Int): SavingsGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Update
    suspend fun update(goal: SavingsGoalEntity): Unit

    @Delete
    suspend fun delete(goal: SavingsGoalEntity): Unit
}

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM investments")
    fun getAllInvestments(): Flow<List<InvestmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(investment: InvestmentEntity): Long

    @Update
    suspend fun update(investment: InvestmentEntity): Unit

    @Delete
    suspend fun delete(investment: InvestmentEntity): Unit
}

@Dao
interface RecurringMerchantDao {
    @Query("SELECT * FROM recurring_merchants")
    suspend fun getAllRecurringMerchants(): List<RecurringMerchantEntity>

    @Query("SELECT * FROM recurring_merchants WHERE merchant = :merchant LIMIT 1")
    suspend fun getRecurringMerchant(merchant: String): RecurringMerchantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recurringMerchant: RecurringMerchantEntity): Unit
}

@Dao
interface SplitDao {
    @Query("SELECT * FROM splits ORDER BY transactionDate DESC")
    fun getAllSplits(): Flow<List<SplitEntity>>

    @Query("SELECT * FROM splits WHERE id = :id LIMIT 1")
    suspend fun getSplitById(id: Int): SplitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(split: SplitEntity): Long

    @Update
    suspend fun update(split: SplitEntity): Unit

    @Delete
    suspend fun delete(split: SplitEntity): Unit
}

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets ORDER BY createdAt ASC")
    fun getAllWallets(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun getWalletById(id: Int): WalletEntity?

    @Query("SELECT * FROM wallets WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultWallet(): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallet: WalletEntity): Long

    @Update
    suspend fun update(wallet: WalletEntity)

    @Delete
    suspend fun delete(wallet: WalletEntity)

    @Query("SELECT SUM(amount) FROM transactions WHERE walletId = :walletId AND status = 'CONFIRMED' AND isIncome = 1")
    suspend fun getIncomeSum(walletId: Int): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE walletId = :walletId AND status = 'CONFIRMED' AND isIncome = 0")
    suspend fun getExpenseSum(walletId: Int): Double?

    @Query("SELECT SUM(amount) FROM wallet_transfers WHERE toWalletId = :walletId")
    suspend fun getTransfersInSum(walletId: Int): Double?

    @Query("SELECT SUM(amount) FROM wallet_transfers WHERE fromWalletId = :walletId")
    suspend fun getTransfersOutSum(walletId: Int): Double?

    @Query("SELECT COUNT(*) FROM transactions WHERE walletId = :walletId")
    suspend fun getTransactionCount(walletId: Int): Int
}

@Dao
interface WalletTransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transfer: WalletTransferEntity): Long

    @Query("SELECT * FROM wallet_transfers ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<WalletTransferEntity>>

    @Query("SELECT * FROM wallet_transfers WHERE fromWalletId = :walletId OR toWalletId = :walletId ORDER BY timestamp DESC")
    fun getTransfersForWallet(walletId: Int): Flow<List<WalletTransferEntity>>
}


