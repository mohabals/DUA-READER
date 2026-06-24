package com.example.domain.usecases

import com.example.data.model.UserModel
import com.example.data.repository.AuthRepository

class AddXPUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(xpGained: Int): UserModel? {
        if (xpGained <= 0) return null
        return authRepository.addXP(xpGained)
    }
}
