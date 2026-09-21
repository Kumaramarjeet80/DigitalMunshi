package com.digitalmunshi.pos

import android.app.Application
import com.digitalmunshi.pos.core.security.EncryptionManager
import com.digitalmunshi.pos.core.security.SessionManager
import com.digitalmunshi.pos.data.local.database.DigitalMunshiDatabase
import com.digitalmunshi.pos.data.local.entities.ProductEntity
import com.digitalmunshi.pos.data.local.entities.UserEntity
import com.digitalmunshi.pos.domain.models.UnitType
import com.digitalmunshi.pos.domain.models.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DigitalMunshiApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: DigitalMunshiDatabase
        private set

    override fun onCreate() {
        super.onCreate()

        // Derive or fetch hardware-backed SQLCipher passphrase
        val passphrase = EncryptionManager.getOrCreateDatabasePassphrase(this)

        // Initialize encrypted Room database
        database = DigitalMunshiDatabase.getInstance(this, passphrase)

        // Seed initial default accounts and sample catalog if empty
        applicationScope.launch(Dispatchers.IO) {
            seedDefaultAccountsAndCatalog()
        }
    }

    private suspend fun seedDefaultAccountsAndCatalog() {
        val userDao = database.userDao()
        if (userDao.getUserCount() == 0) {
            // Seed Admin User (Default PIN: 1234)
            val adminSalt = EncryptionManager.generateRandomSalt()
            val adminHash = EncryptionManager.hashPin("1234", adminSalt)
            val admin = UserEntity(
                username = "admin",
                displayName = "Store Owner (Admin)",
                pinHash = adminHash,
                salt = EncryptionManager.run { adminSalt.toHexString() },
                role = UserRole.ADMIN
            )
            userDao.insertUser(admin)

            // Seed Cashier User (Default PIN: 0000)
            val cashierSalt = EncryptionManager.generateRandomSalt()
            val cashierHash = EncryptionManager.hashPin("0000", cashierSalt)
            val cashier = UserEntity(
                username = "cashier1",
                displayName = "Billing Cashier",
                pinHash = cashierHash,
                salt = EncryptionManager.run { cashierSalt.toHexString() },
                role = UserRole.CASHIER
            )
            userDao.insertUser(cashier)

            // Auto-login cashier by default
            SessionManager.login(cashier)
        }

        // Seed initial products if catalog is empty
        val productDao = database.productDao()
        val products = listOf(
            ProductEntity(
                barcode = "8901030383344",
                sku = "RICE-BAS-01",
                name = "Royal Basmati Rice (Loose)",
                category = "Grains",
                unitType = UnitType.KG,
                costPrice = 85.0,
                retailPrice = 110.0,
                wholesalePrice = 95.0,
                wholesaleMinQty = 5.0, // Shifts to wholesale price if >= 5.0 kg!
                taxSlab = 0.0,
                stockQty = 150.450,
                minStockWarning = 20.0
            ),
            ProductEntity(
                barcode = "8901262010051",
                sku = "OIL-MUST-01",
                name = "Cold Pressed Mustard Oil",
                category = "Oils",
                unitType = UnitType.LITER,
                costPrice = 130.0,
                retailPrice = 165.0,
                wholesalePrice = 145.0,
                wholesaleMinQty = 10.0,
                taxSlab = 5.0,
                stockQty = 85.0,
                minStockWarning = 10.0
            ),
            ProductEntity(
                barcode = "8901058852396",
                sku = "MAGGI-70G",
                name = "Maggi 2-Minute Noodles 70g",
                category = "Packaged Food",
                unitType = UnitType.PIECE,
                costPrice = 11.5,
                retailPrice = 14.0,
                wholesalePrice = 12.5,
                wholesaleMinQty = 24.0, // Shifts to wholesale if buying carton of 24!
                taxSlab = 12.0,
                stockQty = 320.0,
                minStockWarning = 50.0
            ),
            ProductEntity(
                barcode = "8901491101834",
                sku = "DAL-TUR-01",
                name = "Unpolished Toor Dal Premium",
                category = "Pulses",
                unitType = UnitType.KG,
                costPrice = 135.0,
                retailPrice = 175.0,
                wholesalePrice = 155.0,
                wholesaleMinQty = 5.0,
                taxSlab = 0.0,
                stockQty = 95.750,
                minStockWarning = 15.0
            )
        )

        for (p in products) {
            val existing = productDao.getProductByBarcode(p.barcode)
            if (existing == null) {
                val pId = productDao.insertProduct(p)
                // Seed sample batch expiring in 15 days for radar demonstration
                val now = System.currentTimeMillis()
                database.batchDao().insertBatch(
                    com.digitalmunshi.pos.data.local.entities.BatchEntity(
                        productId = pId,
                        batchNo = "B24A",
                        mfgDate = now - (60L * 86400000L),
                        expiryDate = now + (15L * 86400000L), // Expiring in 15 days!
                        costPrice = p.costPrice,
                        stockQty = p.stockQty
                    )
                )
            }
        }
    }
}
