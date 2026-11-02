package tn.careerpilot.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import tn.careerpilot.domain.Location

/** Room ne stocke pas les listes : elles sont enregistrées en JSON. */
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        gson.fromJson(value, object : TypeToken<List<String>>() {}.type) ?: emptyList()

    @TypeConverter
    fun fromLocations(value: List<Location>): String = gson.toJson(value)

    @TypeConverter
    fun toLocations(value: String): List<Location> =
        gson.fromJson(value, object : TypeToken<List<Location>>() {}.type) ?: emptyList()
}
