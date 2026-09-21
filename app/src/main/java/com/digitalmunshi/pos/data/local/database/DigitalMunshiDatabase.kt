package com.digitalmunshi.pos.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.digitalmunshi.pos.data.local.dao.*
import com.digitalmunshi.pos.data.local.entities.*
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File

@Database(
    entities = [
        ProductEntity::class,
        BatchEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        KhataCustomerEntity::class,
        KhataEntryEntity::class,
        SupplierEntity::class,
        DayCloseReportEntity::class,
        AuditLogEntity::class,
        UserEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class DigitalMunshiDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun batchDao(): BatchDao
    abstract fun transactionDao(): TransactionDao
    abstract fun khataDao(): KhataDao
    abstract fun supplierDao(): SupplierDao
    abstract fun dayCloseDao(): DayCloseDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun userDao(): UserDao

    companion object {
        const val DATABASE_NAME = "digital_munshi_encrypted.db"

        @Volatile
        private var INSTANCE: DigitalMunshiDatabase? = null

        fun getInstance(context: Context, passphraseBytes: ByteArray): DigitalMunshiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext, passphraseBytes).also {
                    INSTANCE = it
                }
            }
        }

        private fun buildDatabase(context: Context, passphraseBytes: ByteArray): DigitalMunshiDatabase {
            // Initialize SQLCipher native binary libraries
            SQLiteDatabase.loadLibs(context)

            val supportFactory = SupportFactory(passphraseBytes)

            return Room.databaseBuilder(
                context,
                DigitalMunshiDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(supportFactory)
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Enforce SQLite Foreign Key constraints
                        db.execSQL("PRAGMA foreign_keys = ON;")
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON;")
                    }
                })
                .build()
        }

        fun getDatabaseFile(context: Context): File {
            return context.getDatabasePath(DATABASE_NAME)
        }

        /**
         * Closes the database instance safely before backup / restore swap operations.
         */
        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
