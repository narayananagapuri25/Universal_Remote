package dev.localremote.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ir_remote_profiles")
data class IrRemoteProfileEntity(
    @PrimaryKey val profileId: String,
    val category: String,
    val brand: String,
    val model: String?,
    val displayName: String,
    val createdAtEpochMillis: Long
)

@Entity(
    tableName = "ir_remote_buttons",
    primaryKeys = ["profileId", "buttonId"],
    foreignKeys = [
        ForeignKey(
            entity = IrRemoteProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class IrRemoteButtonEntity(
    val profileId: String,
    val buttonId: String,
    val label: String,
    val commandType: String,
    val protocolName: String?,
    val protocolVariant: String?,
    val addressValue: Int?,
    val commandValue: Int?,
    val extendedDataValue: Int?,
    val rawCarrierFrequencyHz: Int?,
    val rawDutyCycle: Float?,
    val rawPatternMicros: String?,
    val repeatWhileHeld: Boolean,
    val verificationStatus: String,
    val sourceReference: String?,
    val sourceDataLicenseSpdx: String?,
    val physicalTestRecordId: String?
)
