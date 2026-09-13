package com.intent.screentime.data.local

import androidx.room.TypeConverter
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.TargetType

/**
 * Explicit converters rather than relying on Room's implicit enum handling, so the
 * stored representation is stable if enum members are ever reordered.
 */
class Converters {
    @TypeConverter
    fun fromCategoryKind(value: CategoryKind): String = value.name

    @TypeConverter
    fun toCategoryKind(value: String): CategoryKind = CategoryKind.valueOf(value)

    @TypeConverter
    fun fromTargetType(value: TargetType): String = value.name

    @TypeConverter
    fun toTargetType(value: String): TargetType = TargetType.valueOf(value)
}
