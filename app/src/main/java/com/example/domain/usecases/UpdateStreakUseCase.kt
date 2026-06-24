package com.example.domain.usecases

import com.example.data.model.UserModel
import com.example.data.repository.AuthRepository

class UpdateStreakUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(streakVal: Int): UserModel? {
        if (streakVal < 0) return null
        return authRepository.incrementStreak(streakVal)
    }
}
