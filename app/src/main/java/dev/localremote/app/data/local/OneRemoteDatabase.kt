package dev.localremote.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SavedDeviceEntity::class,
        IrRemoteProfileEntity::class,
        IrRemoteButtonEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class OneRemoteDatabase : RoomDatabase() {
    abstract fun savedDeviceDao(): SavedDeviceDao
    abstract fun irRemoteProfileDao(): IrRemoteProfileDao

    companion object {
        /**
         * Preserve version-1 saved devices when adding the local IR profile library.
         * The foreign key guarantees deleting a profile also removes its buttons.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ir_remote_profiles` (
                        `profileId` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `brand` TEXT NOT NULL,
                        `model` TEXT,
                        `displayName` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ir_remote_buttons` (
                        `profileId` TEXT NOT NULL,
                        `buttonId` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `commandType` TEXT NOT NULL,
                        `protocolName` TEXT,
                        `protocolVariant` TEXT,
                        `addressValue` INTEGER,
                        `commandValue` INTEGER,
                        `extendedDataValue` INTEGER,
                        `rawCarrierFrequencyHz` INTEGER,
                        `rawDutyCycle` REAL,
                        `rawPatternMicros` TEXT,
                        `repeatWhileHeld` INTEGER NOT NULL,
                        `verificationStatus` TEXT NOT NULL,
                        `sourceReference` TEXT,
                        `sourceDataLicenseSpdx` TEXT,
                        `physicalTestRecordId` TEXT,
                        PRIMARY KEY(`profileId`, `buttonId`),
                        FOREIGN KEY(`profileId`) REFERENCES `ir_remote_profiles`(`profileId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ir_remote_buttons_profileId` ON `ir_remote_buttons` (`profileId`)"
                )
            }
        }
    }
}
