package tn.careerpilot.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Routes métier de l'API (profil, préférences, CV, correspondances). Les routes d'authentification
 * restent dans [tn.careerpilot.data.ApiService], utilisé uniquement par l'AuthRepository.
 *
 * Chaque appel renvoie un [Response] : les repositories vérifient `isSuccessful` (erreurs 4xx/5xx)
 * via [safeApiCall], qui capture aussi les erreurs réseau.
 */
interface CareerPilotApi {

    // --- Profil (GET / PUT / DELETE) et ses sections (POST / PUT / DELETE) ---
    @GET("profile")
    suspend fun getProfile(): Response<ProfileDto>

    @PUT("profile")
    suspend fun updateProfile(@Body body: ProfileUpdateDto): Response<ProfileDto>

    @POST("profile/validate")
    suspend fun validateProfile(): Response<ProfileDto>

    @DELETE("profile")
    suspend fun resetProfile(): Response<Unit>

    @POST("profile/skills")
    suspend fun addSkill(@Body body: SkillDto): Response<SkillDto>

    @PUT("profile/skills/{id}")
    suspend fun updateSkill(@Path("id") id: String, @Body body: SkillDto): Response<SkillDto>

    @POST("profile/experiences")
    suspend fun addExperience(@Body body: ExperienceDto): Response<ExperienceDto>

    @PUT("profile/experiences/{id}")
    suspend fun updateExperience(@Path("id") id: String, @Body body: ExperienceDto): Response<ExperienceDto>

    @POST("profile/educations")
    suspend fun addEducation(@Body body: EducationDto): Response<EducationDto>

    @PUT("profile/educations/{id}")
    suspend fun updateEducation(@Path("id") id: String, @Body body: EducationDto): Response<EducationDto>

    @POST("profile/languages")
    suspend fun addLanguage(@Body body: LanguageDto): Response<LanguageDto>

    @PUT("profile/languages/{id}")
    suspend fun updateLanguage(@Path("id") id: String, @Body body: LanguageDto): Response<LanguageDto>

    /** [section] : skills, experiences, educations ou languages. */
    @DELETE("profile/{section}/{id}")
    suspend fun deleteProfileItem(@Path("section") section: String, @Path("id") id: String): Response<Unit>

    // --- Préférences ---
    @GET("preferences")
    suspend fun getPreferences(): Response<PreferencesDto>

    @PUT("preferences")
    suspend fun updatePreferences(@Body body: PreferencesDto): Response<PreferencesDto>

    @DELETE("preferences")
    suspend fun resetPreferences(): Response<Unit>

    // --- CV ---
    @GET("cvs")
    suspend fun getCvs(): Response<List<CvDto>>

    @Multipart
    @POST("cvs")
    suspend fun uploadCv(@Part file: MultipartBody.Part): Response<CvDto>

    @GET("cvs/{id}/analysis")
    suspend fun getCvAnalysis(@Path("id") id: String): Response<CvAnalysisDto>

    @POST("cvs/{id}/analyze")
    suspend fun analyzeCv(@Path("id") id: String): Response<CvDto>

    @POST("cvs/{id}/apply-profile")
    suspend fun applyCvProfile(@Path("id") id: String): Response<ApplyProfileResultDto>

    @GET("cvs/quota")
    suspend fun getAiQuota(): Response<AiQuotaDto>

    @PUT("cvs/{id}/activate")
    suspend fun activateCv(@Path("id") id: String): Response<CvDto>

    @DELETE("cvs/{id}")
    suspend fun deleteCv(@Path("id") id: String): Response<Unit>

    // --- Correspondances (offres recommandées) ---
    @POST("matches/refresh")
    suspend fun refreshMatches(): Response<RefreshResultDto>

    /** min_score = 0 : on garde tout en cache, le seuil est appliqué dans l'application. */
    @GET("matches")
    suspend fun getMatches(@Query("min_score") minScore: Int = 0): Response<List<MatchDto>>

    @GET("matches/{id}")
    suspend fun getMatch(@Path("id") id: String): Response<MatchDto>

    @PUT("matches/{id}")
    suspend fun updateMatchStatus(@Path("id") id: String, @Body body: MatchStatusDto): Response<MatchDto>

    @DELETE("matches/{id}")
    suspend fun hideMatch(@Path("id") id: String): Response<Unit>
}
