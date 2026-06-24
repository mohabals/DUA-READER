package com.example.data

import com.example.model.*
import kotlinx.coroutines.flow.Flow

class SentenceRepository(private val sentenceDao: SentenceDao) {
    val allGroups: Flow<List<StoryGroup>> = sentenceDao.getAllGroups()
    val allStories: Flow<List<Story>> = sentenceDao.getAllStories()
    val allSentences: Flow<List<SentenceCard>> = sentenceDao.getAllSentences()
    val savedSentences: Flow<List<SentenceCard>> = sentenceDao.getSavedSentences()

    // --- Progress Tracking ---
    val allActivityLogs: Flow<List<ActivityLog>> = sentenceDao.getAllActivityLogs()
    val userProgress: Flow<UserProgress?> = sentenceDao.getUserProgressFlow()
    val allAchievements: Flow<List<Achievement>> = sentenceDao.getAllAchievements()

    fun getMonthlyChallenge(monthStr: String): Flow<MonthlyChallenge?> {
        return sentenceDao.getMonthlyChallenge(monthStr)
    }

    suspend fun getMonthlyChallengeNow(monthStr: String): MonthlyChallenge? {
        return sentenceDao.getMonthlyChallengeNow(monthStr)
    }

    suspend fun insertMonthlyChallenge(challenge: MonthlyChallenge) {
        sentenceDao.insertMonthlyChallenge(challenge)
    }

    suspend fun getActivityLog(date: String): ActivityLog? {
        return sentenceDao.getActivityLog(date)
    }

    suspend fun insertActivityLog(log: ActivityLog) {
        sentenceDao.insertActivityLog(log)
    }

    suspend fun getUserProgress(): UserProgress? {
        return sentenceDao.getUserProgress()
    }

    suspend fun insertUserProgress(progress: UserProgress) {
        sentenceDao.insertUserProgress(progress)
    }

    suspend fun insertAchievements(achievements: List<Achievement>) {
        sentenceDao.insertAchievements(achievements)
    }

    suspend fun updateAchievementStatus(id: String, unlocked: Boolean, unlockDate: String?) {
        sentenceDao.updateAchievementStatus(id, unlocked, unlockDate)
    }

    // --- Story Groups ---
    suspend fun insertGroup(group: StoryGroup): Long {
        return sentenceDao.insertGroup(group)
    }

    suspend fun deleteGroup(group: StoryGroup) {
        sentenceDao.deleteGroup(group)
    }

    suspend fun updateGroup(group: StoryGroup) {
        sentenceDao.updateGroup(group)
    }

    // --- Stories ---
    fun getStoriesByGroup(groupId: Int): Flow<List<Story>> {
        return sentenceDao.getStoriesByGroup(groupId)
    }

    suspend fun insertStory(story: Story): Long {
        return sentenceDao.insertStory(story)
    }

    suspend fun deleteStory(story: Story) {
        sentenceDao.deleteStory(story)
    }

    suspend fun updateStory(story: Story) {
        sentenceDao.updateStory(story)
    }

    // --- Sentences ---
    fun getSentencesForStory(storyId: Int): Flow<List<SentenceCard>> {
        return sentenceDao.getSentencesForStory(storyId)
    }

    suspend fun insert(sentence: SentenceCard): Long {
        return sentenceDao.insertSentence(sentence)
    }

    suspend fun insertAll(sentences: List<SentenceCard>) {
        sentenceDao.insertSentences(sentences)
    }

    suspend fun update(sentence: SentenceCard) {
        sentenceDao.updateSentence(sentence)
    }

    suspend fun delete(sentence: SentenceCard) {
        sentenceDao.deleteSentence(sentence)
    }

    suspend fun deleteAll() {
        sentenceDao.deleteAllSentences()
    }

    suspend fun deleteAllGroups() {
        sentenceDao.deleteAllGroups()
    }

    suspend fun deleteAllStories() {
        sentenceDao.deleteAllStories()
    }

    suspend fun setSaved(id: Int, isSaved: Boolean) {
        sentenceDao.updateSavedState(id, isSaved)
    }
}
