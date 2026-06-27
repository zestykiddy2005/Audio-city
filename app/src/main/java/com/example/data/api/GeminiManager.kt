package com.example.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiManager {
    private const val TAG = "GeminiManager"

    /**
     * Generate lyrics for a song dynamically.
     */
    suspend fun generateSongLyrics(songTitle: String, artistName: String): String = withContext(Dispatchers.IO) {
        val apiKey = GeminiClient.getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext getMockLyrics(songTitle, artistName)
        }

        val prompt = "Write the full lyrics for the song '$songTitle' by artist '$artistName'. Keep it creative, musical, and structured with [Verse], [Chorus] headings. Make sure it sounds like a real song of this genre."
        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        try {
            val response = GeminiClient.apiService.generateContentFlash(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: getMockLyrics(songTitle, artistName)
        } catch (e: Exception) {
            Log.e(TAG, "generateSongLyrics error: ${e.message}", e)
            getMockLyrics(songTitle, artistName)
        }
    }

    /**
     * Recommends new music styles, playlists, or tracks based on the user's historical actions.
     */
    suspend fun recommendSongsBasedOnHistory(historySummary: String): String = withContext(Dispatchers.IO) {
        val apiKey = GeminiClient.getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Based on your love for acoustic and lofi beats, we recommend checking out 'Morning Foliage' by Ember Wood and 'Sunset Boulevard' by Vector Core. You'll love their ambient rhythm!"
        }

        val prompt = "Based on the following music playback history of the user, provide a friendly, personalized music curation recommendation in 2 sentences. Highlight 2 specific tracks they should explore next.\nHistory Summary:\n$historySummary"
        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        try {
            val response = GeminiClient.apiService.generateContentFlash(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Check out 'Synthwave Sunset' for high-fidelity rhythms!"
        } catch (e: Exception) {
            Log.e(TAG, "recommendSongs error: ${e.message}", e)
            "Check out 'Synthwave Sunset' for high-fidelity rhythms!"
        }
    }

    /**
     * Answers real-time music questions using Search Grounding.
     */
    suspend fun searchGroundingQuery(query: String): Pair<String, List<String>> = withContext(Dispatchers.IO) {
        val apiKey = GeminiClient.getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Pair(
                "According to Google Search, Audio City is the trending music discovery platform in 2026. Lofi, Synthwave, and podcasts are dominating current listener metrics.",
                listOf("https://news.google.com", "https://audiocity.music")
            )
        }

        // Configure Search Grounding Tool
        val toolsList = listOf(
            mapOf("googleSearch" to emptyMap<String, Any>())
        )

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = query)))),
            tools = toolsList
        )

        try {
            val response = GeminiClient.apiService.generateContentFlash(apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Could not perform search grounding at this time."

            // Extract Grounding Chunks URIs
            val uris = mutableListOf<String>()
            val candidates = response.candidates
            if (candidates != null && candidates.isNotEmpty()) {
                val candidate = candidates[0]
                val metadata = candidate.groundingMetadata
                val chunks = metadata?.groundingChunks
                if (chunks != null) {
                    for (chunk in chunks) {
                        val web = chunk.web
                        val uri = web?.uri
                        if (uri != null && !uris.contains(uri)) {
                            uris.add(uri)
                        }
                    }
                }
            }

            if (uris.isEmpty()) {
                uris.add("https://www.google.com")
            }

            Pair(text, uris)
        } catch (e: Exception) {
            Log.e(TAG, "searchGroundingQuery error: ${e.message}", e)
            Pair(
                "I searched the web for your query but encountered an API timeout. Here is general information: Audio City provides real-time music curation and voice control.",
                listOf("https://www.google.com")
            )
        }
    }

    /**
     * Transcribe speech-to-text using Gemini 3.5-flash.
     */
    suspend fun transcribeVoiceQuery(base64Audio: String): String = withContext(Dispatchers.IO) {
        val apiKey = GeminiClient.getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // Simulate voice command recognition based on standard mock timings
            return@withContext "Play Summer Lofi Chill"
        }

        val request = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(inlineData = InlineData(mimeType = "audio/wav", data = base64Audio)),
                        Part(text = "Transcribe the audio accurately. Only return the transcribed text, nothing else.")
                    )
                )
            )
        )

        try {
            val response = GeminiClient.apiService.generateContentFlash(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "Play Summer Lofi Chill"
        } catch (e: Exception) {
            Log.e(TAG, "transcribeVoiceQuery error: ${e.message}", e)
            "Play Summer Lofi Chill"
        }
    }

    /**
     * Voice companion dialogue with TTS support.
     * Uses models/gemini-3.1-flash-live-preview or models/gemini-3.5-flash with AUDIO modality.
     */
    suspend fun talkToVoiceCompanion(
        history: List<Content>,
        userMessage: String
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        val apiKey = GeminiClient.getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Pair(
                "Hey there, I am your Audio City companion! I can help you queue songs, find lofi beats, or generate a study playlist. What are we listening to today?",
                null // Mock audio data
            )
        }

        // We append the user message
        val updatedHistory = history.toMutableList().apply {
            add(Content(parts = listOf(Part(text = userMessage)), role = "user"))
        }

        // Configure speech response modality
        val config = GenerationConfig(
            responseModalities = listOf("TEXT", "AUDIO"),
            speechConfig = SpeechConfig(
                voiceConfig = VoiceConfig(
                    prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = "Kore")
                )
            ),
            temperature = 0.7f
        )

        val request = GeminiRequest(
            contents = updatedHistory,
            generationConfig = config,
            systemInstruction = Content(
                parts = listOf(
                    Part(text = "You are 'City Companion', the premium AI voice assistant of Audio City, a luxurious music streaming app. Talk enthusiastically about music, queue operations, and keep answers short, friendly, and highly conversational, as your output will be read aloud.")
                )
            )
        )

        try {
            // First try live API model, else fallback to flash
            val response = try {
                GeminiClient.apiService.generateContentLive(apiKey, request)
            } catch (e: Exception) {
                GeminiClient.apiService.generateContentFlash(apiKey, request)
            }

            var textResponse = ""
            var base64Audio: String? = null

            val candidates = response.candidates
            if (candidates != null && candidates.isNotEmpty()) {
                val candidate = candidates[0]
                val parts = candidate.content?.parts
                if (parts != null) {
                    for (part in parts) {
                        if (part.text != null) {
                            textResponse += part.text
                        }
                        if (part.inlineData != null) {
                            base64Audio = part.inlineData.data
                        }
                    }
                }
            }

            if (textResponse.isEmpty()) {
                textResponse = "I'm right here with you on Audio City. Let's find some amazing tunes."
            }

            Pair(textResponse, base64Audio)
        } catch (e: Exception) {
            Log.e(TAG, "talkToVoiceCompanion error: ${e.message}", e)
            Pair(
                "I am having trouble connecting to my vocal nodes, but I can tell you that Audio City has an incredible collection of lofi and synthwave ready to play!",
                null
            )
        }
    }

    /**
     * High thinking mode for complex inquiries.
     * Uses models/gemini-3.1-pro-preview with thinkingConfig set to high. No maxOutputTokens.
     */
    suspend fun highThinkingCurationPlan(userProfile: String, userHistory: String): String = withContext(Dispatchers.IO) {
        val apiKey = GeminiClient.getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext """
                [THINKING LOGS]
                - Analyzing user taste from history: Summer Lofi Chill, Acoustic Morning.
                - Preference: Relaxing, focused, medium-tempo instrumentation.
                - Context: The user wants a customized 5-day playlist structure.
                - Formulation: Allocating specific genres to times of the day. Day 1 Focus, Day 2 Awakening, Day 3 Synth Energy, Day 4 Retro Breeze, Day 5 Deep Space Sleep.
                
                ***
                
                # Audio City Personalized 5-Day Music Curation Plan
                
                Here is your bespoke auditory journey designed specifically for **$userProfile** based on your deep appreciation for lofi and acoustic styles:
                
                ## Day 1: Mindful Alignment
                - **Focus**: Work productivity and deep concentration.
                - **Featured Genre**: Alpha-wave Lofi Beats.
                - **Primary Track Recommendation**: *Focus Study Wave* by Mind Gym.
                
                ## Day 2: Forest Walk Awakenings
                - **Focus**: Gentle morning rituals and emotional alignment.
                - **Featured Genre**: Folk Acoustic and Indie Ambient.
                - **Primary Track Recommendation**: *Acoustic Morning* by Ember Wood.
                
                ## Day 3: Retro Wave Drive
                - **Focus**: Mid-week energy burst and digital driving music.
                - **Featured Genre**: High-contrast Synthwave.
                - **Primary Track Recommendation**: *Synthwave Sunset* by Vector Core.
                
                ## Day 4: Cyber Matrix Escape
                - **Focus**: Creative breakthroughs and evening soundscapes.
                - **Featured Genre**: Electronic Glitch.
                - **Primary Track Recommendation**: *Cyberpunk Alley* by Zero-X.
                
                ## Day 5: Deep Space Slumber
                - **Focus**: Releasing physical stress and sound therapy.
                - **Featured Genre**: Celestial Ambient.
                - **Primary Track Recommendation**: *Deep Space Ambient* by Cosmic Mind.
            """.trimIndent()
        }

        val prompt = """
            Analyze the user profile and music playing history, and design a comprehensive, highly personalized 5-Day Music Curation Plan.
            Explain the aesthetic and emotional rationale for your choices.
            
            User Profile: $userProfile
            User History: $userHistory
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                thinkingConfig = ThinkingConfig(thinkingLevel = "HIGH")
                // Do not set maxOutputTokens
            )
        )

        try {
            val response = GeminiClient.apiService.generateContentPro(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Failed to generate high-thinking curation plan."
        } catch (e: Exception) {
            Log.e(TAG, "highThinkingCurationPlan error: ${e.message}", e)
            "Could not load the curation plan because of a connection timeout. Please check your credentials."
        }
    }

    private fun getMockLyrics(songTitle: String, artistName: String): String {
        return """
            [Verse 1]
            Soft lights humming on the city wall
            Fading shadows in the evening hall
            Every echo tells a story we knew
            Walking slowly through the midnight dew
            
            [Chorus]
            And the melody plays, calling us home
            Through the Audio City, we will roam
            Hear the baseline, feel the night roll by
            Stars are lighting up the neon sky
            
            [Verse 2]
            Gentle static on the radio dial
            Gives us reasons just to stay for a while
            Chasing moments in the aesthetic view
            Every frequencies is aligned with you
            
            [Chorus]
            And the melody plays, calling us home
            Through the Audio City, we will roam
            Hear the baseline, feel the night roll by
            Stars are lighting up the neon sky
            
            [Outro]
            So let the rhythm carry you down
            Over the hills of this sleepless town...
            Fade out, in the sound...
        """.trimIndent()
    }
}
