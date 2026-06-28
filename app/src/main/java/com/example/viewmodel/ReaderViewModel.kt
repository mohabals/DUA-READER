package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LocalDatabase
import com.example.data.SentenceRepository
import com.example.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.BuildConfig

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val database = LocalDatabase.getDatabase(application)
    private val repository = SentenceRepository(database.sentenceDao())

    // --- Discover (Story Content Platform) State Flows ---
    private val firestoreRepository = com.example.data.repository.FirestoreStoryRepository(application)

    private val _featuredStories = MutableStateFlow<List<FirestoreStory>>(emptyList())
    val featuredStories: StateFlow<List<FirestoreStory>> = _featuredStories.asStateFlow()

    private val _trendingStories = MutableStateFlow<List<FirestoreStory>>(emptyList())
    val trendingStories: StateFlow<List<FirestoreStory>> = _trendingStories.asStateFlow()

    private val _latestStories = MutableStateFlow<List<FirestoreStory>>(emptyList())
    val latestStories: StateFlow<List<FirestoreStory>> = _latestStories.asStateFlow()

    val discoverSearchQuery = MutableStateFlow("")
    val selectedDiscoverCategory = MutableStateFlow("")
    val selectedDiscoverLanguage = MutableStateFlow("")
    val selectedDiscoverDifficulty = MutableStateFlow("")

    val isDiscoverLoading = MutableStateFlow(false)
    val isDiscoverRefreshing = MutableStateFlow(false)
    val discoverError = MutableStateFlow<String?>(null)

    val hasMoreLatestStories = MutableStateFlow(true)

    // All available groups
    val groups: StateFlow<List<StoryGroup>> = repository.allGroups
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // All stories in the database
    val allStories: StateFlow<List<Story>> = repository.allStories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Progress Tracking Flows ---
    val allActivityLogs: StateFlow<List<ActivityLog>> = repository.allActivityLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userProgress: StateFlow<UserProgress?> = repository.userProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val allAchievements: StateFlow<List<Achievement>> = repository.allAchievements
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _celebrationMessage = MutableSharedFlow<String>()
    val celebrationMessage: SharedFlow<String> = _celebrationMessage.asSharedFlow()

    private val _shieldActivatedMessage = MutableSharedFlow<String>()
    val shieldActivatedMessage: SharedFlow<String> = _shieldActivatedMessage.asSharedFlow()

    private val readSentencesToday = mutableStateOf(setOf<Int>())

    // Active reading selections
    val activeStoryId = MutableStateFlow<Int?>(null)
    val activeStory = MutableStateFlow<Story?>(null)

    // Global Settings State Flows (User-controlled attributes)
    val fontSizeScale = MutableStateFlow(1f)
    val isDarkMode = MutableStateFlow<Boolean?>(null) // null = system, true = dark, false = light
    val appTheme = MutableStateFlow("natural") // "natural" or "glassy"
    val showTranslationAlways = MutableStateFlow(true)
    val speechPitch = MutableStateFlow(1.0f)
    val speechRate = MutableStateFlow(1.0f)

    private val sharedPrefs = application.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE)
    private val appDataStore = com.example.data.AppDataStore(application)

    val aiExplanationPrompt = MutableStateFlow(
        sharedPrefs.getString("key_ai_explanation_prompt", """
Explain this sentence.
Include:
Natural translation
Word breakdown
Grammar notes
Common usage
Alternative expressions

Sentence: "{sentence}"
        """.trimIndent()) ?: """
Explain this sentence.
Include:
Natural translation
Word breakdown
Grammar notes
Common usage
Alternative expressions

Sentence: "{sentence}"
        """.trimIndent()
    )

    fun setAiExplanationPrompt(prompt: String) {
        aiExplanationPrompt.value = prompt
        sharedPrefs.edit().putString("key_ai_explanation_prompt", prompt).apply()
    }

    sealed interface AiExplanationState {
        object Idle : AiExplanationState
        object Loading : AiExplanationState
        data class Success(val sentence: String, val explanation: String) : AiExplanationState
        data class Error(val message: String) : AiExplanationState
    }

    private val _aiExplanationState = MutableStateFlow<AiExplanationState>(AiExplanationState.Idle)
    val aiExplanationState = _aiExplanationState.asStateFlow()

    private var explanationJob: Job? = null

    fun dismissAiExplanation() {
        explanationJob?.cancel()
        _aiExplanationState.value = AiExplanationState.Idle
    }

    fun explainSentenceWithGemini(sentenceText: String) {
        explanationJob?.cancel() // Cancel previous active request
        
        // Check local cache first to provide sub-millisecond instant responses and prevent API calls
        val cached = com.example.api.GeminiService.getCachedExplanation(sentenceText)
        if (cached != null) {
            _aiExplanationState.value = AiExplanationState.Success(sentenceText, cached)
            return
        }

        _aiExplanationState.value = AiExplanationState.Loading
        explanationJob = viewModelScope.launch {
            val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
            if (apiKey.isEmpty() || apiKey.contains("MY_GEMINI_API_KEY")) {
                _aiExplanationState.value = AiExplanationState.Error("Gemini API key is not configured in the Secrets panel.")
                return@launch
            }

            val template = aiExplanationPrompt.value
            val prompt = if (template.contains("{sentence}")) {
                template.replace("{sentence}", sentenceText)
            } else {
                "${template}\n\nSentence: \"$sentenceText\""
            }

            try {
                // Request optimized for ultra-low latency with max output tokens capped to 256
                val text = com.example.api.GeminiService.generateText(
                    apiKey = apiKey,
                    prompt = prompt,
                    maxTokens = 256,
                    temperature = 0.2
                )
                
                com.example.api.GeminiService.setCachedExplanation(sentenceText, text)
                _aiExplanationState.value = AiExplanationState.Success(sentenceText, text)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e // Propagate coroutine cancellation cleanly
                }
                e.printStackTrace()
                _aiExplanationState.value = AiExplanationState.Error(e.message ?: "Unknown error occurred.")
            }
        }
    }

    // Experimental Settings State Flows
    val expWordFocus = MutableStateFlow(false)
    val expGrammarColor = MutableStateFlow(false)
    val expKaraokeTts = MutableStateFlow(false)
    val expPronunciationCoach = MutableStateFlow(false)

    val isOnboardingCompleted = MutableStateFlow(sharedPrefs.getBoolean("key_onboarding_completed", false))
    val isRememberMeEnabled = MutableStateFlow(sharedPrefs.getBoolean("key_remember_me_enabled", true))

    fun setRememberMeEnabled(enabled: Boolean) {
        isRememberMeEnabled.value = enabled
        sharedPrefs.edit().putBoolean("key_remember_me_enabled", enabled).apply()
    }

    // --- Firebase Authentication & Cloud Sync Integration ---
    val authRepository: com.example.data.repository.AuthRepository = com.example.data.repository.AuthRepositoryImpl(application)
    val isFirebaseFallback = MutableStateFlow(authRepository.isFallbackMode())
    val currentUserFlow: StateFlow<com.example.data.model.UserModel?> = authRepository.currentUserFlow

    val isLoggedIn: StateFlow<Boolean> = authRepository.currentUserFlow
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.currentUserFlow.value != null)

    val isEmailVerifiedFlow: StateFlow<Boolean> = authRepository.currentUserFlow
        .map { it == null || it.isVerified || it.email == "guest@example.com" || it.uid == "guest_user_uid" || authRepository.isEmailVerified() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.currentUserFlow.value == null || authRepository.currentUserFlow.value?.isVerified == true || authRepository.currentUserFlow.value?.email == "guest@example.com" || authRepository.isEmailVerified())

    fun loginAsGuest(onFinished: (Result<com.example.data.model.UserModel>) -> Unit) {
        authRepository.loginGuest { result ->
            if (result.isSuccess) {
                isOnboardingCompleted.value = true
                sharedPrefs.edit().putBoolean("key_onboarding_completed", true).apply()
            }
            onFinished(result)
        }
    }

    fun loginSimulated(name: String, email: String, onFinished: () -> Unit) {
        authRepository.loginWithGoogleSimulated(name, email) { result ->
            if (result.isSuccess) {
                val user = result.getOrNull()
                if (user != null && !user.nativeLanguage.isNullOrEmpty() && !user.targetLanguage.isNullOrEmpty()) {
                    isOnboardingCompleted.value = true
                }
            }
            onFinished()
        }
    }

    fun signUpCustom(firstName: String, lastName: String, email: String, password: String, onFinished: (Result<com.example.data.model.UserModel>) -> Unit) {
        authRepository.signUpWithEmailAndPassword(firstName, lastName, email, password) { result ->
            if (result.isSuccess) {
                val user = result.getOrNull()
                if (user != null && !user.nativeLanguage.isNullOrEmpty() && !user.targetLanguage.isNullOrEmpty()) {
                    isOnboardingCompleted.value = true
                }
            }
            onFinished(result)
        }
    }

    fun signInCustom(email: String, password: String, onFinished: (Result<com.example.data.model.UserModel>) -> Unit) {
        authRepository.signInWithEmailAndPassword(email, password) { result ->
            if (result.isSuccess) {
                val user = result.getOrNull()
                if (user != null && !user.nativeLanguage.isNullOrEmpty() && !user.targetLanguage.isNullOrEmpty()) {
                    isOnboardingCompleted.value = true
                }
            }
            onFinished(result)
        }
    }

    fun sendPasswordResetEmail(email: String, onFinished: (Result<Unit>) -> Unit) {
        authRepository.sendPasswordResetEmail(email, onFinished)
    }

    fun generateAndStoreResetCode(email: String, onFinished: (Result<String>) -> Unit) {
        authRepository.generateAndStoreResetCode(email, onFinished)
    }

    fun verifyResetCode(email: String, code: String, onFinished: (Result<Unit>) -> Unit) {
        authRepository.verifyResetCode(email, code, onFinished)
    }

    fun resetPasswordWithCode(email: String, newPassword: String, onFinished: (Result<Unit>) -> Unit) {
        authRepository.resetPasswordWithCode(email, newPassword, onFinished)
    }

    fun sendEmailVerification(onFinished: (Result<Unit>) -> Unit) {
        authRepository.sendEmailVerification(onFinished)
    }

    fun reloadUser(onFinished: (Result<Unit>) -> Unit) {
        authRepository.reloadUser(onFinished)
    }

    fun changePassword(current: String, new: String, onFinished: (Result<Unit>) -> Unit) {
        authRepository.changePassword(current, new, onFinished)
    }

    fun deleteAccount(onFinished: (Result<Unit>) -> Unit) {
        authRepository.deleteAccount(onFinished)
    }

    fun setSimulatedUserVerified(email: String, verified: Boolean) {
        authRepository.setSimulatedUserVerified(email, verified)
    }

    fun simulateSignOut() {
        authRepository.logout()
        isOnboardingCompleted.value = false
        sharedPrefs.edit().putBoolean("key_onboarding_completed", false).apply()
        // Reset local database progress structure too
        viewModelScope.launch {
            val existing = repository.getUserProgress() ?: UserProgress()
            repository.insertUserProgress(existing.copy(userName = "", nativeLanguage = "", targetLanguage = ""))
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            repository.deleteAll()
            repository.deleteAllStories()
            repository.deleteAllGroups()
            loadInitialFablesData()
            simulateSignOut()
            _importResult.emit(ImportResult.Success(0, "App completely reset to factory defaults!"))
        }
    }

    fun completeOnboarding(
        name: String,
        nativeLang: String,
        targetLang: String,
        goalMultiplier: Int,
        difficulty: String
    ) {
        viewModelScope.launch {
            // Write languages and profile parameters to Cloud repository
            authRepository.completeOnboarding(nativeLang, targetLang)

            val existingProgress = repository.getUserProgress() ?: UserProgress()
            val newProgress = existingProgress.copy(
                userName = name,
                nativeLanguage = nativeLang,
                targetLanguage = targetLang,
                currentGoalSentences = goalMultiplier,
                difficulty = difficulty
            )
            repository.insertUserProgress(newProgress)
            
            // Mark onboarding completed in preferences
            sharedPrefs.edit().putBoolean("key_onboarding_completed", true).apply()
            isOnboardingCompleted.value = true
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            sharedPrefs.edit().putBoolean("key_onboarding_completed", false).apply()
            isOnboardingCompleted.value = false
        }
    }

    // Reactive list of sentences for current active story
    val sentences: StateFlow<List<SentenceCard>> = activeStoryId
        .flatMapLatest { storyId ->
            if (storyId == null) {
                flowOf(emptyList())
            } else {
                repository.getSentencesForStory(storyId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Globally starred/saved reviews across all stories
    val savedSentences: StateFlow<List<SentenceCard>> = repository.savedSentences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun getStorySentencesFlow(storyId: Int): Flow<List<SentenceCard>> {
        return repository.getSentencesForStory(storyId)
    }

    // Parsing Status Events
    private val _importResult = MutableSharedFlow<ImportResult>()
    val importResult: SharedFlow<ImportResult> = _importResult.asSharedFlow()

    // Temp binding state
    val inputText = MutableStateFlow("")



    init {
        // Load initial settings
        fontSizeScale.value = sharedPrefs.getFloat("key_font_size", 1.0f)
        val modeStr = sharedPrefs.getString("key_dark_mode", "system")
        isDarkMode.value = when (modeStr) {
            "dark" -> true
            "light" -> false
            else -> null
        }
        appTheme.value = sharedPrefs.getString("key_app_theme", "natural") ?: "natural"
        showTranslationAlways.value = sharedPrefs.getBoolean("key_show_translation", true)
        speechPitch.value = sharedPrefs.getFloat("key_speech_pitch", 1.0f)
        speechRate.value = sharedPrefs.getFloat("key_speech_rate", 1.0f)

        // Save settings automatically when changed
        viewModelScope.launch {
            appTheme.collect { theme ->
                sharedPrefs.edit().putString("key_app_theme", theme).apply()
            }
        }
        viewModelScope.launch {
            fontSizeScale.collect { sharedPrefs.edit().putFloat("key_font_size", it).apply() }
        }
        viewModelScope.launch {
            isDarkMode.collect { mode ->
                val strValue = when (mode) {
                    true -> "dark"
                    false -> "light"
                    else -> "system"
                }
                sharedPrefs.edit().putString("key_dark_mode", strValue).apply()
            }
        }
        viewModelScope.launch {
            showTranslationAlways.collect { sharedPrefs.edit().putBoolean("key_show_translation", it).apply() }
        }
        viewModelScope.launch {
            speechPitch.collect { sharedPrefs.edit().putFloat("key_speech_pitch", it).apply() }
        }
        viewModelScope.launch {
            speechRate.collect { sharedPrefs.edit().putFloat("key_speech_rate", it).apply() }
        }

        viewModelScope.launch {
            val initial = appDataStore.featureWordHighlightFlow.first()
            expWordFocus.value = initial
            expWordFocus.collect { appDataStore.setFeatureWordHighlight(it) }
        }
        viewModelScope.launch {
            val initial = appDataStore.featureGrammarColorFlow.first()
            expGrammarColor.value = initial
            expGrammarColor.collect { appDataStore.setFeatureGrammarColor(it) }
        }
        viewModelScope.launch {
            val initial = appDataStore.featureKaraokeTtsFlow.first()
            expKaraokeTts.value = initial
            expKaraokeTts.collect { appDataStore.setFeatureKaraokeTts(it) }
        }
        viewModelScope.launch {
            val initial = appDataStore.featurePronunciationCoachFlow.first()
            expPronunciationCoach.value = initial
            expPronunciationCoach.collect { appDataStore.setFeaturePronunciationCoach(it) }
        }

        // Automatically prefill sample data with structured groups and stories if blank on start
        viewModelScope.launch {
            val list = repository.allGroups.first()
            if (list.isEmpty()) {
                loadInitialFablesData()
            } else if (activeStoryId.value == null) {
                // Pull the first story to display as default
                val stories = repository.allStories.first()
                if (stories.isNotEmpty() && activeStoryId.value == null) {
                    selectStory(stories.first())
                }
            }
        }

        initializeProgressData()
    }



    private suspend fun loadInitialFablesData() {
        // Create initial Group
        val groupId = repository.insertGroup(StoryGroup(name = "Classic Tales"))
        
        // Create initial Story 1
        val story1Id = repository.insertStory(
            Story(groupId = groupId.toInt(), title = "The Tortoise & The Hare")
        )
        
        // Fables Story content
        val fableString = """
            Había una vez una tortuga y una liebre. | Once upon a time there was a tortoise and a hare.
            La liebre corría muy rápido y se burlaba de la tortuga. | The hare ran very fast and made fun of the tortoise.
            Un día, la tortuga desafió a la liebre a una carrera. | One day, the tortoise challenged the hare to a race.
            La liebre aceptó, muy segura de sí misma. | The hare accepted, very confident in herself.
            Al comenzar la carrera, la liebre salió corriendo velozmente. | As the race began, the hare shot ahead swiftly.
            Viendo que llevaba mucha ventaja, la liebre decidió tomar una siesta. | Seeing that she had a huge lead, the hare decided to take a nap.
            Mientras la liebre dormía, la tortuga siguió caminando lentamente pero sin detenerse. | While the hare was sleeping, the tortoise kept walking slowly but without stopping.
            Poco a poco, la tortuga se acercó a la línea de meta. | Little by little, the tortoise approached the finish line.
            Cuando la liebre despertó, vio que la tortuga estaba a punto de ganar. | When the hare woke up, she saw that the tortoise was about to win.
            La liebre corrió con todas sus fuerzas, pero ya era tarde. | The hare ran with all her might, but it was too late.
            La tortuga cruzó la meta primero y ganó la carrera. | The tortoise crossed the finish line first and won the race.
            La constancia vence a la rapidez. | Consistency beats speed.
        """.trimIndent()
        
        val parsed1 = parseBilingualLines(fableString, story1Id.toInt())
        repository.insertAll(parsed1)

        // Create initial Story 2 for demo purposes
        val story2Id = repository.insertStory(
            Story(groupId = groupId.toInt(), title = "Evening in Berlin")
        )
        val berlinString = """
            Der Wind weht sanft durch die Straßen Berlins. | The wind blows softly through the streets of Berlin.
            Die Lichter de Stadt begannen herrlich zu leuchten. | The city lights began to glow magnificently.
            Es war ein magischer Moment an einem Sommerabend. | It was a magical moment on a summer evening.
            Überall saßen Menschen in gemütlichen Straßencafés. | People were sitting everywhere in cozy street cafes.
            Sie tranken Kaffee, plauderten und lachten gemeinsam. | They drank coffee, chatted and laughed together.
            Ein Straßenmusiker spielte eine sanfte Melodie auf seiner Gitarre. | A street musician played a gentle melody on his guitar.
            Das Leben fühlte sich friedlich und vollkommen an. | Life felt peaceful and complete.
        """.trimIndent()

        val parsed2 = parseBilingualLines(berlinString, story2Id.toInt())
        repository.insertAll(parsed2)

        // Select the first story
        val firstStory = Story(id = story1Id.toInt(), groupId = groupId.toInt(), title = "The Tortoise & The Hare")
        selectStory(firstStory)
    }

    fun selectStory(story: Story) {
        activeStory.value = story
        activeStoryId.value = story.id
    }

    // --- Story Groups & Stories Management Functions ---
    fun addGroup(name: String) {
        viewModelScope.launch {
            if (name.trim().isNotEmpty()) {
                repository.insertGroup(StoryGroup(name = name.trim()))
                _importResult.emit(ImportResult.Success(0, "Group '$name' created!"))
            } else {
                _importResult.emit(ImportResult.Error("Group name cannot be empty."))
            }
        }
    }

    fun deleteGroup(group: StoryGroup) {
        viewModelScope.launch {
            repository.deleteGroup(group)
            // If the deleted group contained our active story, reset selected story
            val active = activeStory.value
            if (active != null && active.groupId == group.id) {
                resetActiveStorySelection()
            }
            _importResult.emit(ImportResult.Success(0, "Category and all its stories deleted."))
        }
    }

    fun addStoryToGroup(groupId: Int, title: String, content: String? = null, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            val trimmedTitle = title.trim()
            if (trimmedTitle.isEmpty()) {
                _importResult.emit(ImportResult.Error("Story title cannot be empty."))
                return@launch
            }

            val storyId = repository.insertStory(Story(groupId = groupId, title = trimmedTitle))
            var sentenceCount = 0

            content?.let { rawText ->
                if (rawText.trim().isNotEmpty()) {
                    val parsed = parseBilingualLines(rawText, storyId.toInt())
                    if (parsed.isNotEmpty()) {
                        repository.insertAll(parsed)
                        sentenceCount = parsed.size
                    }
                }
            }

            // Automatically select the freshly added story!
            val newStory = Story(id = storyId.toInt(), groupId = groupId, title = trimmedTitle)
            selectStory(newStory)

            _importResult.emit(
                ImportResult.Success(
                    sentenceCount,
                    "Story '$trimmedTitle' created successfully!"
                )
            )
            onSuccess?.invoke()
        }
    }

    fun deleteStory(story: Story) {
        viewModelScope.launch {
            repository.deleteStory(story)
            if (activeStoryId.value == story.id) {
                resetActiveStorySelection()
            }
            _importResult.emit(ImportResult.Success(0, "Story '${story.title}' deleted."))
        }
    }

    fun updateStoryProgress(storyId: Int, lastReadIndex: Int) {
        viewModelScope.launch {
            allStories.value.find { it.id == storyId }?.let { story ->
                val updated = story.copy(lastReadIndex = lastReadIndex)
                repository.updateStory(updated)
                if (activeStory.value?.id == storyId) {
                    activeStory.value = updated
                }
            }
        }
    }

    private fun resetActiveStorySelection() {
        viewModelScope.launch {
            // Find any surviving story to switch to
            val stories = repository.allStories.first()
            if (stories.isNotEmpty()) {
                selectStory(stories.first())
            } else {
                activeStory.value = null
                activeStoryId.value = null
            }
        }
    }

    // --- Sentences Operations ---
    fun toggleSaved(id: Int, isSaved: Boolean) {
        viewModelScope.launch {
            repository.setSaved(id, isSaved)
        }
    }

    fun toggleSavedDirect(sentence: SentenceCard) {
        viewModelScope.launch {
            repository.update(sentence.copy(isSaved = !sentence.isSaved))
        }
    }

    fun importText(text: String, append: Boolean = false) {
        val currentStory = activeStory.value
        if (currentStory == null) {
            viewModelScope.launch {
                _importResult.emit(ImportResult.Error("Please select or create a Story in the Library first."))
            }
            return
        }

        viewModelScope.launch {
            if (text.trim().isEmpty()) {
                _importResult.emit(ImportResult.Error("Input text is empty."))
                return@launch
            }

            val parsedList = parseBilingualLines(text, currentStory.id)
            if (parsedList.isEmpty()) {
                _importResult.emit(ImportResult.Error("No valid bilingual sentences found. Format lines as 'Original | Translation'"))
                return@launch
            }

            if (!append) {
                // Clear existing sentences for THIS specific story
                val list = repository.getSentencesForStory(currentStory.id).first()
                for (s in list) {
                    repository.delete(s)
                }
            }

            repository.insertAll(parsedList)
            _importResult.emit(
                ImportResult.Success(
                    parsedList.size,
                    "Successfully imported ${parsedList.size} sentences to '${currentStory.title}'!"
                )
            )
        }
    }

    fun clearActiveStorySentences() {
        val currentStory = activeStory.value ?: return
        viewModelScope.launch {
            val list = repository.getSentencesForStory(currentStory.id).first()
            for (s in list) {
                repository.delete(s)
            }
            _importResult.emit(ImportResult.Success(0, "Cleared all sentences for story: ${currentStory.title}"))
        }
    }

    private fun parseBilingualLines(text: String, targetStoryId: Int): List<SentenceCard> {
        val lines = text.split("\n")
        val parsed = mutableListOf<SentenceCard>()

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            val parts = trimmedLine.split("|")
            if (parts.size >= 2) {
                val original = parts[0].trim()
                val translation = parts.subList(1, parts.size).joinToString("|").trim()
                
                if (original.isNotEmpty() && translation.isNotEmpty()) {
                    parsed.add(
                        SentenceCard(
                            storyId = targetStoryId,
                            originalText = original,
                            translatedText = translation,
                            isSaved = false
                        )
                    )
                }
            }
        }
        return parsed
    }

    // --- Core High-Fidelity Progress System Logic ---

    fun getMonthlyChallenge(monthStr: String): Flow<MonthlyChallenge?> {
        return repository.getMonthlyChallenge(monthStr)
    }

    fun getCurrentDateStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun getCurrentMonthStr(): String {
        return SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    }

    fun getLevelTitleForXP(xp: Int): String {
        return when {
            xp < 100 -> "Reader"
            xp < 300 -> "Explorer"
            xp < 800 -> "Scholar"
            xp < 1500 -> "Linguist"
            xp < 3000 -> "Polyglot"
            else -> "Master Reader"
        }
    }

    fun initializeProgressData() {
        viewModelScope.launch {
            // Check if UserProgress row is present; if not, create default
            val prog = repository.getUserProgress()
            if (prog == null) {
                repository.insertUserProgress(
                    UserProgress(
                        id = 1,
                        currentStreak = 0,
                        longestStreak = 0,
                        lastReadDate = "",
                        shieldsCount = 1, // Start with 1 shield of welcome!
                        levelTitle = "Reader",
                        levelXP = 50,
                        currentGoalSentences = 10,
                        difficulty = "Intermediate"
                    )
                )
            }

            // Check if monthly challenge for current month is initialized
            val curMonth = getCurrentMonthStr()
            val mChallenge = repository.getMonthlyChallengeNow(curMonth)
            if (mChallenge == null) {
                repository.insertMonthlyChallenge(
                    MonthlyChallenge(
                        monthStr = curMonth,
                        targetSentences = 300,
                        completed = false
                    )
                )
            }

            // Check if achievements exist; if not, insert initial achievements catalog
            val achs = repository.allAchievements.first()
            if (achs.isEmpty()) {
                val catalog = listOf(
                    Achievement("first_card", "First Step", "Read your first bilingual sentence card.", "reading", 1),
                    Achievement("story_done", "Story Starter", "Unlock progress by reading at least 5 cards in a story.", "stories", 5),
                    Achievement("streak_3", "Consistency Spark", "Maintain a 3-day reading streak.", "streak", 3),
                    Achievement("streak_7", "Habit Builder", "Maintain a 7-day reading streak.", "streak", 7),
                    Achievement("scholar_100", "Linguistic Scholar", "Read 100 sentences in total.", "reading", 100),
                    Achievement("bilingual_500", "Bilingual Polyglot", "Read 500 sentences in total.", "reading", 500),
                    Achievement("starred_10", "Word Hoarder", "Star/save 10 sentences for review.", "reviewing", 10),
                    Achievement("shield_unlocked", "Shield Bearer", "Earn 3 shields of streak protection.", "streak", 3)
                )
                repository.insertAchievements(catalog)
            }

            // Prefill some historic logs to show a beautiful GitHub heatmap calendar on start!
            val logs = repository.allActivityLogs.first()
            if (logs.isEmpty()) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                
                // Seed 38-day streak: Dec 20, 2025 to Jan 26, 2026
                val cal = java.util.Calendar.getInstance()
                cal.set(2025, java.util.Calendar.DECEMBER, 20)
                val endCal = java.util.Calendar.getInstance()
                endCal.set(2026, java.util.Calendar.JANUARY, 26)
                while (!cal.after(endCal)) {
                    val dateStr = sdf.format(cal.time)
                    repository.insertActivityLog(ActivityLog(date = dateStr, sentencesRead = 12, cardsReviewed = 3, minutesSpent = 15))
                    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                
                // Seed 2-day streak: Jan 29 to Jan 30, 2026
                repository.insertActivityLog(ActivityLog(date = "2026-01-29", sentencesRead = 10, cardsReviewed = 2, minutesSpent = 12))
                repository.insertActivityLog(ActivityLog(date = "2026-01-30", sentencesRead = 11, cardsReviewed = 4, minutesSpent = 14))
                
                // Seed 1-day streak: Feb 1, 2026
                repository.insertActivityLog(ActivityLog(date = "2026-02-01", sentencesRead = 8, cardsReviewed = 1, minutesSpent = 10))
                
                // Seed 1-day streak: May 9, 2026
                repository.insertActivityLog(ActivityLog(date = "2026-05-09", sentencesRead = 15, cardsReviewed = 1, minutesSpent = 8))
                
                // Seed 2-day streak: May 31 to Jun 1, 2026
                repository.insertActivityLog(ActivityLog(date = "2026-05-31", sentencesRead = 14, cardsReviewed = 2, minutesSpent = 15))
                repository.insertActivityLog(ActivityLog(date = "2026-06-01", sentencesRead = 10, cardsReviewed = 3, minutesSpent = 11))
            }
        }
    }

    fun toggleDayCompletion(dateStr: String) {
        viewModelScope.launch {
            val existingLog = repository.getActivityLog(dateStr)
            if (existingLog != null && existingLog.sentencesRead > 0) {
                repository.insertActivityLog(existingLog.copy(sentencesRead = 0))
            } else {
                if (existingLog != null) {
                    repository.insertActivityLog(existingLog.copy(sentencesRead = 10))
                } else {
                    repository.insertActivityLog(ActivityLog(date = dateStr, sentencesRead = 10, cardsReviewed = 2, minutesSpent = 10))
                }
            }
        }
    }

    fun logSentenceRead(sentenceId: Int) {
        if (readSentencesToday.value.contains(sentenceId)) return
        readSentencesToday.value = readSentencesToday.value + sentenceId

        viewModelScope.launch {
            val todayStr = getCurrentDateStr()
            val currentLog = repository.getActivityLog(todayStr) ?: ActivityLog(date = todayStr)
            val updatedLog = currentLog.copy(sentencesRead = currentLog.sentencesRead + 1)
            repository.insertActivityLog(updatedLog)

            val currentProg = repository.getUserProgress() ?: UserProgress()
            val updatedProg = currentProg.copy(
                lastReadDate = todayStr
            )
            repository.insertUserProgress(updatedProg)
        }
    }

    fun logCardReviewed() {
        viewModelScope.launch {
            val todayStr = getCurrentDateStr()
            val currentLog = repository.getActivityLog(todayStr) ?: ActivityLog(date = todayStr)
            val updatedLog = currentLog.copy(cardsReviewed = currentLog.cardsReviewed + 1)
            repository.insertActivityLog(updatedLog)
        }
    }

    fun logActiveMinutes(minutes: Int = 1) {
        viewModelScope.launch {
            val todayStr = getCurrentDateStr()
            val currentLog = repository.getActivityLog(todayStr) ?: ActivityLog(date = todayStr)
            val updatedLog = currentLog.copy(minutesSpent = currentLog.minutesSpent + minutes)
            repository.insertActivityLog(updatedLog)
        }
    }

    private suspend fun checkAndUnlockAchievements(prog: UserProgress, sentencesReadToday: Int) {
        val achievements = repository.allAchievements.first()
        val todayStr = getCurrentDateStr()

        val allLogs = repository.allActivityLogs.first()
        val totalSentencesRead = allLogs.sumOf { it.sentencesRead }
        val totalStarredCount = savedSentences.value.size

        for (ach in achievements) {
            if (ach.unlocked) continue

            var shouldUnlock = false
            when (ach.id) {
                "first_card" -> {
                    if (totalSentencesRead >= 1) shouldUnlock = true
                }
                "story_done" -> {
                    if (sentencesReadToday >= 5) shouldUnlock = true
                }
                "streak_3" -> {
                    if (prog.currentStreak >= 3) shouldUnlock = true
                }
                "streak_7" -> {
                    if (prog.currentStreak >= 7) shouldUnlock = true
                }
                "scholar_100" -> {
                    if (totalSentencesRead >= 100) shouldUnlock = true
                }
                "bilingual_500" -> {
                    if (totalSentencesRead >= 500) shouldUnlock = true
                }
                "starred_10" -> {
                    if (totalStarredCount >= 10) shouldUnlock = true
                }
                "shield_unlocked" -> {
                    if (prog.shieldsCount >= 3) shouldUnlock = true
                }
            }

            if (shouldUnlock) {
                repository.updateAchievementStatus(ach.id, true, todayStr)
                _celebrationMessage.emit("Achievement Unlocked! 🏆 ${ach.title}\n${ach.description}")
            }
        }
    }

    fun setGoalDifficulty(difficulty: String) {
        viewModelScope.launch {
            val goal = when (difficulty) {
                "Beginner" -> 5
                "Intermediate" -> 10
                "Advanced" -> 20
                else -> 10
            }
            val currentProg = repository.getUserProgress() ?: UserProgress()
            repository.insertUserProgress(
                currentProg.copy(
                    difficulty = difficulty,
                    currentGoalSentences = goal
                )
            )
            _importResult.emit(ImportResult.Success(0, "Daily goal set to $goal sentences ($difficulty)"))
        }
    }

    // --- Discover (Cloud Story Platform) Support Methods ---
    fun fetchDiscoverData(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                isDiscoverRefreshing.value = true
                firestoreRepository.clearPaginationCache()
            } else {
                isDiscoverLoading.value = true
            }
            discoverError.value = null
            try {
                // Fetch featured Pick of the Day
                val featuredResult = firestoreRepository.getFeaturedStories(limit = 3)
                _featuredStories.value = featuredResult

                // Fetch trending stories
                val trendingResult = firestoreRepository.getTrendingStories(limit = 5)
                _trendingStories.value = trendingResult

                // Fetch latest dynamic lists based on filters
                val keyword = discoverSearchQuery.value
                val category = selectedDiscoverCategory.value
                val language = selectedDiscoverLanguage.value

                val latestResult = when {
                    keyword.isNotEmpty() -> firestoreRepository.searchStories(keyword, limit = 10L, resetPage = true)
                    category.isNotEmpty() -> firestoreRepository.getStoriesByCategory(category, limit = 10L, resetPage = true)
                    language.isNotEmpty() -> firestoreRepository.getStoriesByLanguage(language, limit = 10L, resetPage = true)
                    else -> firestoreRepository.getLatestStories(limit = 10L, resetPage = true)
                }

                // Filter by difficulty selection
                val filteredResult = if (selectedDiscoverDifficulty.value.isNotEmpty()) {
                    latestResult.filter { it.difficulty.lowercase() == selectedDiscoverDifficulty.value.lowercase() }
                } else {
                    latestResult
                }

                _latestStories.value = filteredResult
                hasMoreLatestStories.value = latestResult.size >= 10
            } catch (e: Exception) {
                discoverError.value = e.localizedMessage ?: "Unknown discovery error"
            } finally {
                isDiscoverLoading.value = false
                isDiscoverRefreshing.value = false
            }
        }
    }

    fun loadMoreLatestStories() {
        if (!hasMoreLatestStories.value || isDiscoverLoading.value || isDiscoverRefreshing.value) return
        viewModelScope.launch {
            try {
                val keyword = discoverSearchQuery.value
                val category = selectedDiscoverCategory.value
                val language = selectedDiscoverLanguage.value

                val nextPageResult = when {
                    keyword.isNotEmpty() -> firestoreRepository.searchStories(keyword, limit = 10L, resetPage = false)
                    category.isNotEmpty() -> firestoreRepository.getStoriesByCategory(category, limit = 10L, resetPage = false)
                    language.isNotEmpty() -> firestoreRepository.getStoriesByLanguage(language, limit = 10L, resetPage = false)
                    else -> firestoreRepository.getLatestStories(limit = 10L, resetPage = false)
                }

                val filtered = if (selectedDiscoverDifficulty.value.isNotEmpty()) {
                    nextPageResult.filter { it.difficulty.lowercase() == selectedDiscoverDifficulty.value.lowercase() }
                } else {
                    nextPageResult
                }

                if (filtered.isNotEmpty()) {
                    _latestStories.value = _latestStories.value + filtered
                }
                hasMoreLatestStories.value = nextPageResult.size >= 10
            } catch (e: Exception) {
                // Silent paging exception handle
            }
        }
    }

    fun selectStoryCategory(category: String) {
        val current = selectedDiscoverCategory.value
        selectedDiscoverCategory.value = if (current == category) "" else category
        selectedDiscoverLanguage.value = "" // Mutual exclusive filters
        fetchDiscoverData()
    }

    fun selectStoryLanguage(language: String) {
        val current = selectedDiscoverLanguage.value
        selectedDiscoverLanguage.value = if (current == language) "" else language
        selectedDiscoverCategory.value = "" // Mutual exclusive filters
        fetchDiscoverData()
    }

    fun selectStoryDifficulty(difficulty: String) {
        val current = selectedDiscoverDifficulty.value
        selectedDiscoverDifficulty.value = if (current == difficulty) "" else difficulty
        fetchDiscoverData()
    }

    fun clearDiscoverFilters() {
        selectedDiscoverCategory.value = ""
        selectedDiscoverLanguage.value = ""
        selectedDiscoverDifficulty.value = ""
        discoverSearchQuery.value = ""
        fetchDiscoverData()
    }

    fun loadStoryFromFirestoreIntoReader(firestoreStory: FirestoreStory, onNavigateToReader: () -> Unit) {
        viewModelScope.launch {
            // 1. Locate or create a local category named "Cloud Downloads"
            val localGroups = repository.allGroups.first()
            var cloudGroup = localGroups.find { it.name == "Cloud Explorer" }
            val groupId = if (cloudGroup == null) {
                val newGroupId = repository.insertGroup(StoryGroup(name = "Cloud Explorer"))
                newGroupId.toInt()
            } else {
                cloudGroup.id
            }

            // 2. Check if a Story with this title already exists in this group to avoid absolute duplicates
            val groupStories = repository.allStories.first().filter { it.groupId == groupId }
            val existingStory = groupStories.find { it.title == firestoreStory.title }
            
            val storyId = if (existingStory == null) {
                // Insert a new local Story
                val newId = repository.insertStory(
                    Story(groupId = groupId, title = firestoreStory.title)
                )
                
                // Parse and insert aligned bilingual sentences
                val parsedSentences = parseBilingualLines(firestoreStory.content, newId.toInt())
                if (parsedSentences.isNotEmpty()) {
                    repository.insertAll(parsedSentences)
                }
                newId.toInt()
            } else {
                existingStory.id
            }

            // 3. Select this story so the Reader screen opens it immediately
            val selectedStory = Story(id = storyId, groupId = groupId, title = firestoreStory.title)
            selectStory(selectedStory)
            
            // 4. Trigger navigation callback to reader screen!
            onNavigateToReader()
        }
    }

    sealed class ImportResult {
        data class Success(val count: Int, val message: String) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    private var textToSpeechInstance: android.speech.tts.TextToSpeech? = null
    private var isTtsReady = false

    val wordHighlightManager = WordHighlightManager()
    private var karaokeTtsManagerInstance: KaraokeTtsManager? = null
    val currentlySpokenText = MutableStateFlow<String?>(null)

    val karaokeTtsManager: KaraokeTtsManager
        get() {
            if (karaokeTtsManagerInstance == null) {
                karaokeTtsManagerInstance = KaraokeTtsManager(getApplication())
            }
            return karaokeTtsManagerInstance!!
        }

    fun speakText(text: String) {
        if (textToSpeechInstance == null) {
            textToSpeechInstance = android.speech.tts.TextToSpeech(getApplication()) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    performSpeak(text)
                }
            }
        } else {
            performSpeak(text)
        }
    }

    fun stopSpeak() {
        textToSpeechInstance?.stop()
        wordHighlightManager.reset()
        currentlySpokenText.value = null
    }

    fun playKaraoke(sentences: List<SentenceCard>) {
        if (expKaraokeTts.value) {
            karaokeTtsManager.play(sentences, speechRate.value, speechPitch.value)
        }
    }

    fun stopKaraoke() {
        karaokeTtsManagerInstance?.stop()
    }

    private fun performSpeak(text: String) {
        val tts = textToSpeechInstance ?: return
        if (!isTtsReady) return
        try {
            currentlySpokenText.value = text
            if (expWordFocus.value) {
                wordHighlightManager.setupListener(tts, text, speechRate.value)
            } else {
                wordHighlightManager.reset()
                tts.setOnUtteranceProgressListener(null)
            }

            // Target Language detection based on character analysis
            val selectedLocale = when {
                text.any { it in '\u0400'..'\u04FF' } -> java.util.Locale("ru") // Cyrillic
                text.any { it in '\u3040'..'\u30FF' || it in '\u4E00'..'\u9FFF' } -> java.util.Locale.JAPANESE
                text.any { it in '\u00C0'..'\u00FF' && (text.contains("ñ") || text.contains("¿") || text.contains("í") || text.contains("á") || text.contains("é") || text.contains("ó") || text.contains("ú")) } -> java.util.Locale("es") // Spanish
                else -> {
                    // Try targeting user preferences
                    val p = userProgress.value
                    if (p != null) {
                        when (p.targetLanguage.lowercase(java.util.Locale.getDefault())) {
                            "spanish", "es" -> java.util.Locale("es")
                            "russian", "ru" -> java.util.Locale("ru")
                            "german", "de" -> java.util.Locale("de")
                            "french", "fr" -> java.util.Locale("fr")
                            "italian", "it" -> java.util.Locale("it")
                            "japanese", "ja" -> java.util.Locale("ja")
                            "chinese", "zh" -> java.util.Locale("zh")
                            else -> java.util.Locale("es")
                        }
                    } else {
                        java.util.Locale("es")
                    }
                }
            }
            tts.language = selectedLocale
            tts.setPitch(speechPitch.value)
            tts.setSpeechRate(speechRate.value)
            tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "sentence_id_active")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeechInstance?.stop()
        textToSpeechInstance?.shutdown()
        wordHighlightManager.reset()
        karaokeTtsManagerInstance?.shutdown()
        currentlySpokenText.value = null
    }
}
