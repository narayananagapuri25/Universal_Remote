package dev.localremote.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SavedDeviceEntity::class,
        IrRemoteProfileEntity::class,
        IrRemoteButtonEntity::class,
        DaikinAcProfileEntity::class,
        IrRemoteControlMappingEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class OneRemoteDatabase : RoomDatabase() {
    abstract fun savedDeviceDao(): SavedDeviceDao
    abstract fun irRemoteProfileDao(): IrRemoteProfileDao
    abstract fun daikinAcProfileDao(): DaikinAcProfileDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ir_remote_control_mappings` (
                        `profileId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `buttonId` TEXT NOT NULL,
                        PRIMARY KEY(`profileId`, `role`),
                        FOREIGN KEY(`profileId`) REFERENCES `ir_remote_profiles`(`profileId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ir_remote_control_mappings_profileId` ON `ir_remote_control_mappings` (`profileId`)"
                )
            }
        }

        /**
         * Preserve version-1 saved devices when adding the local IR profile library.
         * The foreign key guarantees deleting a profile also removes its buttons.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daikin_ac_profiles` (
                        `profileId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `power` INTEGER NOT NULL,
                        `mode` TEXT NOT NULL,
                        `temperatureCelsius` REAL NOT NULL,
                        `fanSpeed` TEXT NOT NULL,
                        `verticalSwing` INTEGER NOT NULL,
                        `horizontalSwing` INTEGER NOT NULL,
                        `intelligentEye` INTEGER NOT NULL,
                        `econo` INTEGER NOT NULL,
                        `outdoorQuiet` INTEGER NOT NULL,
                        `powerful` INTEGER NOT NULL,
                        `weeklyScheduleActive` INTEGER NOT NULL,
                        `transportVariant` TEXT NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`)
                    )
                    """.trimIndent()
                )
            }
        }

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
