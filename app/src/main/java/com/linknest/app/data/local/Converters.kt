package com.linknest.app.data.local

import androidx.room.TypeConverter
import com.linknest.app.domain.ResourceType

class RoomConverters {
    @TypeConverter
    fun resourceTypeToString(type: ResourceType): String = type.name

    @TypeConverter
    fun stringToResourceType(value: String): ResourceType =
        runCatching { ResourceType.valueOf(value) }.getOrDefault(ResourceType.OTHER)
}
