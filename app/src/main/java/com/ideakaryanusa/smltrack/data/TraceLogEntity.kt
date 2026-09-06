package com.ideakaryanusa.smltrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trace_logs")
data class TraceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double,
    val heading: Float,
    val speed: Float,
    val timestamp: String,
    val deviceId: String,
    val synced: Boolean = false
)
