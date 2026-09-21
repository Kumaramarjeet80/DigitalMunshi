package com.digitalmunshi.pos.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.digitalmunshi.pos.domain.models.UnitType

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["sku"], unique = true),
        Index(value = ["name"]),
        Index(value = ["category"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "barcode")
    val barcode: String,

    @ColumnInfo(name = "sku")
    val sku: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "unit_type")
    val unitType: UnitType,

    @ColumnInfo(name = "cost_price")
    val costPrice: Double,

    @ColumnInfo(name = "retail_price")
    val retailPrice: Double,

    @ColumnInfo(name = "wholesale_price")
    val wholesalePrice: Double,

    @ColumnInfo(name = "wholesale_min_qty")
    val wholesaleMinQty: Double,

    @ColumnInfo(name = "tax_slab")
    val taxSlab: Double, // e.g. 5.0, 12.0, 18.0 percent

    @ColumnInfo(name = "stock_qty")
    val stockQty: Double, // Double for fractional weight e.g. 15.450 kg

    @ColumnInfo(name = "min_stock_warning")
    val minStockWarning: Double = 5.0,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
