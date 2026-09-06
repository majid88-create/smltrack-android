package com.ideakaryanusa.smltrack.model

import com.google.gson.annotations.SerializedName

data class ScheduleItem(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("start_date") val startDate: String?,
    @SerializedName("end_date") val endDate: String?,
    @SerializedName("start_time") val startTime: String?,
    @SerializedName("end_time") val endTime: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("repeat") val repeat: String?
)

data class ScheduleListResponse(
    @SerializedName("data") val data: List<ScheduleItem>?
)
