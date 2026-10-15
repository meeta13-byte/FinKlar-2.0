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
        WalletTransferEntity::class,
        BankAccountEntity::class
    ],
    version = 5,
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
    abstract fun bankAccountDao(): BankAccountDao

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

        /**
         * Multi-bank support. Existing rows keep a NULL linkedBankAccountId, which means
         * "all bank accounts" — the behaviour from version 4 — so nothing has to be
         * backfilled.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bank_accounts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bankName` TEXT NOT NULL,
                        `last4` TEXT NOT NULL,
                        `lastKnownBalance` REAL,
                        `lastSeenAt` INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bank_accounts_bankName_last4` ON `bank_accounts` (`bankName`, `last4`)")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `bankAccountId` INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_bankAccountId` ON `transactions` (`bankAccountId`)")
                db.execSQL("ALTER TABLE `wallets` ADD COLUMN `linkedBankAccountId` INTEGER DEFAULT NULL")

                // Recover the accounts that were already recorded: the SMS rows already
                // store the bank name in `account` and the digits inside `notes`.
                val cursor = db.query("SELECT account, notes FROM transactions WHERE detectedFromSms = 1")
                val seen = mutableSetOf<String>()
                cursor.use {
                    val accountIdx = it.getColumnIndex("account")
                    val notesIdx = it.getColumnIndex("notes")
                    while (it.moveToNext()) {
                        val bankName = it.getString(accountIdx)?.takeIf { n -> n.isNotBlank() } ?: continue
                        val notes = it.getString(notesIdx).orEmpty()
                        val digits = Regex("(?:a/c|a\\.c|account|card)[^\\n]{0,24}?x{2,}(\\d{4})", RegexOption.IGNORE_CASE)
                            .find(notes)?.groupValues?.get(1)
                        if (digits == null) continue
                        val key = "$bankName|$digits"
                        if (seen.add(key)) {
                            db.execSQL(
                                "INSERT INTO `bank_accounts` (`bankName`, `last4`, `lastKnownBalance`, `lastSeenAt`) " +
                                    "VALUES ('${bankName.replace("'", "''")}', '$digits', NULL, ${System.currentTimeMillis()})"
                            )
                        }
                    }
                }

                // Attach the recovered transactions to their account. The bank name must
                // match too: two banks can both have an account ending 1234.
                val linkCursor = db.query("SELECT id, account, notes FROM transactions WHERE bankAccountId IS NULL AND detectedFromSms = 1")
                linkCursor.use {
                    val idIdx = it.getColumnIndex("id")
                    val accountIdx = it.getColumnIndex("account")
                    val notesIdx = it.getColumnIndex("notes")
                    while (it.moveToNext()) {
                        val txId = it.getLong(idIdx)
                        val bankName = it.getString(accountIdx)?.trim().orEmpty()
                        val notes = it.getString(notesIdx).orEmpty()
                        val digits = Regex("(?:a/c|a\\.c|account|card)[^\\n]{0,24}?x{2,}(\\d{4})", RegexOption.IGNORE_CASE)
                            .find(notes)?.groupValues?.get(1) ?: continue
                        db.execSQL(
                            "UPDATE `transactions` SET `bankAccountId` = " +
                                "(SELECT `id` FROM `bank_accounts` WHERE `last4` = '$digits' AND `bankName` = " +
                                "'${bankName.replace("'", "''")}' LIMIT 1) WHERE `id` = $txId"
                        )
                    }
                }
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
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
