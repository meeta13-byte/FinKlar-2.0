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

    /**
     * Duplicate check restricted to rows this app itself read from a notification.
     * Manual entries and OCR receipts are excluded: matching one of those is not
     * evidence that the notification was already stored.
     */
    @Query("SELECT * FROM transactions WHERE amount = :amount AND merchant = :merchant AND detectedFromSms = 1 AND ABS(date - :date) < :timeThreshold LIMIT 1")
    suspend fun findRecentSmsDuplicate(amount: Double, merchant: String, date: Long, timeThreshold: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity): Unit

    @Delete
    suspend fun delete(transaction: TransactionEntity): Unit

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions(): Unit

    @Query("SELECT * FROM transactions WHERE walletId = :walletId AND (:bankAccountId IS NULL OR bankAccountId = :bankAccountId) ORDER BY date DESC")
    fun getTransactionsByWallet(walletId: Int, bankAccountId: Int?): Flow<List<TransactionEntity>>

    @Query("UPDATE transactions SET walletId = :newWalletId WHERE walletId = :oldWalletId")
    suspend fun moveTransactionsToWallet(oldWalletId: Int, newWalletId: Int)

    @Query("UPDATE transactions SET walletId = NULL WHERE walletId = :walletId")
    suspend fun clearWalletForTransactions(walletId: Int)

    /** Unlinking a bank account keeps the transactions, it only detaches them. */
    @Query("UPDATE transactions SET bankAccountId = NULL WHERE bankAccountId = :bankAccountId")
    suspend fun clearBankAccount(bankAccountId: Int)
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

    @Query("SELECT * FROM wallets WHERE id = :id")
    fun getWalletByIdFlow(id: Int): Flow<WalletEntity?>

    @Query("SELECT * FROM wallets WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultWallet(): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallet: WalletEntity): Long

    @Update
    suspend fun update(wallet: WalletEntity)

    @Delete
    suspend fun delete(wallet: WalletEntity)

    @Query("SELECT SUM(amount) FROM transactions WHERE walletId = :walletId AND status = 'CONFIRMED' AND isIncome = 1 AND (:bankAccountId IS NULL OR bankAccountId = :bankAccountId)")
    suspend fun getIncomeSum(walletId: Int, bankAccountId: Int?): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE walletId = :walletId AND status = 'CONFIRMED' AND isIncome = 0 AND (:bankAccountId IS NULL OR bankAccountId = :bankAccountId)")
    suspend fun getExpenseSum(walletId: Int, bankAccountId: Int?): Double?

    @Query("SELECT SUM(amount) FROM wallet_transfers WHERE toWalletId = :walletId")
    suspend fun getTransfersInSum(walletId: Int): Double?

    @Query("SELECT SUM(amount) FROM wallet_transfers WHERE fromWalletId = :walletId")
    suspend fun getTransfersOutSum(walletId: Int): Double?

    @Query("SELECT COUNT(*) FROM transactions WHERE walletId = :walletId AND (:bankAccountId IS NULL OR bankAccountId = :bankAccountId)")
    suspend fun getTransactionCount(walletId: Int, bankAccountId: Int?): Int

    @Query("SELECT * FROM wallets ORDER BY createdAt ASC")
    suspend fun getAllWalletsList(): List<WalletEntity>

    /** The wallet restricted to [bankAccountId], or null when no wallet is linked to it. */
    @Query("SELECT * FROM wallets WHERE linkedBankAccountId = :bankAccountId LIMIT 1")
    suspend fun getWalletByLinkedBankAccount(bankAccountId: Int): WalletEntity?
}

// Shortcut: a NULL :bankAccountId means "all bank accounts", which is what SQLite's
// `:param IS NULL OR` gives us for free — one query serves both the unrestricted and the
// single-account wallet instead of two near-identical queries per aggregate.
@Dao
interface BankAccountDao {
    @Query("SELECT * FROM bank_accounts ORDER BY lastSeenAt DESC")
    fun getAllBankAccounts(): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM bank_accounts WHERE id = :id")
    suspend fun getBankAccountById(id: Int): BankAccountEntity?

    @Query("SELECT * FROM bank_accounts WHERE bankName = :bankName AND last4 = :last4 LIMIT 1")
    suspend fun findByBankAndDigits(bankName: String, last4: String): BankAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: BankAccountEntity): Long

    @Query("UPDATE bank_accounts SET lastKnownBalance = :balance, lastSeenAt = :seenAt WHERE id = :id")
    suspend fun updateSeen(id: Int, balance: Double?, seenAt: Long)

    @Query("DELETE FROM bank_accounts WHERE id = :id")
    suspend fun delete(id: Int)
}

@Dao
interface WalletTransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transfer: WalletTransferEntity): Long

    @Query("SELECT * FROM wallet_transfers ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<WalletTransferEntity>>

    @Query("SELECT * FROM wallet_transfers WHERE fromWalletId = :walletId OR toWalletId = :walletId ORDER BY timestamp DESC")
    fun getTransfersForWallet(walletId: Int): Flow<List<WalletTransferEntity>>

    @Query("DELETE FROM wallet_transfers WHERE fromWalletId = :walletId OR toWalletId = :walletId")
    suspend fun deleteTransfersForWallet(walletId: Int)
}


