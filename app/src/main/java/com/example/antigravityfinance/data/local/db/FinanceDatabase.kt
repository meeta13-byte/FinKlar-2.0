package com.example.antigravityfinance.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.antigravityfinance.service.security.SecurityHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        TransactionEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class,
        InvestmentEntity::class,
        RecurringMerchantEntity::class,
        SplitEntity::class,
        WalletEntity::class,
        WalletTransferEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun investmentDao(): InvestmentDao
    abstract fun recurringMerchantDao(): RecurringMerchantDao
    abstract fun splitDao(): SplitDao
    abstract fun walletDao(): WalletDao
    abstract fun walletTransferDao(): WalletTransferDao

    companion object {
        @Volatile
        private var INSTANCE: FinanceDatabase? = null

        init {
            // Load native SQLCipher binary components
            System.loadLibrary("sqlcipher")
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create wallets table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `wallets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `balance` REAL NOT NULL,
                        `purpose` TEXT,
                        `isDefault` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `investedAmount` REAL NOT NULL,
                        `initialAmount` REAL NOT NULL
                    )
                """.trimIndent())

                // Create wallet_transfers table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `wallet_transfers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fromWalletId` INTEGER NOT NULL,
                        `toWalletId` INTEGER NOT NULL,
                        `amount` REAL NOT NULL,
                        `note` TEXT,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())

                // Add walletId column to transactions
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `walletId` INTEGER DEFAULT NULL")

                // Create index on walletId in transactions
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_walletId` ON `transactions` (`walletId`)")

                // Seed the Default Spending Wallet
                val now = System.currentTimeMillis()
                db.execSQL("INSERT INTO `wallets` (`id`, `name`, `balance`, `purpose`, `isDefault`, `createdAt`, `investedAmount`, `initialAmount`) VALUES (1, 'Default Spending Wallet', 0.0, 'Default spending wallet', 1, $now, 0.0, 11500.0)")
            }
        }

        fun getDatabase(context: Context): FinanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val securityHelper = SecurityHelper(context.applicationContext)
                val passphrase = securityHelper.getDatabasePassphrase()
                
                val factory = SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8))

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FinanceDatabase::class.java,
                    "finance_secure_db"
                )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_3_4)
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
