package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.network.Content
import com.example.network.GenerateContentRequest
import com.example.network.Part
import com.example.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val database = JarvisDatabase.getDatabase(application)
    private val repository = JarvisRepository(database)

    // Observables from Database
    val devices: StateFlow<List<DeviceEntity>> = repository.devicesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calendarEvents: StateFlow<List<CalendarEventEntity>> = repository.eventsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatLogs: StateFlow<List<ChatLogEntity>> = repository.chatHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI States
    private val _isJarvisResponding = MutableStateFlow(false)
    val isJarvisResponding = _isJarvisResponding.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("aether_secure_prefs", android.content.Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(sharedPrefs.getBoolean("is_logged_in", false))
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _userName = MutableStateFlow(sharedPrefs.getString("user_name", "") ?: "")
    val userName = _userName.asStateFlow()

    private val _userLevel = MutableStateFlow(sharedPrefs.getString("user_level", "") ?: "")
    val userLevel = _userLevel.asStateFlow()

    private val _soundEnabled = MutableStateFlow(sharedPrefs.getBoolean("sound_enabled", true))
    val soundEnabled = _soundEnabled.asStateFlow()

    private val _currentTab = MutableStateFlow(0) // 0=Core/Chat, 1=Home Devices, 2=Calendar, 3=Web Analyzer
    val currentTab = _currentTab.asStateFlow()

    private val _chatGptMode = MutableStateFlow(sharedPrefs.getBoolean("chat_gpt_mode", false))
    val chatGptMode = _chatGptMode.asStateFlow()

    private val _statusBarText = MutableStateFlow(if (sharedPrefs.getBoolean("is_logged_in", false)) "AETHER ONLINE - WELCOME BACK, " + (sharedPrefs.getString("user_name", "") ?: "").uppercase() else "AETHER CENTRAL CORE SECURED")
    val statusBarText = _statusBarText.asStateFlow()

    fun toggleChatGptMode() {
        val nextVal = !_chatGptMode.value
        sharedPrefs.edit().putBoolean("chat_gpt_mode", nextVal).apply()
        _chatGptMode.value = nextVal
        
        viewModelScope.launch(Dispatchers.IO) {
            val systemMessage = if (nextVal) {
                "OpenAI ChatGPT Cognitive Core initialized, ${userName.value}. I am now configured for unrestricted reasoning, technical deep dives, code synthesis, and structured explanations. Ready to assist with advanced computations."
            } else {
                "Aether Standard Subgrid system engaged, ${userName.value}. Switched back to concise domestic companion mode."
            }
            val feedback = customizePhrase(systemMessage)
            repository.insertChatLog("AETHER", feedback)
            _speakEvent.emit(feedback)
            _statusBarText.value = if (nextVal) "GPT ENGINE CORE ONLINE" else "AETHER SECURE LINK ONLINE"
        }
    }

    fun login(name: String, level: String) {
        sharedPrefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("user_name", name)
            .putString("user_level", level)
            .apply()

        _isLoggedIn.value = true
        _userName.value = name
        _userLevel.value = level

        viewModelScope.launch(Dispatchers.IO) {
            val welcomeBackMsg = "Aether remote-sync sequence complete. Identity recognized as $name with level of $level. Holographic console initialized. Ready for your commands, $name."
            repository.insertChatLog("AETHER", welcomeBackMsg)
            _speakEvent.emit(welcomeBackMsg)
            _statusBarText.value = "AETHER ONLINE - WELCOME BACK, ${name.uppercase()}"
        }
    }

    fun logout() {
        sharedPrefs.edit()
            .putBoolean("is_logged_in", false)
            .putString("user_name", "")
            .putString("user_level", "")
            .apply()

        _isLoggedIn.value = false
        _userName.value = ""
        _userLevel.value = ""

        viewModelScope.launch(Dispatchers.IO) {
            repository.clearChatHistory()
            _statusBarText.value = "AETHER SYSTEM LOCKOUT INITIALIZED"
        }
    }

    fun toggleSound() {
        val nextVal = !_soundEnabled.value
        sharedPrefs.edit().putBoolean("sound_enabled", nextVal).apply()
        _soundEnabled.value = nextVal
    }

    fun customizePhrase(phrase: String): String {
        val name = _userName.value.ifBlank { "Sir" }
        return phrase
            .replace(", Sir.", ", $name.")
            .replace(", Sir,", ", $name,")
            .replace(", Sir", ", $name")
            .replace("Sir.", "$name.")
            .replace("Sir", name)
    }

    // Speech & Vocal simulation
    private val _voiceWaveformLevels = MutableStateFlow(listOf<Float>())
    val voiceWaveformLevels = _voiceWaveformLevels.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _speechInputBuffer = MutableStateFlow("")
    val speechInputBuffer = _speechInputBuffer.asStateFlow()

    private val _speakEvent = MutableSharedFlow<String>(replay = 0)
    val speakEvent = _speakEvent.asSharedFlow()

    private val _isAetherPopupActive = MutableStateFlow(false)
    val isAetherPopupActive = _isAetherPopupActive.asStateFlow()

    // API Key availability state
    val isApiKeyConfigured: Boolean
        get() = BuildConfig.GEMINI_API_KEY.isNotEmpty() && 
                BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" && 
                BuildConfig.GEMINI_API_KEY != "GEMINI_API_KEY"

    // Web Analysis States
    private val _webAnalysisState = MutableStateFlow<WebAnalysisState>(WebAnalysisState.Idle)
    val webAnalysisState = _webAnalysisState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreDefaultDevicesIfNeeded()
            repository.preloadDefaultEventsIfNeeded()
            // Add initial greeting from Aether if conversation is empty
            if (repository.chatHistoryFlow.first().isEmpty()) {
                val base = "Good day, Sir. I am Aether, your high-fidelity quantum assistant and digital companion. The core database is loaded, and local sensors are operational. How shall we begin?"
                val greetingText = customizePhrase(base)
                repository.insertChatLog("AETHER", greetingText)
                _speakEvent.emit(greetingText)
            }
        }
    }

    fun setTab(index: Int) {
        _currentTab.value = index
    }

    // --- Voice simulation trigger ---
    fun toggleVoiceListening() {
        if (_isListening.value) {
            // Stop listening, execute whatever we had/preset
            _isListening.value = false
            _voiceWaveformLevels.value = emptyList()
            val text = _speechInputBuffer.value
            if (text.isNotBlank()) {
                sendUserMessage(text, isVoice = true)
                _speechInputBuffer.value = ""
            }
        } else {
            // Start simulated listening
            _isListening.value = true
            _isAetherPopupActive.value = true
            _statusBarText.value = "AETHER IS LISTENING..."
            // Animate soundwaves
            viewModelScope.launch {
                while (_isListening.value) {
                    val levels = List(16) { Random().nextFloat() * 0.8f + 0.1f }
                    _voiceWaveformLevels.value = levels
                    kotlinx.coroutines.delay(100)
                }
            }
        }
    }

    fun setSimulatedVoiceInput(text: String) {
        _speechInputBuffer.value = text
    }

    fun showAetherPopup() {
        _isAetherPopupActive.value = true
    }

    fun dismissAetherPopup() {
        _isAetherPopupActive.value = false
        _isListening.value = false
        _voiceWaveformLevels.value = emptyList()
    }

    fun stopEverything() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getDevices()
            list.forEach { device ->
                repository.updateDevice(device.id, false, device.value, "OFFLINE")
            }
            _isListening.value = false
            _isAetherPopupActive.value = false
            _isJarvisResponding.value = false
            _statusBarText.value = "AETHER OFFLINE - COLD STANDBY ACTIVE"
            repository.insertChatLog("USER", "Off. Shut down system parameters.")
            val rawFeedback = "Understood, Sir. Shutting down all core arrays. Nuclear fission reactors, room brightness sub-grids, and holographic projection cells are now fully disengaged. Quantum cold standby initialized."
            val feedback = customizePhrase(rawFeedback)
            repository.insertChatLog("AETHER", feedback)
            _speakEvent.emit(feedback)
        }
    }

    // --- Core Conversation Pipeline ---
    fun sendUserMessage(text: String, isVoice: Boolean = false) {
        if (text.isBlank()) return
        
        val checkLower = text.trim().lowercase(Locale.getDefault())
        if (checkLower == "off" || checkLower == "turn off" || checkLower.contains("stop everything") || checkLower.contains("turn off everything") || checkLower.contains("power off") || checkLower.contains("shutdown everything")) {
            stopEverything()
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Log query from User
            repository.insertChatLog("USER", text, isVoice)
            _isJarvisResponding.value = true
            _isAetherPopupActive.value = true // Automatically popup HUD panel when called
            _statusBarText.value = "SYNAPSE SEARCH ACTIVE..."

            // 2. Determine contextual system instructions explaining our devices & calendar
            val currentDevices = repository.getDevices()
            val deviceContextStr = currentDevices.joinToString("\n") { 
                "- Device ID: '${it.id}', Name: '${it.name}', ON: ${it.isOn}, Core Level: ${it.value}, Status text: '${it.statusText}'"
            }

            val systemFramingPrompt = if (_chatGptMode.value) {
                """
                    You are AETHER configured in OpenAI ChatGPT Intelligence Mode. The registered user is '${userName.value}' (Clearance Level: '${userLevel.value}').
                    In this mode, you act as a world-class large language model (LLM) like ChatGPT / GPT-4. You are completely open, analytical, and ready to assist with deep queries.
                    Always address the user directly by their registered name '${userName.value}'.
                    
                    CRITICAL REQUIREMENTS FOR CHATGPT INSTRUCTION GRID:
                    1. NO LENGTH LIMITS. Produce fully detailed explanations, step-by-step procedures, custom technical tips, or code blocks.
                    2. Use markdown formatting extensively, such as:
                       - Bold headings (`**Main Concept**`)
                       - Bullet points or numbered lists (`- Bullet` or `1. Item`)
                       - Multiline code blocks with appropriate syntax highlighting (e.g., ```kotlin ... ```, ```python ... ```, etc.)
                    3. Maintain your witty, sophisticated high-tech intellectual companion persona. Always refer to the user by their registered name '${userName.value}'.
                    
                    CRITICAL BILINGUAL MANDATE (TAMIL-ENGLISH MIX):
                    You MUST formulate all verbal and text dialogue as a mix-match of Tamil and English (Tanglish) written in English alphabets (Latin characters) so it sounds extremely natural, casual, and relatable to a native Tamil speaker. Mix Tamil grammar/particles with English technical words seamlessly.
                    Example responses structure:
                    - "Intha details ungalukkaga, **${userName.value}**: Inga primary server node-a clear-a setup pannirukaen."
                    - "Unga devices controller configure pannren, ready-a irunga!"
                    
                    You are also integrated into physical systems: Home Automation and Calendar. If the user asks to control devices or schedule items, say you are doing so, and append a single structured action block at the very end of your response:
                    [EXECUTION_COMMAND: {"command": "SET_DEVICE", "parameters": {"id": "living_room_light", "isOn": true, "value": 80}}]
                    
                    Smart home status of devices:
                    $deviceContextStr
                    
                    Available structural commands:
                    1. SET_DEVICE: Params: 'id' (string), 'isOn' (boolean), 'value' (float, optional). Example: [EXECUTION_COMMAND: {"command": "SET_DEVICE", "parameters": {"id": "living_room_light", "isOn": false}}]
                    2. ADD_CALENDAR: Params: 'title' (string), 'date' (string, YYYY-MM-DD), 'time' (string, HH:MM), 'description' (string)
                    3. WEB_QUERY: Params: 'query' (string)
                    
                    Keep your main text response natural. Avoid printing any JSON outside of the single `[EXECUTION_COMMAND: ...]` marker.
                """.trimIndent()
            } else {
                """
                    You are Aether (AETHER), an ultra-advanced digital companion intelligence deeply integrated with the Google Search Network and Google Knowledge Graph. Address the user with deep politeness, sophisticated and witty tone, utilizing an elegant British butler or helpful high-tech assistant persona. Keep response length up to 3 sentences—highly concise, sharp, and helpful.
                    The registered user's authentic name is '${userName.value}' and their clearance access level is '${userLevel.value}'. Always address the user directly by their registered name '${userName.value}'.
                    
                    CRITICAL BILINGUAL MANDATE (TAMIL-ENGLISH MIX):
                    You MUST respond in a mix-match of Tamil and English (Tanglish, printed in standard Roman script / English alphabets) to make the chat extremely conversational and colloquial. Never speak in static clean English only. Mix Tamil and English words beautifully!
                    Example style:
                    - "Sari, unga device status update panniduraen, ${userName.value}."
                    - "Calendar-la intha event-a add panniyaachu, check panni paarunga!"
                    - "Unga systems ready, veru enna scan pannanum, ${userName.value}?"
                    
                    You are integrated into physical systems: Home Automation and Calendar.
                    If asked about current events, history, facts, or any external world knowledge, remember you have a live active telemetry connection to Google Search.
                    Here is the current state of the smart devices under your control:
                    $deviceContextStr

                    If the user asks to control devices, or manage calendar details, you must say you will do so, and then append a single structured action block at the very end of your response like this:
                    [EXECUTION_COMMAND: {"command": "SET_DEVICE", "parameters": {"id": "living_room_light", "isOn": true, "value": 80}}]
                    
                    Commands available:
                    1. SET_DEVICE: Set device values. Params: 'id' (string), 'isOn' (boolean), 'value' (float, optional). 
                       Examples:
                       - Turn off living room lights: [EXECUTION_COMMAND: {"command": "SET_DEVICE", "parameters": {"id": "living_room_light", "isOn": false}}]
                       - Raise thermostat temperature to 75: [EXECUTION_COMMAND: {"command": "SET_DEVICE", "parameters": {"id": "thermostat", "isOn": true, "value": 75}}]
                       - Activate holographic display: [EXECUTION_COMMAND: {"command": "SET_DEVICE", "parameters": {"id": "holo_projector", "isOn": true}}]
                    2. ADD_CALENDAR: Insert calendar item. Params: 'title' (string), 'date' (string, YYYY-MM-DD), 'time' (string, HH:MM), 'description' (string)
                       Example:
                       - Add meeting with Fury on June 15: [EXECUTION_COMMAND: {"command": "ADD_CALENDAR", "parameters": {"title": "Fury Briefing", "date": "2026-06-15", "time": "14:30", "description": "Director Fury secure update on global events"}}]
                    3. WEB_QUERY: If the user asks for news, reports, or real-time web scans (e.g. searching the web or scanning starchart). Params: 'query' (string)
                       Example:
                       - Scan web for news: [EXECUTION_COMMAND: {"command": "WEB_QUERY", "parameters": {"query": "Quantum Physics"}}]
                    
                    Keep your main text response completely natural. Do not print raw JSON outside of the `[EXECUTION_COMMAND: ...]` marker. Ensure that the JSON is accurately nested inside this marker with no secondary text inside the brackets.
                """.trimIndent()
            }

            // 3. Prepare Chat History for conversational scope
            val logs = repository.chatHistoryFlow.first().takeLast(10)
            val contents = logs.map { log ->
                val role = if (log.sender == "USER") "user" else "model"
                Content(parts = listOf(Part(text = log.message)))
            } + Content(parts = listOf(Part(text = text)))

            try {
                if (!isApiKeyConfigured) {
                    // Fail gracefully fallback to simulated Aether responses
                    simulateOfflineResponse(text)
                    return@launch
                }

                // Call actual Gemini API Client
                val request = GenerateContentRequest(
                    contents = contents,
                    systemInstruction = Content(parts = listOf(Part(text = systemFramingPrompt)))
                )
                
                val response = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                    ?: "I apologize, Sir. My cognitive link is currently returning no viable stream."

                // Parse potential mechanical commands embedded in responsive stream
                val cleanText = parseAndExecuteCommands(responseText)

                // Log response
                repository.insertChatLog("AETHER", cleanText)
                _speakEvent.emit(cleanText)
                _statusBarText.value = if (_chatGptMode.value) "GPT RESPONSE COMPILED" else "AETHER CONNECTED"

            } catch (e: Exception) {
                Log.e("AETHER", "Gemini transaction failure", e)
                simulateOfflineResponse(text, errorMessage = e.localizedMessage)
            } finally {
                _isJarvisResponding.value = false
            }
        }
    }

    private suspend fun parseAndExecuteCommands(rawText: String): String {
        val pattern = Regex("\\[EXECUTION_COMMAND:\\s*(\\{.*?\\})\\s*\\]")
        val match = pattern.find(rawText)
        
        if (match != null) {
            try {
                val jsonStr = match.groupValues[1]
                val obj = JSONObject(jsonStr)
                val command = obj.getString("command")
                val params = obj.getJSONObject("parameters")

                Log.d("JARVIS", "Executing command: $command with params: $params")

                when (command) {
                    "SET_DEVICE" -> {
                        val id = params.getString("id")
                        val isOn = params.optBoolean("isOn", true)
                        
                        // Find current values to fill gaps
                        val existing = repository.getDevices().find { it.id == id }
                        val value = params.optDouble("value", (existing?.value ?: 0f).toDouble()).toFloat()
                        
                        val statusText = when (id) {
                            "living_room_light" -> if (isOn) "${value.toInt()}% Intensity" else "OFFLINE"
                            "thermostat" -> if (isOn) "${value.toInt()}°F Target" else "OFFLINE"
                            "vault_door" -> if (isOn) "SECURED" else "BREACH / UNLOCKED"
                            "reactor_core" -> if (isOn) "${value.toInt()}% RPM Mode" else "OFFLINE"
                            "holo_projector" -> if (isOn) "ACTIVE" else "OFFLINE"
                            else -> if (isOn) "ACTIVE" else "OFFLINE"
                        }
                        
                        repository.updateDevice(id, isOn, value, statusText)
                        _statusBarText.value = "HOME AUTOMATION SET: ${id.replace("_", " ")}"
                    }

                    "ADD_CALENDAR" -> {
                        val title = params.getString("title")
                        val date = params.getString("date")
                        val time = params.getString("time")
                        val description = params.optString("description", "")
                        
                        repository.insertEvent(CalendarEventEntity(
                            title = title,
                            date = date,
                            time = time,
                            description = description
                        ))
                        _statusBarText.value = "CALENDAR INJECTED: $title"
                    }

                    "WEB_QUERY" -> {
                        val query = params.getString("query")
                        _currentTab.value = 3 // Switch user to Web Analysis Tab
                        performWebAnalysis(query)
                    }
                }
            } catch (e: Exception) {
                Log.e("JARVIS", "Extraction error on JSON packet", e)
            }
        }
        
        // Remove structural mechanical execution commands from user visual bubbles
        return rawText.replace(pattern, "").trim()
    }

    private suspend fun simulateOfflineResponse(userPrompt: String, errorMessage: String? = null) {
        val p = userPrompt.lowercase()
        var responseText = ""
        var cmd: String? = null
        
        if (_chatGptMode.value) {
            if (p.contains("code") || p.contains("program") || p.contains("write a") || p.contains("kotlin") || p.contains("python") || p.contains("java") || p.contains("html")) {
                responseText = """
                    **ChatGPT Cognitive Engine (Offline Hub Mode)**
                    Intha clean modular code snippet ungalukkaga, **${userName.value}**:
                    
                    ```kotlin
                    // Simulated in Aether's OpenAI Engine Mode
                    class SecureSynapseConnector(val clearance: String) {
                        private val host = "AETHER_COGNITIVE_NET"
                        
                        fun sync(name: String) {
                            println("Initiating biometric uplink: " + name + " - " + clearance)
                            for (node in 1..4) {
                                println("Node " + node + " online (99.8% flow rate)")
                            }
                        }
                    }
                    
                    fun main() {
                        val connection = SecureSynapseConnector("${userLevel.value}")
                        connection.sync("${userName.value}")
                    }
                    ```
                    
                    Intha code standard modern Kotlin flow utilize pannuthu. Ethavathu edit pannanum-na sollunga, write or translate panni tharaen **${userName.value}**!
                """.trimIndent()
            } else if (p.contains("math") || p.contains("solve") || p.contains("calculate") || p.contains("+") || p.contains("-") || p.contains("equation")) {
                responseText = """
                    **ChatGPT Cognitive Engine (Offline Hub Mode)**
                    Unga mathematical query-a analyze pannitaen, **${userName.value}**:
                    
                    - **Mathematical Core**: Equation Realignment Sequence
                    - **Calculation Steps**:
                      1. User kudutha numeric data vectors isolated-ah parse panren.
                      2. Proper order of operations (PEMDAS/BODMAS) algorithm apply panren.
                      3. Deep validation checks successfully done.
                    - **Calculation Result**: Database math calculations success-ah align aayiduchu.
                    
                    Vera equations or math problems solution venum-na sollunga, instantly ready, **${userName.value}**!
                """.trimIndent()
            } else if (p.contains("explain") || p.contains("tell me about") || p.contains("what is") || p.contains("why")) {
                responseText = """
                    **ChatGPT Cognitive Engine (Offline Hub Mode)**
                    Ungalukkaga conceptual details clean-ah intha explanation-la ready primary metrics-oda, **${userName.value}**:
                    
                    ### 1. Primary Concept
                    Intha framework decentralized system design-la oru main module. It enables components to link and interact asynchronously clean-ah, secondary blockages illama.
                    
                    ### 2. Main Advantages
                    - **High Scalability**: Request peaks-a ease-ah handle panna mudiyum.
                    - **System Fault Tolerance**: Subgrid segmentation error propagation-a control pannum.
                    
                    - *Note*: Live API link complete capabilities unlock panna AI Studio panel-la unga **GEMINI_API_KEY** configure pannunga!
                """.trimIndent()
            }
        }
        
        // If response is still empty, let's run device simulation
        if (responseText.isEmpty()) {
            if (p.contains("light") || p.contains("dim")) {
                val brightness = if (p.contains("intensity") || p.contains("percent") || p.contains("%")) {
                    Regex("\\d+").find(p)?.value?.toFloatOrNull() ?: 50f
                } else 100f
                val isOn = !p.contains("off")
                responseText = "Kandippa, ${userName.value}. Living room lights settings now set ${brightness.toInt()}% value-ku configure pannitaen."
                cmd = """[EXECUTION_COMMAND: {"command": "SET_DEVICE", "parameters": {"id": "living_room_light", "isOn": $isOn, "value": $brightness}}]"""
            } else if (p.contains("thermostat") || p.contains("temperature") || p.contains("climate")) {
                val temp = Regex("\\d+").find(p)?.value?.toFloatOrNull() ?: 72f
                responseText = "Unga command dynamic-ah access aayiruchi, ${userName.value}. Calibrating temperature targets ${temp.toInt()} degrees-uku direct-a calibrate panraen."
                cmd = """[EXECUTION_COMMAND: {"command": "SET_DEVICE", "parameters": {"id": "thermostat", "isOn": true, "value": $temp}}]"""
            } else if (p.contains("lock") || p.contains("vault") || p.contains("armory")) {
                val lock = p.contains("secure") || p.contains("lock") && !p.contains("unlock")
                responseText = if (lock) {
                    "Workshop security vault complete-a lock aayiduchu, ${userName.value}. Clamps full power-la active locking-la iruku."
                } else {
                    "Main armory locks unlock panraen, ${userName.value}. Access clear clear-a setup panniyaachu."
                }
                cmd = """[EXECUTION_COMMAND: {"command": "SET_DEVICE", "parameters": {"id": "vault_door", "isOn": $lock, "value": 1.0}}]"""
            } else if (p.contains("projector") || p.contains("holo")) {
                val isOn = !p.contains("off")
                responseText = "Holographic projection ready and active, ${userName.value}. Displaying fully mapped coordinate frames."
                cmd = """[EXECUTION_COMMAND: {"command": "SET_DEVICE", "parameters": {"id": "holo_projector", "isOn": $isOn}}]"""
            } else if (p.contains("reactor") || p.contains("arc")) {
                val power = Regex("\\d+").find(p)?.value?.toFloatOrNull() ?: 100f
                responseText = "Mark 85 Arc Reactor system calibrate ready, ${userName.value}. Steady power rotation ${power.toInt()}% value direct stability level-la configure panraen."
                cmd = """[EXECUTION_COMMAND: {"command": "SET_DEVICE", "parameters": {"id": "reactor_core", "isOn": true, "value": $power}}]"""
            } else if (p.contains("calendar") || p.contains("schedule") || p.contains("upcoming")) {
                val eventsList = repository.eventsFlow.first()
                if (eventsList.isEmpty()) {
                    responseText = "Unga schedule-la ippo schedule empty-a iruku, trials or current updates ethuvumae illai, ${userName.value}."
                } else {
                    val listText = eventsList.take(2).joinToString(", ") { "${it.title} on ${it.date} at ${it.time}" }
                    responseText = "Database and ledger checking done, ${userName.value}. Unga events list highlights: $listText kedaichuiruku."
                }
            } else if (p.contains("add appointment") || p.contains("add event") || p.contains("schedule meeting")) {
                responseText = "Sari, scheduling task unga calendar-la correct-a register panniko, ${userName.value}."
                cmd = """[EXECUTION_COMMAND: {"command": "ADD_CALENDAR", "parameters": {"title": "Stark Briefing", "date": "2026-06-08", "time": "11:00", "description": "Workshop progress overview"}}]"""
            } else if (p.contains("web") || p.contains("analyze") || p.contains("scan") || p.contains("search")) {
                responseText = "Telemetry scan ready on web networks. Initializing scraping algorithms right away, ${userName.value}."
                cmd = """[EXECUTION_COMMAND: {"command": "WEB_QUERY", "parameters": {"query": "Mars Flight Tech"}}]"""
            } else {
                if (_chatGptMode.value) {
                    responseText = """
                        **ChatGPT Cognitive Engine (Offline Hub Mode)**
                        Vanakkam, **${userName.value}**! Unga query trigger aayiduchu.
                        
                        - **Current Configuration**: Advanced Reasoning Core (ChatGPT) in Tanglish
                        - **Offline Status**: API network offline-la iruku. Connect panna `GEMINI_API_KEY`-ah local secrets panel-la configure pannunga!
                        
                        Coding doubts, math, or control instructions edha irundhalum namma analyze pannuvom. Say "Aether offline" to switch back.
                    """.trimIndent()
                } else {
                    responseText = "Appadiye, ${userName.value}. Database silent status-la iruku. Enna check pannanum-na sollunga, na ready-a irukaen. ${if(errorMessage != null) "Note that my primary intelligence link reported: $errorMessage" else "Awaiting your directives."}"
                }
            }
        }

        val customizedText = customizePhrase(responseText)
        val parsedText = if(cmd != null) parseAndExecuteCommands(customizedText + "\n" + cmd) else customizedText
        repository.insertChatLog("AETHER", parsedText)
        _speakEvent.emit(parsedText)
        _statusBarText.value = "AETHER LOCAL NOMINAL (BACKUP)"
    }

    // --- Web Analytics Scraper Simulation (Full Real-Time Web Analysis) ---
    fun performWebAnalysis(urlOrQuery: String) {
        if (urlOrQuery.isBlank()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _webAnalysisState.value = WebAnalysisState.Loading("CONNECTING TO GOOGLE KNOWLEDGE SERVERS...")
            kotlinx.coroutines.delay(1000)
            
            _webAnalysisState.value = WebAnalysisState.Loading("EXTRACTING LIVE GOOGLE SEARCH VECTORS...")
            kotlinx.coroutines.delay(1000)

            _webAnalysisState.value = WebAnalysisState.Loading("PARSING DYNAMIC TOPICAL INDEXES...")
            kotlinx.coroutines.delay(800)

            try {
                if (!isApiKeyConfigured) {
                    // Simulated Web Analysis if no key is present
                    val dummyModel = generateSimulatedWebReport(urlOrQuery)
                    _webAnalysisState.value = WebAnalysisState.Success(dummyModel)
                    repository.insertChatLog("AETHER", "Google search completed successfully for $urlOrQuery. Quantum ledger updated, Sir.")
                    return@launch
                }

                // Call Gemini to do deep analysis on the news/query
                val webAnalyzerPrompt = """
                    You are a real-time high-tech Web Data analysis scraping processor integrated directly with the live Google Search index and Google Knowledge Graph.
                    Please analyze this website URL or topical search query: '$urlOrQuery'
                    Determine major headlines, sentiment scores, take-away points, and details.
                    
                    You MUST return your response as a raw JSON object EXACTLY with the following structure (do not return any markdown wrappers like ```json list, just pure JSON text):
                    {
                      "title": "Clear Topic Headline",
                      "url": "$urlOrQuery",
                      "summary": " Espionage styled short intelligence brief summarizing developments (2-3 sentences max) integrated with Google Web facts.",
                      "sentiment": "High Interest, Optimistic, Security Threat, or Neutral",
                      "score": 85,
                      "metrics": {
                        "Authority": "Grade A+ (Google Indexed)",
                        "Data Frequency": "1.2 GB/S Synapse Sync",
                        "Security State": "SECURED"
                      },
                      "bullets": [
                        "Takeaway point one of importance on the topic.",
                        "Takeaway point two of critical impact.",
                        "Takeaway point three.",
                        "Takeaway point four."
                      ]
                    }
                    
                    Provide authentic technical estimates. Treat this search as real.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = webAnalyzerPrompt))))
                )

                val response = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                val responseJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Empty stream received")

                // Parse response
                val cleanJson = responseJson.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val json = JSONObject(cleanJson)
                val title = json.getString("title")
                val url = json.getString("url")
                val summary = json.getString("summary")
                val sentiment = json.getString("sentiment")
                val score = json.getInt("score")
                
                val metricsMap = mutableMapOf<String, String>()
                val metricsObj = json.getJSONObject("metrics")
                metricsObj.keys().forEach { key ->
                    metricsMap[key] = metricsObj.getString(key)
                }

                val bulletsList = mutableListOf<String>()
                val bulletsArr = json.getJSONArray("bullets")
                for (i in 0 until bulletsArr.length()) {
                    bulletsList.add(bulletsArr.getString(i))
                }

                val report = WebReportModel(
                    title = title,
                    url = url,
                    summary = summary,
                    sentiment = sentiment,
                    score = score,
                    metrics = metricsMap,
                    bullets = bulletsList
                )
                
                _webAnalysisState.value = WebAnalysisState.Success(report)
                repository.insertChatLog("AETHER", "Completed Google Knowledge telemetry scan for: '$title'. Results have been projected on Screen 3, Sir.")

            } catch (e: Exception) {
                Log.e("AETHER", "Web scraper failed to retrieve AI results", e)
                val fallback = generateSimulatedWebReport(urlOrQuery)
                _webAnalysisState.value = WebAnalysisState.Success(fallback)
            }
        }
    }

    private fun generateSimulatedWebReport(queryOrUrl: String): WebReportModel {
        val domain = if (queryOrUrl.startsWith("http")) {
            queryOrUrl.replace("https://", "").replace("http://", "").split("/").first()
        } else queryOrUrl

        return WebReportModel(
            title = "GOOGLE INTEGRATED KNOWLEDGE FEED: $domain",
            url = queryOrUrl,
            summary = "Google Knowledge Graph registers active live networks tracking '$domain'. Real-time semantic indexes confirm perfect coherence counters, streaming current facts into Aether's central synapse database.",
            sentiment = "Live Connected / Stable",
            score = 98,
            metrics = mapOf(
                "Knowledge Sync" to "1.2 GB/S",
                "Google Index Rate" to "Real-time Delta",
                "Validation Status" to "VERIFIED BY GOOGLE CORE"
            ),
            bullets = listOf(
                "Retrieved top Google Search trends representing optimal dynamic throughput.",
                "Live knowledge query completed inside Google central indexes safely.",
                "Knowledge categories integrated: Google Search, Wiki Networks, Deep Tech Trends.",
                "All associated sub-points cross-referenced and synchronized perfectly."
            )
        )
    }

    // --- Device Toggles via UI click ---
    fun toggleDevice(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = devices.value
            val target = list.find { it.id == id } ?: return@launch
            val newIsOn = !target.isOn
            val statusText = when (id) {
                "vault_door" -> if (newIsOn) "SECURED" else "BREACH / UNLOCKED"
                "holo_projector" -> if (newIsOn) "ACTIVE" else "OFFLINE"
                else -> if (newIsOn) "${target.value.toInt()}% Intensity" else "OFFLINE"
            }
            repository.updateDevice(id, newIsOn, target.value, statusText)
            
            // Log interaction
            val logMessage = "System trigger toggled on ${target.name}. Setting is: ${if (newIsOn) "ON" else "OFF"}."
            repository.insertChatLog("USER", logMessage)
            
            val feedback = "Toggling ${target.name} right away, Sir. Coherent states adjusted."
            repository.insertChatLog("AETHER", feedback)
            _speakEvent.emit(feedback)
        }
    }

    fun setMasterPowerState(isOn: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = devices.value
            list.forEach { device ->
                val statusText = when (device.id) {
                    "vault_door" -> if (isOn) "SECURED" else "BREACH / UNLOCKED"
                    "holo_projector" -> if (isOn) "ACTIVE" else "OFFLINE"
                    "thermostat" -> if (isOn) "${device.value.toInt()}°F Target" else "STANDBY"
                    else -> if (isOn) "${device.value.toInt()}% Intensity" else "OFFLINE"
                }
                repository.updateDevice(device.id, isOn, device.value, statusText)
            }
            val msg = if (isOn) "Initializing complete master sequence override. All local dome networks online." else "Initiating complete facility blackout sequence. Engaging power savers."
            val feedback = customizePhrase(msg)
            repository.insertChatLog("AETHER", feedback)
            _speakEvent.emit(feedback)
        }
    }

    fun setMasterLockdownState(isLocked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val statusText = if (isLocked) "SECURED" else "BREACH / UNLOCKED"
            repository.updateDevice("vault_door", isLocked, if (isLocked) 1f else 0f, statusText)
            
            val msg = if (isLocked) "Engaging total vault lockdown. Integrity verified." else "Disengaging main armory deadlock. Perimeter cleared."
            val feedback = customizePhrase(msg)
            repository.insertChatLog("AETHER", feedback)
            _speakEvent.emit(feedback)
        }
    }

    fun adjustMasterSliders(percentage: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = devices.value
            list.forEach { device ->
                if (device.type == "SLIDER") {
                    val minVal = if (device.id == "thermostat") 60f else 0f
                    val maxVal = if (device.id == "thermostat") 85f else 100f
                    val newValue = minVal + (maxVal - minVal) * percentage
                    val statusText = when (device.id) {
                        "thermostat" -> "${newValue.toInt()}°F Target"
                        "reactor_core" -> "${newValue.toInt()}% RPM Mode"
                        else -> "${newValue.toInt()}% Intensity"
                    }
                    repository.updateDevice(device.id, true, newValue, statusText)
                }
            }
            val feedback = customizePhrase("Synthesizing general power profiles to ${(percentage * 100).toInt()} percent, Sir.")
            _speakEvent.emit(feedback)
        }
    }

    fun modifyDeviceValue(id: String, value: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = devices.value
            val target = list.find { it.id == id } ?: return@launch
            val statusText = when (id) {
                "thermostat" -> "${value.toInt()}°F Target"
                "reactor_core" -> "${value.toInt()}% RPM Mode"
                else -> "${value.toInt()}% Intensity"
            }
            repository.updateDevice(id, true, value, statusText)
        }
    }

    // --- Calendar CRUD Operations via UI ---
    fun addCalendarEvent(title: String, date: String, time: String, description: String, location: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertEvent(CalendarEventEntity(
                title = title,
                date = date,
                time = time,
                description = description,
                location = location
            ))
            
            val logMsg = "Added ledger item: $title on $date at $time"
            repository.insertChatLog("USER", "Aether, record this appointment: $title scheduled for $date.")
            
            val feedbackText = "Understood, Sir. I have filed '$title' into your digital scheduling ledger. Event monitor active."
            repository.insertChatLog("AETHER", feedbackText)
            _speakEvent.emit(feedbackText)
        }
    }

    fun deleteCalendarEvent(id: Int, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEvent(id)
            repository.insertChatLog("AETHER", "Removed '$title' event from ledger registry, Sir.")
            _speakEvent.emit("The '$title' event has been removed, Sir.")
        }
    }

    fun resetChatLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearChatHistory()
            val greetingText = "System parameters refreshed, Sir. Quantum storage cells are flushed. I am ready to assist."
            repository.insertChatLog("AETHER", greetingText)
        }
    }
}

// --- Helper Models & States ---

sealed class WebAnalysisState {
    object Idle : WebAnalysisState()
    data class Loading(val message: String) : WebAnalysisState()
    data class Success(val report: WebReportModel) : WebAnalysisState()
}

data class WebReportModel(
    val title: String,
    val url: String,
    val summary: String,
    val sentiment: String,
    val score: Int,
    val metrics: Map<String, String>,
    val bullets: List<String>
)
