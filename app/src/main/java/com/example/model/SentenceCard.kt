package com.example.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "story_groups")
data class StoryGroup(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
) : Serializable

@Entity(
    tableName = "stories",
    foreignKeys = [
        ForeignKey(
            entity = StoryGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class Story(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val groupId: Int,
    val title: String,
    val lastReadIndex: Int = 0
) : Serializable

@Entity(
    tableName = "sentences",
    foreignKeys = [
        ForeignKey(
            entity = Story::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["storyId"])]
)
data class SentenceCard(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val storyId: Int,
    val originalText: String,
    val translatedText: String,
    val isSaved: Boolean = false
) : Serializable
