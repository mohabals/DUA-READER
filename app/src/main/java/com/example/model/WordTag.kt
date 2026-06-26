package com.example.model

import java.io.Serializable

data class WordTag(
    val word: String,
    val pos: String, // noun, verb, adjective, adverb, other
    val start: Int,
    val end: Int
) : Serializable
