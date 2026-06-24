package com.example.data.repository

import android.content.Context
import com.example.data.model.UserModel
import com.example.data.remote.firebase.FirebaseManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DelicateCoroutinesApi

interface AuthRepository {
    val currentUserFlow: StateFlow<UserModel?>
    fun isFallbackMode(): Boolean
    suspend fun getSessionUser(): UserModel?
    fun loginGuest(onResult: (Result<UserModel>) -> Unit)
    fun loginWithGoogleSimulated(name: String, email: String, onResult: (Result<UserModel>) -> Unit)
    fun signUpWithEmailAndPassword(firstName: String, lastName: String, email: String, password: String, onResult: (Result<UserModel>) -> Unit)
    fun signInWithEmailAndPassword(email: String, password: String, onResult: (Result<UserModel>) -> Unit)
    suspend fun completeOnboarding(nativeLanguage: String, targetLanguage: String): Boolean
    suspend fun addXP(amount: Int): UserModel?
    suspend fun incrementStreak(newValue: Int): UserModel?
    fun logout()
    fun isEmailVerified(): Boolean
    fun setSimulatedUserVerified(email: String, verified: Boolean)
    fun sendEmailVerification(onResult: (Result<Unit>) -> Unit)
    fun reloadUser(onResult: (Result<Unit>) -> Unit)
    fun sendPasswordResetEmail(email: String, onResult: (Result<Unit>) -> Unit)
    fun generateAndStoreResetCode(email: String, onResult: (Result<String>) -> Unit)
    fun verifyResetCode(email: String, code: String, onResult: (Result<Unit>) -> Unit)
    fun resetPasswordWithCode(email: String, newPassword: String, onResult: (Result<Unit>) -> Unit)
    fun changePassword(currentPassword: String, newPassword: String, onResult: (Result<Unit>) -> Unit)
    fun deleteAccount(onResult: (Result<Unit>) -> Unit)
}

class AuthRepositoryImpl(context: Context) : AuthRepository {
    private val firebaseManager = FirebaseManager.getInstance(context)
    private val _currentUserFlow = MutableStateFlow<UserModel?>(null)
    override val currentUserFlow: StateFlow<UserModel?> = _currentUserFlow.asStateFlow()

    init {
        // Refresh local memory state on launch
        val currentUid = firebaseManager.getCurrentUserUid()
        if (currentUid != null) {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val profile = firebaseManager.getProfile(currentUid)
                _currentUserFlow.value = profile
            }
        }
    }

    override fun isFallbackMode(): Boolean = firebaseManager.isFallbackMode

    override suspend fun getSessionUser(): UserModel? {
        val uid = firebaseManager.getCurrentUserUid() ?: return null
        val profile = firebaseManager.getProfile(uid)
        _currentUserFlow.value = profile
        return profile
    }

    override fun loginGuest(onResult: (Result<UserModel>) -> Unit) {
        firebaseManager.loginGuest { result ->
            if (result.isSuccess) {
                val user = result.getOrNull()
                _currentUserFlow.value = user
            }
            onResult(result)
        }
    }

    override fun loginWithGoogleSimulated(name: String, email: String, onResult: (Result<UserModel>) -> Unit) {
        val first = name.substringBefore(" ")
        val last = if (name.contains(" ")) name.substringAfter(" ") else ""
        firebaseManager.initiateSimulatedLogin(first, last, email) { result ->
            if (result.isSuccess) {
                val user = result.getOrNull()
                _currentUserFlow.value = user
            }
            onResult(result)
        }
    }

    override fun signUpWithEmailAndPassword(firstName: String, lastName: String, email: String, password: String, onResult: (Result<UserModel>) -> Unit) {
        firebaseManager.signUpWithEmailAndPassword(firstName, lastName, email, password) { result ->
            if (result.isSuccess) {
                val user = result.getOrNull()
                _currentUserFlow.value = user
            }
            onResult(result)
        }
    }

    override fun signInWithEmailAndPassword(email: String, password: String, onResult: (Result<UserModel>) -> Unit) {
        firebaseManager.signInWithEmailAndPassword(email, password) { result ->
            if (result.isSuccess) {
                val user = result.getOrNull()
                _currentUserFlow.value = user
            }
            onResult(result)
        }
    }

    override suspend fun completeOnboarding(nativeLanguage: String, targetLanguage: String): Boolean {
        val success = firebaseManager.updateOnboardingLangs(nativeLanguage, targetLanguage)
        if (success) {
            val uid = firebaseManager.getCurrentUserUid()
            if (uid != null) {
                _currentUserFlow.value = firebaseManager.getProfile(uid)
            }
        }
        return success
    }

    override suspend fun addXP(amount: Int): UserModel? {
        val updated = firebaseManager.addXPEnergy(amount)
        if (updated != null) {
            _currentUserFlow.value = updated
        }
        return updated
    }

    override suspend fun incrementStreak(newValue: Int): UserModel? {
        val updated = firebaseManager.updateStreakVal(newValue)
        if (updated != null) {
            _currentUserFlow.value = updated
        }
        return updated
    }

    override fun logout() {
        firebaseManager.logout()
        _currentUserFlow.value = null
    }

    override fun isEmailVerified(): Boolean {
        return firebaseManager.isEmailVerified()
    }

    override fun setSimulatedUserVerified(email: String, verified: Boolean) {
        firebaseManager.setSimulatedUserVerified(email, verified)
        val currentUid = firebaseManager.getCurrentUserUid()
        if (currentUid != null) {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val profile = firebaseManager.getProfile(currentUid)
                _currentUserFlow.value = profile
            }
        }
    }

    override fun sendEmailVerification(onResult: (Result<Unit>) -> Unit) {
        firebaseManager.sendEmailVerification(onResult)
    }

    override fun reloadUser(onResult: (Result<Unit>) -> Unit) {
        firebaseManager.reloadUser { result ->
            if (result.isSuccess) {
                val currentUid = firebaseManager.getCurrentUserUid()
                if (currentUid != null) {
                    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val profile = firebaseManager.getProfile(currentUid)
                        _currentUserFlow.value = profile
                    }
                }
            }
            onResult(result)
        }
    }

    override fun sendPasswordResetEmail(email: String, onResult: (Result<Unit>) -> Unit) {
        firebaseManager.sendPasswordResetEmail(email, onResult)
    }

    override fun generateAndStoreResetCode(email: String, onResult: (Result<String>) -> Unit) {
        firebaseManager.generateAndStoreResetCode(email, onResult)
    }

    override fun verifyResetCode(email: String, code: String, onResult: (Result<Unit>) -> Unit) {
        firebaseManager.verifyResetCode(email, code, onResult)
    }

    override fun resetPasswordWithCode(email: String, newPassword: String, onResult: (Result<Unit>) -> Unit) {
        firebaseManager.resetPasswordOffline(email, newPassword, onResult)
    }

    override fun changePassword(currentPassword: String, newPassword: String, onResult: (Result<Unit>) -> Unit) {
        firebaseManager.changePassword(currentPassword, newPassword, onResult)
    }

    override fun deleteAccount(onResult: (Result<Unit>) -> Unit) {
        firebaseManager.deleteAccount { result ->
            if (result.isSuccess) {
                _currentUserFlow.value = null
            }
            onResult(result)
        }
    }
}
