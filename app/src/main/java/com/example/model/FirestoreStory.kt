package com.example.model

import java.io.Serializable

data class FirestoreStory(
    val storyId: String = "",
    val title: String = "",
    val description: String = "",
    val content: String = "",
    val language: String = "",
    val category: String = "",
    val difficulty: String = "",
    val tags: List<String> = emptyList(),
    val coverImageUrl: String = "",
    val readingTimeMinutes: Int = 5,
    val wordCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val published: Boolean = true,
    val featured: Boolean = false,
    val trendingScore: Double = 0.0,
    val readCount: Long = 0L
) : Serializable
