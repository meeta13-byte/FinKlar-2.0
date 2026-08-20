package com.example.antigravityfinance.data.repository

import androidx.room.withTransaction
import com.example.antigravityfinance.data.local.db.*
import com.example.antigravityfinance.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WalletRepository(
    private val walletDao: WalletDao,
    private val walletTransferDao: WalletTransferDao,
    private val transactionDao: TransactionDao,
    private val db: FinanceDatabase? = null
) {
    val allWallets: Flow<List<Wallet>> = walletDao.getAllWallets().map { list ->
        list.map { it.toDomain() }.sortedWith(compareByDescending<Wallet> { it.isDefault }.thenBy { it.id })
    }

    val allTransfers: Flow<List<WalletTransfer>> = walletTransferDao.getAllTransfers().map { list ->
        list.map { it.toDomain() }
    }

    fun getTransfersForWallet(walletId: Int): Flow<List<WalletTransfer>> {
        return walletTransferDao.getTransfersForWallet(walletId).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getTransactionsForWallet(walletId: Int): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByWallet(walletId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getWalletById(id: Int): Wallet? {
        return walletDao.getWalletById(id)?.toDomain()
    }

    suspend fun getDefaultWallet(): Wallet? {
        return walletDao.getDefaultWallet()?.toDomain()
    }

    suspend fun insertWallet(wallet: Wallet): Int {
        val entity = WalletEntity.fromDomain(wallet)
        return walletDao.insert(entity).toInt()
    }

    suspend fun updateWallet(wallet: Wallet) {
        walletDao.update(WalletEntity.fromDomain(wallet))
    }

    suspend fun deleteWallet(wallet: Wallet): Boolean {
        val entity = walletDao.getWalletById(wallet.id) ?: return false
        if (entity.isDefault) {
            return false
        }
        if (entity.balance != 0.0) {
            return false
        }
        val txCount = walletDao.getTransactionCount(wallet.id)
        if (txCount > 0) {
            return false
        }
        walletDao.delete(entity)
        return true
    }

    suspend fun setDefaultWallet(walletId: Int) {
        val block = suspend {
            val currentDefault = walletDao.getDefaultWallet()
            if (currentDefault != null) {
                walletDao.update(currentDefault.copy(isDefault = false))
            }
            val newDefault = walletDao.getWalletById(walletId)
            if (newDefault != null) {
                walletDao.update(newDefault.copy(isDefault = true))
            }
        }
        if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }

    suspend fun recomputeWalletBalance(walletId: Int): Double {
        val income = walletDao.getIncomeSum(walletId) ?: 0.0
        val expense = walletDao.getExpenseSum(walletId) ?: 0.0
        val transfersIn = walletDao.getTransfersInSum(walletId) ?: 0.0
        val transfersOut = walletDao.getTransfersOutSum(walletId) ?: 0.0
        return income - expense + transfersIn - transfersOut
    }

    suspend fun transferMoney(fromWalletId: Int, toWalletId: Int, amount: Double, note: String?) {
        if (amount <= 0.0) {
            throw IllegalArgumentException("Transfer amount must be greater than zero")
        }
        if (fromWalletId == toWalletId) {
            throw IllegalArgumentException("Source and destination wallets must be different")
        }
        val block = suspend {
            val fromWallet = walletDao.getWalletById(fromWalletId) ?: error("Source wallet not found")
            val toWallet = walletDao.getWalletById(toWalletId) ?: error("Destination wallet not found")
            
            if (fromWallet.balance < amount) {
                throw IllegalArgumentException("Insufficient funds in source wallet")
            }

            walletTransferDao.insert(
                WalletTransferEntity(
                    fromWalletId = fromWalletId,
                    toWalletId = toWalletId,
                    amount = amount,
                    note = note,
                    timestamp = System.currentTimeMillis()
                )
            )

            val newFromBalance = recomputeWalletBalance(fromWalletId)
            val newToBalance = recomputeWalletBalance(toWalletId)

            walletDao.update(fromWallet.copy(balance = newFromBalance))
            walletDao.update(toWallet.copy(balance = newToBalance))
        }
        if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }
}
