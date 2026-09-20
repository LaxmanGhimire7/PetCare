package com.example.petcare.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.petcare.data.local.care.CareTaskDao
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.expense.ExpenseDao
import com.example.petcare.data.local.expense.ExpenseEntity
import com.example.petcare.data.local.pet.PetDao
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.provider.ProviderDao
import com.example.petcare.data.local.provider.ProviderEntity

@Database(
    entities = [PetEntity::class, CareTaskEntity::class, ExpenseEntity::class, ProviderEntity::class],
    version = 11,
    exportSchema = true
)
abstract class PetCareDatabase : RoomDatabase() {

    abstract fun petDao(): PetDao
    abstract fun careTaskDao(): CareTaskDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun providerDao(): ProviderDao

    companion object {
        @Volatile
        private var instance: PetCareDatabase? = null

        fun getInstance(context: Context): PetCareDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PetCareDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11
            ).build().also { instance = it }
        }

        private const val DATABASE_NAME = "petcare.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `care_tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `petId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `dueDateEpochDay` INTEGER NOT NULL,
                        FOREIGN KEY(`petId`) REFERENCES `pets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_care_tasks_petId` ON `care_tasks` (`petId`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `care_tasks` ADD COLUMN `isCompleted` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `care_tasks` ADD COLUMN `reminderMinutesOfDay` INTEGER NOT NULL DEFAULT 540"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pets` ADD COLUMN `healthNotes` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pets` ADD COLUMN `photoUri` TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pets` ADD COLUMN `breed` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pets` ADD COLUMN `age` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pets` ADD COLUMN `weight` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pets` ADD COLUMN `dietaryPreferences` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pets` ADD COLUMN `vaccinationHistory` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pets` ADD COLUMN `allergies` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pets` ADD COLUMN `favoriteToys` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pets` ADD COLUMN `medicalRecords` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pets` ADD COLUMN `groomingRoutine` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pets` ADD COLUMN `photoUris` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `care_tasks` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'General'")
                db.execSQL("ALTER TABLE `care_tasks` ADD COLUMN `frequency` TEXT NOT NULL DEFAULT 'One time'")
                db.execSQL("ALTER TABLE `care_tasks` ADD COLUMN `requiredSupplies` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `care_tasks` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expenses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `petId` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `dateEpochDay` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        FOREIGN KEY(`petId`) REFERENCES `pets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_petId` ON `expenses` (`petId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `providers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `openingHours` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `bookingUrl` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Existing rows receive a stable pet identity colour from their row id. A NOT NULL
         * default keeps older data valid while the UPDATE gives each pet its own tag.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pets ADD COLUMN colorIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE pets SET colorIndex = id % 6")
            }
        }

        /** Nullable coordinates preserve old tasks and retain a location after a place is removed. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE care_tasks ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE care_tasks ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE care_tasks ADD COLUMN placeId INTEGER")
            }
        }

        /** Old row ids provide a stable order; null origin marks pre-existing tasks. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE care_tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE care_tasks ADD COLUMN generatedFromId INTEGER")
                db.execSQL("UPDATE care_tasks SET sortOrder = id")
            }
        }
    }
}
