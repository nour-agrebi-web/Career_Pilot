package tn.careerpilot.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun current(): UserEntity?

    @Query("DELETE FROM users")
    suspend fun clear()
}

@Dao
abstract class ProfileDao {
    @Query("SELECT * FROM profile LIMIT 1")
    abstract fun observeProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM skills ORDER BY name COLLATE NOCASE")
    abstract fun observeSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM experiences ORDER BY startDate DESC")
    abstract fun observeExperiences(): Flow<List<ExperienceEntity>>

    @Query("SELECT * FROM educations ORDER BY startDate DESC")
    abstract fun observeEducations(): Flow<List<EducationEntity>>

    @Query("SELECT * FROM languages ORDER BY name COLLATE NOCASE")
    abstract fun observeLanguages(): Flow<List<LanguageEntity>>

    @Upsert abstract suspend fun upsertProfile(profile: ProfileEntity)
    @Upsert abstract suspend fun upsertSkill(item: SkillEntity)
    @Upsert abstract suspend fun upsertExperience(item: ExperienceEntity)
    @Upsert abstract suspend fun upsertEducation(item: EducationEntity)
    @Upsert abstract suspend fun upsertLanguage(item: LanguageEntity)
    @Upsert abstract suspend fun upsertSkills(items: List<SkillEntity>)
    @Upsert abstract suspend fun upsertExperiences(items: List<ExperienceEntity>)
    @Upsert abstract suspend fun upsertEducations(items: List<EducationEntity>)
    @Upsert abstract suspend fun upsertLanguages(items: List<LanguageEntity>)

    @Query("DELETE FROM skills WHERE id = :id") abstract suspend fun deleteSkill(id: String)
    @Query("DELETE FROM experiences WHERE id = :id") abstract suspend fun deleteExperience(id: String)
    @Query("DELETE FROM educations WHERE id = :id") abstract suspend fun deleteEducation(id: String)
    @Query("DELETE FROM languages WHERE id = :id") abstract suspend fun deleteLanguage(id: String)

    @Query("DELETE FROM profile") abstract suspend fun clearProfile()
    @Query("DELETE FROM skills") abstract suspend fun clearSkills()
    @Query("DELETE FROM experiences") abstract suspend fun clearExperiences()
    @Query("DELETE FROM educations") abstract suspend fun clearEducations()
    @Query("DELETE FROM languages") abstract suspend fun clearLanguages()

    /** Remplace tout le profil en cache par la version du serveur, en une seule transaction. */
    @Transaction
    open suspend fun replaceAll(
        profile: ProfileEntity,
        skills: List<SkillEntity>,
        experiences: List<ExperienceEntity>,
        educations: List<EducationEntity>,
        languages: List<LanguageEntity>,
    ) {
        clearAll()
        upsertProfile(profile)
        upsertSkills(skills)
        upsertExperiences(experiences)
        upsertEducations(educations)
        upsertLanguages(languages)
    }

    @Transaction
    open suspend fun clearAll() {
        clearProfile()
        clearSkills()
        clearExperiences()
        clearEducations()
        clearLanguages()
    }
}

@Dao
interface PreferencesDao {
    @Query("SELECT * FROM preferences WHERE id = 0")
    fun observe(): Flow<PreferencesEntity?>

    @Upsert
    suspend fun upsert(preferences: PreferencesEntity)

    @Query("DELETE FROM preferences")
    suspend fun clear()
}

@Dao
abstract class CvDao {
    @Query("SELECT * FROM cvs ORDER BY version DESC")
    abstract fun observeAll(): Flow<List<CvEntity>>

    @Upsert abstract suspend fun upsertAll(cvs: List<CvEntity>)

    @Query("DELETE FROM cvs") abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(cvs: List<CvEntity>) {
        clear()
        upsertAll(cvs)
    }
}

@Dao
abstract class MatchDao {
    @Query("SELECT * FROM matches ORDER BY score DESC")
    abstract fun observeAll(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE id = :id")
    abstract fun observe(id: String): Flow<MatchEntity?>

    @Upsert abstract suspend fun upsert(match: MatchEntity)
    @Upsert abstract suspend fun upsertAll(matches: List<MatchEntity>)

    @Query("UPDATE matches SET status = :status WHERE id = :id")
    abstract suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM matches") abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(matches: List<MatchEntity>) {
        clear()
        upsertAll(matches)
    }
}
