package com.example.data.remote.firebase

import android.content.Context
import android.util.Log
import com.example.data.model.UserModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.DelicateCoroutinesApi

class FirebaseManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseManager"
        
        @Volatile
        private var instance: FirebaseManager? = null

        fun getInstance(context: Context): FirebaseManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    var isFallbackMode: Boolean = true
        private set

    init {
        isFallbackMode = true
    }

    // High fidelity simulator storage if Firebase is absent
    private var localSimulatedUser: UserModel? = null
    private val prefs = context.getSharedPreferences("firebase_simulator_prefs", Context.MODE_PRIVATE)

    init {
        val uid = prefs.getString("user_uid", "guest_user_uid") ?: "guest_user_uid"
        val email = prefs.getString("user_email", "guest@example.com") ?: "guest@example.com"
        localSimulatedUser = UserModel(
            uid = uid,
            name = prefs.getString("user_name", "Language Learner") ?: "Language Learner",
            firstName = prefs.getString("user_first_name", "Language") ?: "Language",
            lastName = prefs.getString("user_last_name", "Learner") ?: "Learner",
            email = email,
            photoUrl = prefs.getString("user_photo", "https://api.dicebear.com/7.x/pixel-art/svg?seed=Lucky") ?: "",
            createdAt = prefs.getLong("user_created", System.currentTimeMillis()),
            nativeLanguage = prefs.getString("user_native", "English"),
            targetLanguage = prefs.getString("user_target", "Spanish"),
            xp = prefs.getInt("user_xp", 0),
            streak = prefs.getInt("user_streak", 0),
            level = prefs.getInt("user_level", 1),
            isVerified = prefs.getBoolean("user_verified_${email}", true)
        )
    }

    fun isUserLoggedIn(): Boolean {
        return if (isFallbackMode) {
            localSimulatedUser != null
        } else {
            auth?.currentUser != null
        }
    }

    fun isEmailVerified(): Boolean {
        val localUser = localSimulatedUser
        if (localUser != null) {
            if (localUser.email == "guest@example.com" || localUser.uid == "guest_user_uid" || localUser.isVerified) {
                return true
            }
        }
        val firebaseUser = auth?.currentUser
        if (firebaseUser != null) {
            if (firebaseUser.isAnonymous || firebaseUser.uid == "guest_user_uid" || firebaseUser.email == "guest@example.com" || firebaseUser.isEmailVerified) {
                return true
            }
        }
        return if (isFallbackMode) {
            localUser?.isVerified == true
        } else {
            firebaseUser?.isEmailVerified == true
        }
    }

    fun setSimulatedUserVerified(email: String, verified: Boolean) {
        prefs.edit().putBoolean("user_verified_${email}", verified).apply()
        if (localSimulatedUser?.email == email) {
            localSimulatedUser = localSimulatedUser?.copy(isVerified = verified)
        }
    }

    fun getCurrentUserUid(): String? {
        return if (isFallbackMode) {
            localSimulatedUser?.uid
        } else {
            auth?.currentUser?.uid
        }
    }

    fun getFirebaseUserEmail(): String? {
        return if (isFallbackMode) {
            localSimulatedUser?.email
        } else {
            auth?.currentUser?.email
        }
    }

    fun getFirebaseUserName(): String? {
        return if (isFallbackMode) {
            localSimulatedUser?.name
        } else {
            auth?.currentUser?.displayName
        }
    }

    fun getFirebasePhotoUrl(): String? {
        return if (isFallbackMode) {
            localSimulatedUser?.photoUrl
        } else {
            auth?.currentUser?.photoUrl?.toString()
        }
    }

    suspend fun getProfile(uid: String): UserModel? {
        if (isFallbackMode) {
            return localSimulatedUser
        }
        return try {
            kotlinx.coroutines.withTimeoutOrNull(8000) {
                val document = firestore?.collection("users")?.document(uid)?.get()?.await()
                document?.toObject(UserModel::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching firestore profile: ${e.localizedMessage}")
            null
        }
    }

    suspend fun saveProfile(userModel: UserModel): Boolean {
        if (isFallbackMode) {
            localSimulatedUser = userModel
            prefs.edit().apply {
                putString("user_uid", userModel.uid)
                putString("user_name", userModel.name)
                putString("user_first_name", userModel.firstName)
                putString("user_last_name", userModel.lastName)
                putString("user_email", userModel.email)
                putString("user_photo", userModel.photoUrl)
                putLong("user_created", userModel.createdAt)
                putString("user_native", userModel.nativeLanguage)
                putString("user_target", userModel.targetLanguage)
                putInt("user_xp", userModel.xp)
                putInt("user_streak", userModel.streak)
                putInt("user_level", userModel.level)
                putBoolean("user_verified_${userModel.email}", userModel.isVerified)
            }.apply()
            return true
        }
        return try {
            val success = kotlinx.coroutines.withTimeoutOrNull(8000) {
                firestore?.collection("users")?.document(userModel.uid)?.set(userModel)?.await()
                true
            }
            success == true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving profile in Cloud Firestore: ${e.localizedMessage}")
            false
        }
    }

    suspend fun updateOnboardingLangs(native: String, target: String): Boolean {
        val uid = getCurrentUserUid() ?: return false
        val current = getProfile(uid) ?: return false
        val updated = current.copy(nativeLanguage = native, targetLanguage = target)
        return saveProfile(updated)
    }

    suspend fun addXPEnergy(amount: Int): UserModel? {
        val uid = getCurrentUserUid() ?: return null
        val current = getProfile(uid) ?: return null
        val newXp = current.xp + amount
        val newLevel = 1 + (newXp / 1000)
        val updated = current.copy(xp = newXp, level = newLevel)
        val success = saveProfile(updated)
        return if (success) updated else null
    }

    suspend fun updateStreakVal(streakCount: Int): UserModel? {
        val uid = getCurrentUserUid() ?: return null
        val current = getProfile(uid) ?: return null
        val updated = current.copy(streak = streakCount)
        val success = saveProfile(updated)
        return if (success) updated else null
    }

    fun loginGuest(onResult: (Result<UserModel>) -> Unit) {
        val guestUser = UserModel(
            uid = "guest_user_uid",
            name = "Guest User",
            firstName = "Guest",
            lastName = "User",
            email = "guest@example.com",
            photoUrl = "https://api.dicebear.com/7.x/pixel-art/svg?seed=Guest",
            createdAt = System.currentTimeMillis(),
            nativeLanguage = "English",
            targetLanguage = "Spanish",
            xp = 0,
            streak = 0,
            level = 1,
            isVerified = true
        )
        if (isFallbackMode) {
            localSimulatedUser = guestUser
            prefs.edit().apply {
                putString("user_uid", "guest_user_uid")
                putString("user_name", "Guest User")
                putString("user_first_name", "Guest")
                putString("user_last_name", "User")
                putString("user_email", "guest@example.com")
                putString("user_photo", "https://api.dicebear.com/7.x/pixel-art/svg?seed=Guest")
                putLong("user_created", guestUser.createdAt)
                putString("user_native", "English")
                putString("user_target", "Spanish")
                putInt("user_xp", 0)
                putInt("user_streak", 0)
                putInt("user_level", 1)
                putBoolean("user_verified_guest@example.com", true)
                apply()
            }
            onResult(Result.success(guestUser))
        } else {
            val firebaseAuth = auth
            if (firebaseAuth != null) {
                firebaseAuth.signInAnonymously().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = task.result?.user
                        val fbGuest = UserModel(
                            uid = firebaseUser?.uid ?: "guest_user_uid",
                            name = "Guest User",
                            firstName = "Guest",
                            lastName = "User",
                            email = "guest@example.com",
                            photoUrl = "https://api.dicebear.com/7.x/pixel-art/svg?seed=Guest",
                            createdAt = System.currentTimeMillis(),
                            nativeLanguage = "English",
                            targetLanguage = "Spanish",
                            xp = 0,
                            streak = 0,
                            level = 1,
                            isVerified = true
                        )
                        onResult(Result.success(fbGuest))
                    } else {
                        localSimulatedUser = guestUser
                        onResult(Result.success(guestUser))
                    }
                }
            } else {
                localSimulatedUser = guestUser
                onResult(Result.success(guestUser))
            }
        }
    }

    fun initiateSimulatedLogin(firstName: String, lastName: String, email: String, onResult: (Result<UserModel>) -> Unit) {
        val simulatedUid = "sim_uid_" + email.hashCode()
        val nameCombined = "$firstName $lastName".trim()
        val user = UserModel(
            uid = simulatedUid,
            name = nameCombined,
            firstName = firstName,
            lastName = lastName,
            email = email,
            photoUrl = "https://api.dicebear.com/7.x/pixel-art/svg?seed=$firstName",
            createdAt = System.currentTimeMillis(),
            nativeLanguage = "English",
            targetLanguage = "Spanish",
            isVerified = false
        )
        localSimulatedUser = user
        prefs.edit().apply {
            putString("user_uid", simulatedUid)
            putString("user_name", nameCombined)
            putString("user_first_name", firstName)
            putString("user_last_name", lastName)
            putString("user_email", email)
            putString("user_photo", user.photoUrl)
            putLong("user_created", user.createdAt)
            putString("user_native", "English")
            putString("user_target", "Spanish")
            putBoolean("user_verified_${email}", false)
        }.apply()
        onResult(Result.success(user))
    }

    fun signUpWithEmailAndPassword(firstName: String, lastName: String, email: String, password: String, onResult: (Result<UserModel>) -> Unit) {
        val nameCombined = "$firstName $lastName".trim()
        val authObj = auth
        if (isFallbackMode || authObj == null) {
            val simulatedUid = "sim_uid_" + email.hashCode()
            val user = UserModel(
                uid = simulatedUid,
                name = nameCombined,
                firstName = firstName,
                lastName = lastName,
                email = email,
                photoUrl = "https://api.dicebear.com/7.x/pixel-art/svg?seed=$firstName",
                createdAt = System.currentTimeMillis(),
                nativeLanguage = "English",
                targetLanguage = "Spanish",
                isVerified = false
            )
            localSimulatedUser = user
            prefs.edit().apply {
                putString("user_uid", simulatedUid)
                putString("user_name", nameCombined)
                putString("user_first_name", firstName)
                putString("user_last_name", lastName)
                putString("user_email", email)
                putString("user_photo", user.photoUrl)
                putLong("user_created", user.createdAt)
                putString("user_native", "English")
                putString("user_target", "Spanish")
                putBoolean("user_verified_${email}", false)
                putString("sim_password_$email", password)
            }.apply()
            onResult(Result.success(user))
            return
        }

        authObj.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = authObj.currentUser
                    if (firebaseUser != null) {
                        val user = UserModel(
                            uid = firebaseUser.uid,
                            name = nameCombined,
                            firstName = firstName,
                            lastName = lastName,
                            email = email,
                            photoUrl = "https://api.dicebear.com/7.x/pixel-art/svg?seed=$firstName",
                            createdAt = System.currentTimeMillis(),
                            nativeLanguage = "English",
                            targetLanguage = "Spanish",
                            isVerified = false
                        )
                        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val success = saveProfile(user)
                            try {
                                firebaseUser.sendEmailVerification()
                            } catch (e: Exception) {
                                Log.e(TAG, "Initial email verification trigger failed: ${e.localizedMessage}")
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (success) {
                                    onResult(Result.success(user))
                                } else {
                                    onResult(Result.failure(Exception("Successfully created authentication account but failed to save user profile into Cloud Firestore database. Please verify that Cloud Firestore is enabled and active in your Firebase console under 'Build > Firestore Database' and has write rules configured.")))
                                }
                            }
                        }
                    } else {
                        onResult(Result.failure(Exception("Unable to retrieve user details after sign-up.")))
                    }
                } else {
                    onResult(Result.failure(task.exception ?: Exception("Sign-up failed.")))
                }
            }
    }

    fun signInWithEmailAndPassword(email: String, password: String, onResult: (Result<UserModel>) -> Unit) {
        val authObj = auth
        if (isFallbackMode || authObj == null) {
            val savedPassword = prefs.getString("sim_password_$email", null)
            if (savedPassword != null && savedPassword != password) {
                onResult(Result.failure(Exception("Incorrect password. Please verify credentials.")))
                return
            }
            val storedUid = prefs.getString("user_uid", null)
            val nameCombined = prefs.getString("user_name", "Demo User") ?: "Demo User"
            val first = prefs.getString("user_first_name", "Demo") ?: "Demo"
            val last = prefs.getString("user_last_name", "User") ?: "User"
            val verified = prefs.getBoolean("user_verified_${email}", false)

            val user = UserModel(
                uid = storedUid ?: ("sim_uid_" + email.hashCode()),
                name = nameCombined,
                firstName = first,
                lastName = last,
                email = email,
                photoUrl = "https://api.dicebear.com/7.x/pixel-art/svg?seed=$first",
                createdAt = prefs.getLong("user_created", System.currentTimeMillis()),
                nativeLanguage = prefs.getString("user_native", "English"),
                targetLanguage = prefs.getString("user_target", "Spanish"),
                isVerified = verified
            )
            localSimulatedUser = user
            prefs.edit().apply {
                putString("user_uid", user.uid)
                putString("user_name", nameCombined)
                putString("user_first_name", first)
                putString("user_last_name", last)
                putString("user_email", email)
                putString("user_photo", user.photoUrl)
                putBoolean("user_verified_${email}", verified)
                if (savedPassword == null) {
                    putString("sim_password_$email", password)
                }
            }.apply()
            onResult(Result.success(user))
            return
        }

        authObj.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = authObj.currentUser
                    if (firebaseUser != null) {
                        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val profile = getProfile(firebaseUser.uid)
                            val userToSave = if (profile == null) {
                                UserModel(
                                    uid = firebaseUser.uid,
                                    name = firebaseUser.displayName ?: email.substringBefore("@"),
                                    firstName = email.substringBefore("@"),
                                    lastName = "",
                                    email = email,
                                    photoUrl = "https://api.dicebear.com/7.x/pixel-art/svg?seed=${firebaseUser.uid}",
                                    createdAt = System.currentTimeMillis(),
                                    nativeLanguage = "English",
                                    targetLanguage = "Spanish"
                                )
                            } else if (profile.nativeLanguage.isNullOrEmpty() || profile.targetLanguage.isNullOrEmpty()) {
                                profile.copy(
                                    nativeLanguage = profile.nativeLanguage ?: "English",
                                    targetLanguage = profile.targetLanguage ?: "Spanish"
                                )
                            } else null

                            if (userToSave != null) {
                                saveProfile(userToSave)
                            }

                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                onResult(Result.success(profile ?: userToSave!!))
                            }
                        }
                    } else {
                        onResult(Result.failure(Exception("Unable to retrieve user details.")))
                    }
                } else {
                    onResult(Result.failure(task.exception ?: Exception("Sign-in failed. Please verify credentials.")))
                }
            }
    }

    fun sendEmailVerification(onResult: (Result<Unit>) -> Unit) {
        val user = auth?.currentUser
        if (isFallbackMode || user == null) {
            onResult(Result.success(Unit))
            return
        }
        user.sendEmailVerification()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(Result.success(Unit))
                } else {
                    onResult(Result.failure(task.exception ?: Exception("Failed to send verification email.")))
                }
            }
    }

    fun reloadUser(onResult: (Result<Unit>) -> Unit) {
        val user = auth?.currentUser
        if (isFallbackMode || user == null) {
            // Simulated reload
            if (isFallbackMode && localSimulatedUser != null) {
                val email = localSimulatedUser?.email ?: ""
                val verified = prefs.getBoolean("user_verified_${email}", false)
                localSimulatedUser = localSimulatedUser?.copy(isVerified = verified)
            }
            onResult(Result.success(Unit))
            return
        }
        user.reload()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(Result.success(Unit))
                } else {
                    onResult(Result.failure(task.exception ?: Exception("Failed to reload user status.")))
                }
            }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Result<Unit>) -> Unit) {
        val authObj = auth
        if (isFallbackMode || authObj == null) {
            onResult(Result.success(Unit))
            return
        }
        authObj.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(Result.success(Unit))
                } else {
                    onResult(Result.failure(task.exception ?: Exception("Failed to send password reset email.")))
                }
            }
    }

    fun generateAndStoreResetCode(email: String, onResult: (Result<String>) -> Unit) {
        try {
            val code = (100000..999999).random().toString()
            prefs.edit().apply {
                putString("reset_code_$email", code)
                putInt("reset_failures_$email", 0)
                putBoolean("reset_verified_$email", false)
                putLong("reset_code_time_$email", System.currentTimeMillis())
                apply()
            }
            Log.d(TAG, "Backend OTP generated and stored for $email: $code")
            onResult(Result.success(code))
        } catch (e: Exception) {
            onResult(Result.failure(e))
        }
    }

    fun verifyResetCode(email: String, code: String, onResult: (Result<Unit>) -> Unit) {
        try {
            val failureCount = prefs.getInt("reset_failures_$email", 0)
            if (failureCount >= 3) {
                Log.w(TAG, "Verification blocked: Email reset is fully locked out due to brute-force protection for $email.")
                onResult(Result.failure(Exception("LOCKOUT_ERROR")))
                return
            }

            val storedCode = prefs.getString("reset_code_$email", null)
            if (storedCode == null) {
                onResult(Result.failure(Exception("No active reset code found. Please request a new code.")))
                return
            }

            if (storedCode == code) {
                prefs.edit().apply {
                    putBoolean("reset_verified_$email", true)
                    putInt("reset_failures_$email", 0)
                    apply()
                }
                Log.i(TAG, "Successfully validated OTP code on the backend simulation for $email.")
                onResult(Result.success(Unit))
            } else {
                val newFailures = failureCount + 1
                prefs.edit().putInt("reset_failures_$email", newFailures).apply()
                Log.w(TAG, "Incorrect reset code entry ($newFailures/3 attempts failed) for $email.")
                if (newFailures >= 3) {
                    onResult(Result.failure(Exception("LOCKOUT_ERROR")))
                } else {
                    onResult(Result.failure(Exception("ATTEMPTS_REMAINING:${3 - newFailures}")))
                }
            }
        } catch (e: Exception) {
            onResult(Result.failure(e))
        }
    }

    fun resetPasswordOffline(email: String, newPassword: String, onResult: (Result<Unit>) -> Unit) {
        try {
            val isVerified = prefs.getBoolean("reset_verified_$email", false)
            if (!isVerified) {
                onResult(Result.failure(Exception("Unauthorized reset. Please verify OTP first before attempting database update.")))
                return
            }
            // Update the password and consume verification state
            prefs.edit().apply {
                putString("sim_password_$email", newPassword)
                putBoolean("reset_verified_$email", false) // Consume state
                remove("reset_code_$email") // Delete OTP once consumed successfully
                apply()
            }
            Log.d(TAG, "Successfully updated stored simulated password for email: $email and cleared reset token.")
            onResult(Result.success(Unit))
        } catch (e: Exception) {
            onResult(Result.failure(e))
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, onResult: (Result<Unit>) -> Unit) {
        val user = auth?.currentUser
        if (isFallbackMode || user == null) {
            if (isFallbackMode && localSimulatedUser != null) {
                val email = localSimulatedUser?.email ?: ""
                val savedPass = prefs.getString("sim_password_$email", null)
                if (savedPass != null && savedPass != currentPassword) {
                    onResult(Result.failure(Exception("Incorrect current password.")))
                    return
                }
                prefs.edit().putString("sim_password_$email", newPassword).apply()
            }
            onResult(Result.success(Unit))
            return
        }
        val email = user.email ?: ""
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential)
            .addOnCompleteListener { reauthTask ->
                if (reauthTask.isSuccessful) {
                    user.updatePassword(newPassword)
                        .addOnCompleteListener { updateTask ->
                            if (updateTask.isSuccessful) {
                                onResult(Result.success(Unit))
                            } else {
                                onResult(Result.failure(updateTask.exception ?: Exception("Failed to update password.")))
                            }
                        }
                } else {
                    onResult(Result.failure(reauthTask.exception ?: Exception("Incorrect current password. Re-authentication failed.")))
                }
            }
    }

    fun deleteAccount(onResult: (Result<Unit>) -> Unit) {
        val user = auth?.currentUser
        if (isFallbackMode || user == null) {
            logout()
            onResult(Result.success(Unit))
            return
        }
        val uid = user.uid
        user.delete()
            .addOnCompleteListener { deleteTask ->
                if (deleteTask.isSuccessful) {
                    @OptIn(DelicateCoroutinesApi::class)
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            firestore?.collection("users")?.document(uid)?.delete()?.await()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete firestore user document: ${e.localizedMessage}")
                        }
                    }
                    logout()
                    onResult(Result.success(Unit))
                } else {
                    onResult(Result.failure(deleteTask.exception ?: Exception("Failed to delete account.")))
                }
            }
    }

    fun logout() {
        if (isFallbackMode) {
            localSimulatedUser = null
            prefs.edit().clear().apply()
        } else {
            auth?.signOut()
        }
    }
}
