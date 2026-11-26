package dev.localremote.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "ir_remote_control_mappings",
    primaryKeys = ["profileId", "role"],
    foreignKeys = [
        ForeignKey(
            entity = IrRemoteButtonEntity::class,
            parentColumns = ["profileId", "buttonId"],
            childColumns = ["profileId", "buttonId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["profileId", "buttonId"])]
)
data class IrRemoteControlMappingEntity(
    val profileId: String,
    val role: String,
    val buttonId: String
)
