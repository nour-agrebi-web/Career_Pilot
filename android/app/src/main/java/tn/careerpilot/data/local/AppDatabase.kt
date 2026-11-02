package tn.careerpilot.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        UserEntity::class, ProfileEntity::class, SkillEntity::class, ExperienceEntity::class,
        EducationEntity::class, LanguageEntity::class, PreferencesEntity::class, CvEntity::class, MatchEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun profileDao(): ProfileDao
    abstract fun preferencesDao(): PreferencesDao
    abstract fun cvDao(): CvDao
    abstract fun matchDao(): MatchDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "careerpilot.db")
                .fallbackToDestructiveMigration(dropAllTables = true) // simple cache : reconstruit depuis l'API
                .build()
    }
}
