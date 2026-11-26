package dev.localremote.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "ir_remote_control_mappings",
    primaryKeys = ["profileId", "role"],
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
data class IrRemoteControlMappingEntity(
    val profileId: String,
    val role: String,
    val buttonId: String
)
