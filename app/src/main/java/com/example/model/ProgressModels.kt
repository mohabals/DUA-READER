package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val sentencesRead: Int = 0,
    val cardsReviewed: Int = 0,
    val minutesSpent: Int = 0
) : Serializable

@Entity(tableName = "user_progress")
data class UserProgress(
    @PrimaryKey val id: Int = 1, // Single active progress row
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastReadDate: String = "", // Format: YYYY-MM-DD
    val shieldsCount: Int = 0,
    val levelTitle: String = "Reader",
    val levelXP: Int = 0,
    val currentGoalSentences: Int = 10,
    val difficulty: String = "Intermediate", // Beginner, Intermediate, Advanced
    val userName: String = "",
    val nativeLanguage: String = "",
    val targetLanguage: String = ""
) : Serializable

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String, // "streak", "reading", "reviewing", "stories"
    val requirement: Int,
    val unlocked: Boolean = false,
    val unlockDate: String? = null
) : Serializable

@Entity(tableName = "monthly_challenges")
data class MonthlyChallenge(
    @PrimaryKey val monthStr: String, // Format: YYYY-MM (e.g., "2026-06")
    val targetSentences: Int = 300,
    val completed: Boolean = false
) : Serializable
