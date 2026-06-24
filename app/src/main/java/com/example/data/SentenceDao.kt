package com.example.data

import androidx.room.*
import com.example.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SentenceDao {
    // --- Group queries ---
    @Query("SELECT * FROM story_groups ORDER BY id ASC")
    fun getAllGroups(): Flow<List<StoryGroup>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: StoryGroup): Long

    @Delete
    suspend fun deleteGroup(group: StoryGroup)

    @Update
    suspend fun updateGroup(group: StoryGroup)

    // --- Story queries ---
    @Query("SELECT * FROM stories WHERE groupId = :groupId ORDER BY id ASC")
    fun getStoriesByGroup(groupId: Int): Flow<List<Story>>

    @Query("SELECT * FROM stories ORDER BY id ASC")
    fun getAllStories(): Flow<List<Story>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: Story): Long

    @Delete
    suspend fun deleteStory(story: Story)

    @Update
    suspend fun updateStory(story: Story)

    // --- Sentence queries ---
    @Query("SELECT * FROM sentences WHERE storyId = :storyId ORDER BY id ASC")
    fun getSentencesForStory(storyId: Int): Flow<List<SentenceCard>>

    @Query("SELECT * FROM sentences ORDER BY id ASC")
    fun getAllSentences(): Flow<List<SentenceCard>>

    @Query("SELECT * FROM sentences WHERE isSaved = 1 ORDER BY id ASC")
    fun getSavedSentences(): Flow<List<SentenceCard>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentence(sentence: SentenceCard): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentences(sentences: List<SentenceCard>)

    @Update
    suspend fun updateSentence(sentence: SentenceCard)

    @Delete
    suspend fun deleteSentence(sentence: SentenceCard)

    @Query("DELETE FROM sentences")
    suspend fun deleteAllSentences()

    @Query("DELETE FROM story_groups")
    suspend fun deleteAllGroups()

    @Query("DELETE FROM stories")
    suspend fun deleteAllStories()

    @Query("UPDATE sentences SET isSaved = :isSaved WHERE id = :id")
    suspend fun updateSavedState(id: Int, isSaved: Boolean)

    // --- Activity Log queries ---
    @Query("SELECT * FROM activity_logs ORDER BY date DESC")
    fun getAllActivityLogs(): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs WHERE date = :date LIMIT 1")
    suspend fun getActivityLog(date: String): ActivityLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLog)

    // --- User Progress queries ---
    @Query("SELECT * FROM user_progress WHERE id = 1 LIMIT 1")
    fun getUserProgressFlow(): Flow<UserProgress?>

    @Query("SELECT * FROM user_progress WHERE id = 1 LIMIT 1")
    suspend fun getUserProgress(): UserProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProgress(progress: UserProgress)

    // --- Achievement queries ---
    @Query("SELECT * FROM achievements ORDER BY id ASC")
    fun getAllAchievements(): Flow<List<Achievement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<Achievement>)

    @Query("UPDATE achievements SET unlocked = :unlocked, unlockDate = :unlockDate WHERE id = :id")
    suspend fun updateAchievementStatus(id: String, unlocked: Boolean, unlockDate: String?)

    // --- Monthly Challenge queries ---
    @Query("SELECT * FROM monthly_challenges WHERE monthStr = :monthStr LIMIT 1")
    fun getMonthlyChallenge(monthStr: String): Flow<MonthlyChallenge?>

    @Query("SELECT * FROM monthly_challenges WHERE monthStr = :monthStr LIMIT 1")
    suspend fun getMonthlyChallengeNow(monthStr: String): MonthlyChallenge?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonthlyChallenge(challenge: MonthlyChallenge)
}
