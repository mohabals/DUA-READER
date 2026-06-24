package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.remote.firebase.FirebaseManager
import com.example.model.FirestoreStory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.Locale

class FirestoreStoryRepository(private val context: Context) {
    private val TAG = "FirestoreStoryRepository"
    private val db: FirebaseFirestore? = null

    // List of last retrieved snapshots categorized by query key to support next-page sliding windows in Firestore
    private val lastSnapshots = mutableMapOf<String, DocumentSnapshot>()
    // Memory offset mapping for mock/simulated paginated pool
    private val lastOffsetMap = mutableMapOf<String, Int>()

    fun clearPaginationCache() {
        lastSnapshots.clear()
        lastOffsetMap.clear()
    }

    // High fidelity mock database containing 20 curated multilingual and multi-category stories for fallback/offline operations
    private val simulatedStories: List<FirestoreStory> by lazy {
        generateSimulatedStoriesPool()
    }

    suspend fun getLatestStories(
        limit: Long = 10,
        resetPage: Boolean = false
    ): List<FirestoreStory> = withContext(Dispatchers.IO) {
        val cacheKey = "latest"
        if (resetPage) {
            lastSnapshots.remove(cacheKey)
            lastOffsetMap.remove(cacheKey)
        }

        val firestoreDb = db
        if (firestoreDb != null) {
            try {
                var query = firestoreDb.collection("stories")
                    .whereEqualTo("published", true)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit)

                val lastSnapshot = lastSnapshots[cacheKey]
                if (lastSnapshot != null && !resetPage) {
                    query = query.startAfter(lastSnapshot)
                }

                val snapshot = query.get().await()
                if (!snapshot.isEmpty) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(FirestoreStory::class.java)?.copy(storyId = doc.id)
                    }
                    lastSnapshots[cacheKey] = snapshot.documents.last()
                    return@withContext list
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error logging latest stories from Firestore, running local pool: ${e.localizedMessage}")
            }
        }

        // --- Simulated Paginated Pool ---
        val offset = if (resetPage) 0 else (lastOffsetMap[cacheKey] ?: 0)
        val listResult = simulatedStories.sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit.toInt())

        val nextOffset = offset + listResult.size
        if (listResult.isNotEmpty()) {
            lastOffsetMap[cacheKey] = nextOffset
        }
        listResult
    }

    suspend fun getFeaturedStories(limit: Int = 3): List<FirestoreStory> = withContext(Dispatchers.IO) {
        val firestoreDb = db
        if (firestoreDb != null) {
            try {
                val snapshot = firestoreDb.collection("stories")
                    .whereEqualTo("published", true)
                    .whereEqualTo("featured", true)
                    .limit(limit.toLong())
                    .get()
                    .await()
                if (!snapshot.isEmpty) {
                    return@withContext snapshot.documents.mapNotNull { doc ->
                        doc.toObject(FirestoreStory::class.java)?.copy(storyId = doc.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting featured stories from Firestore: ${e.localizedMessage}")
            }
        }
        simulatedStories.filter { it.featured }.take(limit)
    }

    suspend fun getStoriesByCategory(
        category: String,
        limit: Long = 10,
        resetPage: Boolean = false
    ): List<FirestoreStory> = withContext(Dispatchers.IO) {
        val cacheKey = "cat_$category"
        if (resetPage) {
            lastSnapshots.remove(cacheKey)
            lastOffsetMap.remove(cacheKey)
        }

        val firestoreDb = db
        if (firestoreDb != null) {
            try {
                var query = firestoreDb.collection("stories")
                    .whereEqualTo("published", true)
                    .whereEqualTo("category", category)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit)

                val lastSnapshot = lastSnapshots[cacheKey]
                if (lastSnapshot != null && !resetPage) {
                    query = query.startAfter(lastSnapshot)
                }

                val snapshot = query.get().await()
                if (!snapshot.isEmpty) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(FirestoreStory::class.java)?.copy(storyId = doc.id)
                    }
                    lastSnapshots[cacheKey] = snapshot.documents.last()
                    return@withContext list
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching category from Firestore: ${e.localizedMessage}")
            }
        }

        val offset = if (resetPage) 0 else (lastOffsetMap[cacheKey] ?: 0)
        val listResult = simulatedStories
            .filter { it.category.lowercase() == category.lowercase() }
            .sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit.toInt())

        val nextOffset = offset + listResult.size
        if (listResult.isNotEmpty()) {
            lastOffsetMap[cacheKey] = nextOffset
        }
        listResult
    }

    suspend fun getStoriesByLanguage(
        language: String,
        limit: Long = 10,
        resetPage: Boolean = false
    ): List<FirestoreStory> = withContext(Dispatchers.IO) {
        val cacheKey = "lang_$language"
        if (resetPage) {
            lastSnapshots.remove(cacheKey)
            lastOffsetMap.remove(cacheKey)
        }

        val firestoreDb = db
        if (firestoreDb != null) {
            try {
                var query = firestoreDb.collection("stories")
                    .whereEqualTo("published", true)
                    .whereEqualTo("language", language)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit)

                val lastSnapshot = lastSnapshots[cacheKey]
                if (lastSnapshot != null && !resetPage) {
                    query = query.startAfter(lastSnapshot)
                }

                val snapshot = query.get().await()
                if (!snapshot.isEmpty) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(FirestoreStory::class.java)?.copy(storyId = doc.id)
                    }
                    lastSnapshots[cacheKey] = snapshot.documents.last()
                    return@withContext list
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching language from Firestore: ${e.localizedMessage}")
            }
        }

        val offset = if (resetPage) 0 else (lastOffsetMap[cacheKey] ?: 0)
        val listResult = simulatedStories
            .filter { it.language.lowercase() == language.lowercase() }
            .sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit.toInt())

        val nextOffset = offset + listResult.size
        if (listResult.isNotEmpty()) {
            lastOffsetMap[cacheKey] = nextOffset
        }
        listResult
    }

    suspend fun searchStories(
        keyword: String,
        limit: Long = 10,
        resetPage: Boolean = false
    ): List<FirestoreStory> = withContext(Dispatchers.IO) {
        val cacheKey = "search_$keyword"
        if (resetPage) {
            lastSnapshots.remove(cacheKey)
            lastOffsetMap.remove(cacheKey)
        }

        val queryWord = keyword.lowercase().trim()
        if (queryWord.isEmpty()) {
            return@withContext getLatestStories(limit, resetPage)
        }

        val firestoreDb = db
        if (firestoreDb != null) {
            try {
                // Cloud search logic: Standard tags query or full search
                var query = firestoreDb.collection("stories")
                    .whereEqualTo("published", true)
                    .whereArrayContains("tags", queryWord)
                    .limit(limit)

                val lastSnapshot = lastSnapshots[cacheKey]
                if (lastSnapshot != null && !resetPage) {
                    query = query.startAfter(lastSnapshot)
                }

                val snapshot = query.get().await()
                if (!snapshot.isEmpty) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(FirestoreStory::class.java)?.copy(storyId = doc.id)
                    }
                    lastSnapshots[cacheKey] = snapshot.documents.last()
                    return@withContext list
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching stories in Firestore: ${e.localizedMessage}")
            }
        }

        val offset = if (resetPage) 0 else (lastOffsetMap[cacheKey] ?: 0)
        val listResult = simulatedStories
            .filter {
                it.title.lowercase().contains(queryWord) ||
                it.description.lowercase().contains(queryWord) ||
                it.category.lowercase().contains(queryWord) ||
                it.tags.any { tag -> tag.lowercase().contains(queryWord) }
            }
            .sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit.toInt())

        val nextOffset = offset + listResult.size
        if (listResult.isNotEmpty()) {
            lastOffsetMap[cacheKey] = nextOffset
        }
        listResult
    }

    suspend fun getTrendingStories(limit: Int = 5): List<FirestoreStory> = withContext(Dispatchers.IO) {
        val firestoreDb = db
        if (firestoreDb != null) {
            try {
                val snapshot = firestoreDb.collection("stories")
                    .whereEqualTo("published", true)
                    .orderBy("trendingScore", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()
                if (!snapshot.isEmpty) {
                    return@withContext snapshot.documents.mapNotNull { doc ->
                        doc.toObject(FirestoreStory::class.java)?.copy(storyId = doc.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching trending: ${e.localizedMessage}")
            }
        }
        simulatedStories.sortedByDescending { it.trendingScore }.take(limit)
    }

    suspend fun getRecommendedStories(
        targetLang: String,
        difficulty: String,
        limit: Int = 5
    ): List<FirestoreStory> = withContext(Dispatchers.IO) {
        val firestoreDb = db
        if (firestoreDb != null) {
            try {
                val snapshot = firestoreDb.collection("stories")
                    .whereEqualTo("published", true)
                    .whereEqualTo("language", targetLang)
                    .whereEqualTo("difficulty", difficulty)
                    .limit(limit.toLong())
                    .get()
                    .await()
                if (!snapshot.isEmpty) {
                    return@withContext snapshot.documents.mapNotNull { doc ->
                        doc.toObject(FirestoreStory::class.java)?.copy(storyId = doc.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching recommended: ${e.localizedMessage}")
            }
        }
        simulatedStories
            .filter { it.language.lowercase() == targetLang.lowercase() && it.difficulty.lowercase() == difficulty.lowercase() }
            .take(limit)
            .ifEmpty { simulatedStories.take(limit) }
    }

    // Curated high-fidelity mock pool seeding
    private fun generateSimulatedStoriesPool(): List<FirestoreStory> {
        val categories = listOf("Daily Life", "Adventure", "History", "Mystery", "Science Fiction", "Technology")
        val languages = listOf("Spanish", "Russian", "Italian", "German", "French", "Japanese", "English")
        val difficulties = listOf("Beginner", "Elementary", "Intermediate", "Advanced")

        // Curated content map representing highly high-fidelity stories for each target language
        val contentVault = mapOf(
            "Spanish" to """
                Ayer fui al mercado principal de la ciudad. | Yesterday I went to the city's main market.
                Compré deliciosas manzanas rojas y plátanos frescos. | I bought delicious red apples and fresh bananas.
                El vendedor era un hombre muy amable del pueblo. | The seller was a very friendly village man.
                Hablamos sobre el clima cálido de esta semana. | We talked about warm climate of this week.
                Luego, tomé un café capuchino en la esquina. | Then, I had a capuchino coffee on the corner.
                El aroma del grano tostado era simplemente sensacional. | The aroma of roasted bean was simply sensational.
                Me encanta caminar tranquilamente por las calles por la tarde. | I love walking calmly in the afternoon.
                La vida en esta ciudad vieja es pacífica y hermosa. | Life in this old city is peaceful and beautiful.
            """.trimIndent(),
            "Russian" to """
                Сегодня прекрасный солнечный день в Санкт-Петербурге. | Today is a beautiful sunny day in St. Petersburg.
                Я решил прогуляться вдоль реки Невы. | I decided to take a walk along Neva river.
                Множество кораблей плыли по спокойной воде. | Many ships sailed on the calm water.
                В воздухе чувствовался свежий морской бриз. | A fresh sea breeze was felt in the air.
                В кафе у моста я заказал горячие блины со сметаной. | In a cafe near the bridge I ordered hot blinis with sour cream.
                Музыкант играл красивую классическую мелодию на скрипке. | A musician played beautiful classical melody on a violin.
                Прохожие улыбались и радовались прекрасному выходному дню. | Passersby smiled and enjoyed the wonderful weekend.
                Это один из лучших моментов за всю неделю. | This is one of the best moments compiled all week.
            """.trimIndent(),
            "Italian" to """
                Roma è una delle città più affascinanti del mondo intero. | Rome is one of the most fascinating cities of whole world.
                Camminando per le strade antiche, si respira la vera storia. | Walking through ancient streets, you breathe the real history.
                Ho preso un delizioso gelato alla vaniglia e pistacchio. | I had a delicious vanilla and pistachio gelato.
                La gente del posto si riunisce sempre nelle grandi piazze. | Local people always gather in large squares.
                Il sole tramontava gettando un colore caldo dorato sui monumenti. | The sun set throwing warm golden color on monuments.
                Il mio amico mi ha consigliato di visitare una cappella nascosta. | My friend advised me to visit a hidden chapel.
                L'arte e la cultura qui sono davvero indimenticabili. | Art and culture here are truly unforgettable.
                Spero di tornare molto presto in questo paese. | I hope to return very soon to this country.
            """.trimIndent(),
            "German" to """
                Der Schwarzwald ist bekannt für seine dichten, grünen Tannenwälder. | Black Forest is known for its dense, green pine forests.
                Ein schmaler Pfad führte uns tief in die unberührte Natur. | A narrow path led us deep into untouched nature.
                Man konnte das leise Rauschen eines Bergbachs in der Ferne hören. | One could hear soft rushing of a mountain stream in distance.
                Die Luft war kühl, klar und roch frisch nach Harz. | The air was cool, clear and smelled fresh of resin.
                Wir suchten einen sonnigen Holztisch, um eine Brotzeit zu machen. | We searched for a sunny wooden table to have a snack.
                Ein neugieriges Eichhörnchen beobachtete uns von einem Ast. | A curious squirrel watched us from a branch.
                Solche Momente der Ruhe geben dem Leben neue Energie. | Such moments of quietness get new energy to life.
            """.trimIndent(),
            "French" to """
                Le matin, j'aime acheter un croissant chaud à la boulangerie. | In the morning, I like to buy a warm croissant from the bakery.
                Le parfum du pain qui sort du four est merveilleux. | The scent of bread coming out of the oven is wonderful.
                Je marche le long de la Seine pour aller travailler. | I walk along Seine river to go to work.
                Les parisiens boivent leur espresso en terrasse. | Parisians drink their espresso on the terrace.
                L'ambiance de la ville est toujours inspirante et vivante. | The city atmosphere is always inspiring and alive.
            """.trimIndent(),
            "Japanese" to """
                京都の古い通りをゆっくりと歩くのが大好きです。 | I love walking slowly through ancient streets of Kyoto.
                春には美しい桜の花が咲き誇ります。 | In spring, beautiful cherry blossoms bloom magnificently.
                茶室で温かいお抹茶と和菓子をいただきました。 | I had warm matcha and traditional sweets at a tea house.
                庭園には小さな池があり、金魚が静かに泳いでいます。 | The garden features a small pond where goldfish swim quietly.
                本当に心が穏やかになる一日でした。 | It was truly a day that makes the mind calm.
            """.trimIndent(),
            "English" to """
                A light rain began to fall over the hills of Yorkshire. | Un leve orvallo comenzó a caer sobre las colinas de Yorkshire.
                The old brick cottage looked incredibly cozy by the fireside. | La vieja cabaña de ladrillo lucía increíblemente acogedora al fuego.
                We brewed a fresh pot of herbal chamomile tea. | Preparamos una tetera fresca de té de manzanilla.
                Sitting by the window, the world outside looked soft and silent. | Sentados junto a la ventana, el mundo de afuera parecía suave y silencioso.
            """.trimIndent()
        )

        val storyPool = mutableListOf<FirestoreStory>()
        
        // Add 1 explicit Featured story for premium discover landing hero
        storyPool.add(
            FirestoreStory(
                storyId = "featured_editorial_1",
                title = "Secrets of the Alhambra",
                description = "Uncover the hidden whispering gardens and architectural marvels of Moorish Granada.",
                content = contentVault["Spanish"] ?: "",
                language = "Spanish",
                category = "History",
                difficulty = "Intermediate",
                tags = listOf("history", "alhambra", "granada", "gardens", "travel"),
                coverImageUrl = "https://images.unsplash.com/photo-1596436889106-be35e843f974?w=800&auto=format&fit=crop",
                readingTimeMinutes = 8,
                wordCount = 120,
                createdAt = System.currentTimeMillis() - 1000 * 60 * 60 * 2, // 2 hrs ago
                updatedAt = System.currentTimeMillis(),
                published = true,
                featured = true,
                trendingScore = 0.98,
                readCount = 4200
            )
        )

        storyPool.add(
            FirestoreStory(
                storyId = "featured_editorial_2",
                title = "Silent Waters of the Neva",
                description = "A poetic journey through the historic canals of Petersburg during the White Nights.",
                content = contentVault["Russian"] ?: "",
                language = "Russian",
                category = "Daily Life",
                difficulty = "Beginner",
                tags = listOf("russian", "neva", "petersburg", "poetry", "nature"),
                coverImageUrl = "https://images.unsplash.com/photo-1548834925-e40f9fc53cc9?w=800&auto=format&fit=crop",
                readingTimeMinutes = 6,
                wordCount = 100,
                createdAt = System.currentTimeMillis() - 1000 * 60 * 60 * 5, // 5 hrs ago
                updatedAt = System.currentTimeMillis(),
                published = true,
                featured = true,
                trendingScore = 0.94,
                readCount = 1800
            )
        )

        // Seed 18 additional diverse stories
        val titles = listOf(
            "The Mountain Shelter", "Forgotten Clocktower", "Evening Coffee Ritual",
            "Midnight Express Train", "Voyage to Andromeda", "Shadows of Rome",
            "The Secret Bakery", "Legend of the Samurai", "The Whispering Fir Trees",
            "Rainy Day in Toulouse", "Neon Tokyo Trails", "Galactic Pioneers",
            "Tale of Two Cities", "Lost in Granada", "Enchanted Library",
            "Gothic Cathedral Echoes", "A Quiet Meadow Journey", "Cyberpunk Hacker Codes"
        )

        val descList = listOf(
            "A serene journey high up into Alpine winds and glowing wood logs.",
            "Time stands still in this mechanical core left behind a century ago.",
            "How coffee brewing connects cultures across warm busy street cafes.",
            "A mysterious passenger boards with a black suitcase under dim gaslights.",
            "A sci-fi research team detects strange signals from a distant solar system.",
            "Wandering through crumbling marble archways of the Roman Forum.",
            "Baked baguettes and buttery croissants coming together in rural France.",
            "An old scroll holds lessons about honor, discipline, and sword art.",
            "Wandering through pine-packed paths in deep beautiful Germany.",
            "Raindrops brushing against limestone shingles in Southern France.",
            "Luminescent projections reflecting on wet asphalt in Shinjuku alleys.",
            "Discovering uncharted gravity anomalies near Saturn's massive moons.",
            "How parallel paths cross on ancient cobblestone lanes over centuries.",
            "Vibrant tiles and guitar cords echoing from Andalusian balconies.",
            "Ancient dusty volumes bound in gold leaf whisper secrets on bookshelves.",
            "The monumental masonry humming with choirs under tall colorful glass arches.",
            "Laying down in emerald grass fields watching clouds flow lazily.",
            "A digital security expert intercepts encrypted data streams in neon skyscrapers."
        )

        for (i in 0 until 18) {
            val title = titles[i]
            val desc = descList[i]
            val cat = categories[i % categories.size]
            val lang = languages[i % languages.size]
            val diff = difficulties[i % difficulties.size]

            storyPool.add(
                FirestoreStory(
                    storyId = "simulated_story_${i + 1}",
                    title = title,
                    description = desc,
                    content = contentVault[lang] ?: "Hello World | Hola Mundo\nThis is a beautiful sentence. | Esta es una hermosa oración.",
                    language = lang,
                    category = cat,
                    difficulty = diff,
                    tags = listOf(cat.lowercase(), lang.lowercase(), "reading", "vocabulary", "learning"),
                    coverImageUrl = getUnsplashMockUrlForIndex(i),
                    readingTimeMinutes = 3 + (i * 2) % 10,
                    wordCount = 80 + i * 20,
                    createdAt = System.currentTimeMillis() - 1000L * 60 * 60 * 24 * (i + 1), // Past days
                    updatedAt = System.currentTimeMillis(),
                    published = true,
                    featured = false,
                    trendingScore = 0.2 + (i * 0.04),
                    readCount = 100L + (i * 120)
                )
            )
        }

        return storyPool
    }

    private fun getUnsplashMockUrlForIndex(idx: Int): String {
        val list = listOf(
            "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=500&auto=format&fit=crop", // Mountains
            "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&auto=format&fit=crop", // Sci-Fi
            "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=500&auto=format&fit=crop", // Coffee
            "https://images.unsplash.com/photo-1474487548417-781cb71495f3?w=500&auto=format&fit=crop", // Train
            "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=500&auto=format&fit=crop", // Space
            "https://images.unsplash.com/photo-1552832230-c0197dd311b5?w=500&auto=format&fit=crop", // Rome
            "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=500&auto=format&fit=crop", // Bread
            "https://images.unsplash.com/photo-1524413840807-0c3cb6fa808d?w=500&auto=format&fit=crop", // Kyoto
            "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=500&auto=format&fit=crop", // Forest
            "https://images.unsplash.com/photo-1511216113906-8f57bb83e776?w=500&auto=format&fit=crop", // Rain
            "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=500&auto=format&fit=crop", // Tokyo
            "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=500&auto=format&fit=crop", // Saturn
            "https://images.unsplash.com/photo-1448375240586-882707db888b?w=500&auto=format&fit=crop", // Paths
            "https://images.unsplash.com/photo-1539650116574-8efeb43e2750?w=500&auto=format&fit=crop", // Balcony
            "https://images.unsplash.com/photo-1521587760476-6c12a4b040da?w=500&auto=format&fit=crop", // Books
            "https://images.unsplash.com/photo-1547483238-f400e65ccd56?w=500&auto=format&fit=crop", // Glass
            "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=500&auto=format&fit=crop", // Meadow
            "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=500&auto=format&fit=crop"  // Cyber
        )
        return list[idx % list.size]
    }
}
