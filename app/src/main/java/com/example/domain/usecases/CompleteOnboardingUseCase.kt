package com.example.domain.usecases

import com.example.data.repository.AuthRepository

class CompleteOnboardingUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(nativeLanguage: String, targetLanguage: String): Boolean {
        return authRepository.completeOnboarding(nativeLanguage, targetLanguage)
    }
}
