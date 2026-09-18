package com.example.petcare.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.petcare.data.local.care.CareTaskDao
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.pet.PetDao
import com.example.petcare.data.local.pet.PetEntity

@Database(entities = [PetEntity::class, CareTaskEntity::class], version = 5, exportSchema = true)
abstract class PetCareDatabase : RoomDatabase() {

    abstract fun petDao(): PetDao
    abstract fun careTaskDao(): CareTaskDao

    companion object {
        @Volatile
        private var instance: PetCareDatabase? = null

        fun getInstance(context: Context): PetCareDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PetCareDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
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
    }
}
