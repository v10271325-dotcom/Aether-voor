package com.example.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.speech.RecognizerIntent
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CalendarEventEntity
import com.example.data.DeviceEntity
import com.example.ui.theme.CyanHolo
import com.example.ui.theme.PurpleCyber
import com.example.ui.theme.EmeraldBio
import com.example.ui.theme.DarkBg
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.BorderColor
import com.example.ui.theme.TextMain
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun JarvisMainScreen(viewModel: JarvisViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val isJarvisResponding by viewModel.isJarvisResponding.collectAsState()
    val isAetherPopupActive by viewModel.isAetherPopupActive.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Text To Speech setup
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    
    DisposableEffect(context) {
        var ttsInstance: android.speech.tts.TextToSpeech? = null
        ttsInstance = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                ttsInstance?.language = java.util.Locale.US
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
        }
    }
    
    LaunchedEffect(viewModel.speakEvent) {
        viewModel.speakEvent.collect { text ->
            if (soundEnabled && tts != null) {
                // Parse out or clean from JSON / EXECUTION_COMMAND blocks
                val cleanSentence = text.replace(Regex("\\[EXECUTION_COMMAND:.*?\\]"), "").trim()
                if (cleanSentence.isNotEmpty()) {
                    tts?.speak(cleanSentence, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "aether_speech_id")
                }
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (!isLoggedIn) {
            AetherLoginPortal(viewModel = viewModel)
        } else {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg),
                containerColor = DarkBg,
                topBar = {
                    JarvisTopBar(viewModel = viewModel)
                },
                bottomBar = {
                    JarvisBottomBar(
                        currentTab = currentTab,
                        onTabSelected = { viewModel.setTab(it) }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(DarkBg)
                ) {
                    // Main content based on active tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                slideInHorizontally { width -> if (targetState > initialState) width else -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> if (targetState > initialState) -width else width } + fadeOut()
                            },
                            label = "tab_transition"
                        ) { targetTab ->
                            when (targetTab) {
                                0 -> CoreAssistantTab(viewModel)
                                1 -> HomeAutomationTab(viewModel)
                                2 -> CalendarLedgerTab(viewModel)
                                3 -> WebAnalyzerTab(viewModel)
                            }
                        }
                    }

                    // Command input panel (Always active at the bottom of content screen, above bottom navigation bar)
                    CommandInputPanel(viewModel = viewModel)
                }
            }

            // Aether Futuristic HUD Overlay (Pops up like a small screen/terminal!)
            AnimatedVisibility(
                visible = isAetherPopupActive,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier.fillMaxSize()
            ) {
                AetherPopupScreenOverlay(viewModel = viewModel)
            }
        }
    }
}

// --- Status Header Telemetry ---
@Composable
fun JarvisTopBar(viewModel: JarvisViewModel) {
    val statusBarText by viewModel.statusBarText.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val userName by viewModel.userName.collectAsState()
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBg)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Heartbeat indicator ring
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(CyanHolo.copy(alpha = alpha), CircleShape)
                        .border(1.dp, CyanHolo, CircleShape)
                )
                Text(
                    text = "AETHER SECURE LINK: ${userName.uppercase()}",
                    color = CyanHolo.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speaker audio feedback state
                IconButton(
                    onClick = { viewModel.toggleSound() },
                    modifier = Modifier.size(24.dp).testTag("top_speaker_btn")
                ) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Audio status",
                        tint = if (soundEnabled) CyanHolo else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Security Logout key
                IconButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.size(24.dp).testTag("top_logout_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Sign out",
                        tint = PurpleCyber,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = "98%",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Horizontal Glowing Bar Node
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            CyanHolo.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // System telemetry status
        Text(
            text = "TELEMETRY: $statusBarText",
            color = TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

// --- Stark Style Identity Alignment Portal ---
@Composable
fun AetherLoginPortal(viewModel: JarvisViewModel) {
    var name by remember { mutableStateOf("") }
    var passcode by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf("Sovereign Agent") }
    var showError by remember { mutableStateOf(false) }
    
    val levels = listOf("Sovereign Agent", "Chief Specialist", "Tactical Director", "Guest")
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Holographic biometric scans drawing behind
        val infiniteTransition = rememberInfiniteTransition(label = "portal_glow")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing)),
            label = "scan_rotation"
        )
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse_scale"
        )
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = CyanHolo.copy(alpha = 0.03f),
                radius = size.minDimension / 1.5f * pulseScale,
                center = center
            )
            drawCircle(
                color = PurpleCyber.copy(alpha = 0.02f),
                radius = size.minDimension / 2.2f * pulseScale,
                center = center
            )
        }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.92f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(listOf(CyanHolo, PurpleCyber)),
                    shape = RoundedCornerShape(24.dp)
                )
                .scale(pulseScale)
                .testTag("aether_login_panel")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Interactive Holographic Core Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .rotate(rotation),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = CyanHolo,
                            radius = size.minDimension / 4f,
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = PurpleCyber,
                            radius = size.minDimension / 2.5f,
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(15f, 15f), 0f
                                )
                            )
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Holographic Key",
                        tint = CyanHolo,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "AETHER INTEL SYSTEMS",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                
                Text(
                    text = "COGNITIVE ALIGNMENT TERMINAL",
                    color = CyanHolo,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // USER NAME Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; showError = false },
                    label = { 
                        Text(
                            "COGNITIVE ENTITY RECOGNITION (NAME)", 
                            fontSize = 10.sp, 
                            fontFamily = FontFamily.Monospace
                        ) 
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanHolo,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = CyanHolo,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = CyanHolo
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("login_name_input"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                
                Spacer(modifier = Modifier.height(14.dp))
                
                // PASSCODE Input
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it; showError = false },
                    label = { 
                        Text(
                            "SECURE PASSKEY CODE", 
                            fontSize = 10.sp, 
                            fontFamily = FontFamily.Monospace
                        ) 
                    },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleCyber,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = PurpleCyber,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = PurpleCyber
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("login_passcode_input"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (name.isNotBlank()) {
                            viewModel.login(name, selectedLevel)
                        } else {
                            showError = true
                        }
                    })
                )
                
                Spacer(modifier = Modifier.height(18.dp))
                
                // Clearance Chip Row Selection
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "SECURITY AUTOLOG CLEARANCE:",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        levels.forEach { level ->
                            val isSelected = selectedLevel == level
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isSelected) CyanHolo.copy(alpha = 0.15f) else SurfaceDark,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) CyanHolo else BorderColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedLevel = level }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("clearance_chip_$level")
                            ) {
                                Text(
                                    text = level.uppercase(),
                                    color = if (isSelected) CyanHolo else Color.White.copy(alpha = 0.6f),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                if (showError) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "IDENTITY REQUIRED TO CONSTRUCT QUANTUM SYNC",
                        color = Color(0xFFE57373),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(28.dp))
                
                // INITIALIZE CONNECTION Button
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.login(name, selectedLevel)
                        } else {
                            showError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanHolo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("initialize_network_btn")
                ) {
                    Text(
                        text = "INITIALIZE COGNITIVE CONNECTION",
                        color = DarkBg,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// --- TAB 0: Core J.A.R.V.I.S. Visualizer & Assistant ---
@Composable
fun CoreAssistantTab(viewModel: JarvisViewModel) {
    val chatLogs by viewModel.chatLogs.collectAsState()
    val isResponding by viewModel.isJarvisResponding.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val voiceWaveformLevels by viewModel.voiceWaveformLevels.collectAsState()
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Scroll to the latest chats dynamically
    LaunchedEffect(chatLogs.size) {
        if (chatLogs.isNotEmpty()) {
            listState.animateScrollToItem(chatLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Upper section: Holographic Reactor Core
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f),
            contentAlignment = Alignment.Center
        ) {
            HolographicReactorCore(
                isActive = isResponding || isListening,
                isResponding = isResponding
            )
        }

        // Cognitive Model Selector (Aether Concierge vs GPT Engine Core)
        CognitiveCoreToggle(viewModel)

        // Action Quick Assist chips
        QuickSectorsPanel(viewModel)

        // Lower Section: Dialog Terminal Stream
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.8f)
                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark.copy(alpha = 0.5f))
                .padding(12.dp)
        ) {
            if (chatLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "TERMINAL MATRIX OFFLINE. SIR, KINDLY SEND A PACKET PROMPT.",
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(chatLogs) { log ->
                        ChatTermBubble(log = log)
                    }
                }
            }

            // Overlay Loading
            if (isResponding) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = "JARVIS IS PROCESSING SYSTEM VARIABLES...",
                        color = CyanHolo,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
    }
}

// --- Glowing Holographic Reactor Core Representation ---
@Composable
fun HolographicReactorCore(isActive: Boolean, isResponding: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "core_anim")
    
    // Rotating primary angular velocity ring
    val rotAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 3000 else 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Secondary reverse spinning outer ring
    val rotAngleReverse by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reverse_rotation"
    )

    // Pulsing core scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (isResponding) 0.94f else 0.98f,
        targetValue = if (isResponding) 1.06f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isResponding) 400 else 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(190.dp)
            .scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        // Background Glow circular brushes
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CyanHolo.copy(alpha = if (isActive) 0.18f else 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension / 1.5f
                )
            )
        }

        // Inner glowing core elements
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(SurfaceDark.copy(alpha = 0.9f), CircleShape)
                .border(2.dp, CyanHolo.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "JARVIS",
                    color = CyanHolo,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.drawBehind {
                        // Small neon blur under text in custom canvas draw
                    }
                )
                Text(
                    text = if (isResponding) "DECIPHERING..." else if (isActive) "VOCAL MATRIX" else "SECURE RUN",
                    color = if (isResponding) PurpleCyber else CyanHolo.copy(alpha = 0.6f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Active spinning dashboard lines
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotAngle)
        ) {
            val strokeWidth = 2.dp.toPx()
            val radius = (size.minDimension / 2) - 16.dp.toPx()
            
            // Draw 4 segments of arcs around
            drawArc(
                color = CyanHolo,
                startAngle = 0f,
                sweepAngle = 75f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = CyanHolo.copy(alpha = 0.4f),
                startAngle = 100f,
                sweepAngle = 50f,
                useCenter = false,
                style = Stroke(width = strokeWidth - 1.dp.toPx(), cap = StrokeCap.Round)
            )

            drawArc(
                color = CyanHolo,
                startAngle = 180f,
                sweepAngle = 80f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = PurpleCyber,
                startAngle = 290f,
                sweepAngle = 40f,
                useCenter = false,
                style = Stroke(width = strokeWidth + 1.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Reversed spinning ticks
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotAngleReverse)
        ) {
            val strokeWidth = 1.dp.toPx()
            val radius = (size.minDimension / 2) - 4.dp.toPx()
            
            // Draw fine bounding ticks
            for (i in 0..360 step 15) {
                val angleRad = Math.toRadians(i.toDouble())
                val startX = center.x + (radius - 6.dp.toPx()) * Math.cos(angleRad).toFloat()
                val startY = center.y + (radius - 6.dp.toPx()) * Math.sin(angleRad).toFloat()
                val endX = center.x + radius * Math.cos(angleRad).toFloat()
                val endY = center.y + radius * Math.sin(angleRad).toFloat()
                
                drawLine(
                    color = if (i % 60 == 0) PurpleCyber.copy(alpha = 0.7f) else CyanHolo.copy(alpha = 0.3f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (i % 60 == 0) 2.dp.toPx() else strokeWidth
                )
            }
        }
    }
}

// --- Quick assist pre-configured terminal triggers ---
@Composable
fun QuickSectorsPanel(viewModel: JarvisViewModel) {
    val options = listOf(
        "Run Diagnostic Protocol" to "Jarvis, run standard diagnostics on all internal systems & status alerts.",
        "Dim living lights 40%" to "Jarvis, adjust room illumination settings. Turn lights on and configuration to 40% intensity.",
        "Secure the armory" to "Jarvis, lock down the workshop security vault immediately.",
        "Scan spaceflight trends" to "Jarvis, execute a real-time web search for updates on SpaceX and spaceflight exploration."
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .horizontalScrollStateEnabled(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (label, command) ->
            Box(
                modifier = Modifier
                    .background(SurfaceDark, RoundedCornerShape(24.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                    .clickable { 
                        viewModel.sendUserMessage(command, isVoice = false) 
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = label,
                    color = CyanHolo,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Simple Helper Extension to support horizontal scroll in a FlowRow / Row cleanly
@Composable
fun Modifier.horizontalScrollStateEnabled(): Modifier {
    return this.horizontalScroll(rememberScrollState())
}

// --- terminal output logs Chat bubble styles ---
@Composable
fun ChatTermBubble(log: com.example.data.ChatLogEntity) {
    val isUser = log.sender == "USER"
    val dtFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = dtFormat.format(Date(log.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    color = if (isUser) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFF0891B2).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) Color(0xFF334155) else CyanHolo.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp
                    )
                )
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUser) "COMMAND PACKET" else "J.A.R.V.I.S. COM-LINK",
                    color = if (isUser) PurpleCyber else CyanHolo,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (log.isVoice) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = CyanHolo.copy(alpha = 0.6f),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = formattedTime,
                        color = TextMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            RenderMarkdownMessage(message = log.message)
        }
    }
}

// --- Dynamic Markdown Renderer for OpenAI/ChatGPT Style Answers ---
@Composable
fun RenderMarkdownMessage(message: String) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val parts = remember(message) { message.split("```") }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                // This is an advanced markdown Code Block syntax layout
                val lines = part.trim().split("\n")
                val languageStr = lines.firstOrNull()?.trim() ?: "code"
                val codeBody = if (lines.size > 1) {
                    lines.drop(1).joinToString("\n")
                } else {
                    part
                }
                
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = languageStr.uppercase(),
                                color = CyanHolo,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Row(
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(codeBody))
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = CyanHolo.copy(alpha = 0.8f),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "COPY",
                                    color = CyanHolo.copy(alpha = 0.8f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Text(
                            text = codeBody,
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                // Formatting standard text sections including bold markers and itemized bullet lines
                val listLines = part.trim().split("\n")
                listLines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    
                    val trimmedLine = line.trim()
                    if (trimmedLine.startsWith("-") || trimmedLine.startsWith("*")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                color = CyanHolo,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = parseInlineBold(trimmedLine.substring(1).trim()),
                                color = TextMain,
                                fontSize = 13.sp,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 18.sp
                            )
                        }
                    } else if (trimmedLine.all { it.isDigit() || it == '.' || it == ' ' } && trimmedLine.contains('.') && trimmedLine.indexOf('.') > 0) {
                        val dotIndex = trimmedLine.indexOf('.')
                        val num = trimmedLine.substring(0, dotIndex + 1)
                        val content = trimmedLine.substring(dotIndex + 1).trim()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = num,
                                color = PurpleCyber,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = parseInlineBold(content),
                                color = TextMain,
                                fontSize = 13.sp,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 18.sp
                            )
                        }
                    } else {
                        Text(
                            text = parseInlineBold(line),
                            color = TextMain,
                            fontSize = 13.sp,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// Tool function to format bold strings (surrounded by **) in beautiful AnnotatedStrings
fun parseInlineBold(text: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    val parts = text.split("**")
    parts.forEachIndexed { i, portion ->
        if (i % 2 == 1) {
            builder.pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF1F5F9)
                )
            )
            builder.append(portion)
            builder.pop()
        } else {
            builder.append(portion)
        }
    }
    return builder.toAnnotatedString()
}

// Beautiful Cognitive Model Switcher Component
@Composable
fun CognitiveCoreToggle(viewModel: JarvisViewModel) {
    val chatGptMode by viewModel.chatGptMode.collectAsState()
    
    androidx.compose.material3.Card(
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.6f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("cognitive_core_mode_toggle_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Select Option 1: AETHER Concierge Node
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (!chatGptMode) CyanHolo.copy(alpha = 0.15f) else Color.Transparent)
                    .border(
                        width = if (!chatGptMode) 1.dp else 0.dp,
                        color = if (!chatGptMode) CyanHolo.copy(alpha = 0.6f) else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { if (chatGptMode) viewModel.toggleChatGptMode() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Aether Concierge",
                        tint = if (!chatGptMode) CyanHolo else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "AETHER CONCIERGE",
                        color = if (!chatGptMode) Color.White else TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Mode Select Option 2: GPT Cognitive Core
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (chatGptMode) PurpleCyber.copy(alpha = 0.15f) else Color.Transparent)
                    .border(
                        width = if (chatGptMode) 1.dp else 0.dp,
                        color = if (chatGptMode) PurpleCyber.copy(alpha = 0.6f) else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { if (!chatGptMode) viewModel.toggleChatGptMode() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "GPT Engine Core",
                        tint = if (chatGptMode) PurpleCyber else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "GPT ENGINE CORE",
                        color = if (chatGptMode) Color.White else TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


// --- TAB 1: Home Automation ---
@Composable
fun HomeAutomationTab(viewModel: JarvisViewModel) {
    val devices by viewModel.devices.collectAsState()
    val scope = rememberCoroutineScope()

    // Derived stats for telemetry dashboard
    val activeCount = devices.count { it.isOn }
    val totalCount = devices.size
    val thermostatDevice = devices.find { it.id == "thermostat" }
    val avgTemp = thermostatDevice?.value?.toInt() ?: 72
    val vaultActive = devices.find { it.id == "vault_door" }?.isOn == true
    
    // Master values slider state
    var masterSliderValue by remember { mutableStateOf(0.7f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // Holographic Core Dashboard HUD Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(CyanHolo.copy(alpha = 0.6f), PurpleCyber.copy(alpha = 0.2f))),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .testTag("home_telemetry_hud")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "AETHER DOMESTIC TELEMETRY HUD",
                                color = CyanHolo,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = "COGNITIVE MATRIX CENTRAL OVERVIEW",
                                color = TextMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (activeCount > 0) EmeraldBio.copy(alpha = 0.15f) else SurfaceDark,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp, 
                                    if (activeCount > 0) EmeraldBio.copy(alpha = 0.6f) else BorderColor,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (activeCount > 0) "ONLINE / ACTIVE" else "STANDBY / COLD",
                                color = if (activeCount > 0) EmeraldBio else TextMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Horizontal Metric Readouts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Metric 1: System Power Ratio
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("ONLINE CHANNELS", color = TextMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text("$activeCount / $totalCount UNIT", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { activeCount.toFloat() / totalCount },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                    color = CyanHolo,
                                    trackColor = BorderColor
                                )
                            }
                        }

                        // Metric 2: Average Dome Climate
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("CLIMATE RADIAL", color = TextMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text("$avgTemp°F TARGET", color = PurpleCyber, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(4.dp))
                                val ratio = (avgTemp - 60f).coerceIn(0f, 25f) / 25f
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                    color = PurpleCyber,
                                    trackColor = BorderColor
                                )
                            }
                        }

                        // Metric 3: Security Locked State
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("SECURITY MATRIX", color = TextMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text(
                                    text = if (vaultActive) "SECURED" else "BREACH / EN",
                                    color = if (vaultActive) EmeraldBio else Color(0xFFE57373),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (vaultActive) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Lock Status",
                                        tint = if (vaultActive) EmeraldBio else Color(0xFFE57373),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (vaultActive) "ACTIVE" else "WARN",
                                        color = if (vaultActive) EmeraldBio else Color(0xFFE57373),
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = BorderColor, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Advanced Realtime Controls: Master Power override switches and Master sliders
                    Text(
                        text = "MASTER COGNITIVE OVERRIDES",
                        color = TextMain,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Master Power Switch Trigger
                        Button(
                            onClick = { viewModel.setMasterPowerState(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanHolo.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, CyanHolo.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .testTag("master_power_on_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = "On", tint = CyanHolo, modifier = Modifier.size(13.dp))
                                Text("MASTER ON", color = CyanHolo, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Master Blackout Switch Trigger
                        Button(
                            onClick = { viewModel.setMasterPowerState(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                .testTag("master_power_off_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Off", tint = TextMuted, modifier = Modifier.size(13.dp))
                                Text("BLACKOUT", color = TextMain, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Master Security Toggle
                        Button(
                            onClick = { viewModel.setMasterLockdownState(!vaultActive) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (vaultActive) EmeraldBio.copy(alpha = 0.12f) else PurpleCyber.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .weight(1.2f)
                                .border(
                                    width = 1.dp, 
                                    color = if (vaultActive) EmeraldBio.copy(alpha = 0.4f) else PurpleCyber.copy(alpha = 0.4f), 
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .testTag("master_lockdown_toggle"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (vaultActive) Icons.Default.Security else Icons.Default.LockOpen, 
                                    contentDescription = "Shield", 
                                    tint = if (vaultActive) EmeraldBio else PurpleCyber, 
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (vaultActive) "UNSECURE" else "LOCKDOWN", 
                                    color = if (vaultActive) EmeraldBio else PurpleCyber, 
                                    fontSize = 9.sp, 
                                    fontFamily = FontFamily.Monospace, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Master Flux Control Slider (Sets all dimmable grids together)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GLOBAL WAVE FLUX CONTROL (ALL SLIDERS)",
                                color = TextMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${(masterSliderValue * 100).toInt()}% RATIO",
                                color = CyanHolo,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Slider(
                            value = masterSliderValue,
                            onValueChange = { 
                                masterSliderValue = it
                                viewModel.adjustMasterSliders(it)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = CyanHolo,
                                activeTrackColor = CyanHolo,
                                inactiveTrackColor = BorderColor
                             ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .testTag("master_slider_controller")
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "SUB-SYSTEM DOMESTIC AUTO-ARRAYS",
                color = PurpleCyber,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 2.dp, top = 6.dp)
            )
        }

        items(devices, key = { it.id }) { device ->
            DeviceBentoCard(
                device = device,
                onToggle = { viewModel.toggleDevice(device.id) },
                onSliderValueChange = { value -> viewModel.modifyDeviceValue(device.id, value) }
            )
        }
    }
}

@Composable
fun DeviceBentoCard(
    device: DeviceEntity,
    onToggle: () -> Unit,
    onSliderValueChange: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (device.isOn) SurfaceDark else Color(0xFF090D16))
            .border(
                width = 1.dp,
                color = if (device.isOn) CyanHolo.copy(alpha = 0.25f) else BorderColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (device.isOn) CyanHolo.copy(alpha = 0.15f) else Color.White.copy(
                                    alpha = 0.04f
                                ),
                                shape = CircleShape
                             ),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (device.id) {
                            "living_room_light" -> Icons.Default.Lightbulb
                            "thermostat" -> Icons.Default.Thermostat
                            "vault_door" -> Icons.Default.Lock
                            "reactor_core" -> Icons.Default.OfflineBolt
                            "holo_projector" -> Icons.Default.PersonalVideo
                            else -> Icons.Default.Settings
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = device.name,
                            tint = if (device.isOn) CyanHolo else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = device.name.uppercase(),
                            color = TextMain,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "SECTOR LOGIC: ${device.id.uppercase()}",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Interactive Switch toggle
                Switch(
                    checked = device.isOn,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = CyanHolo,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SurfaceDark,
                        uncheckedBorderColor = BorderColor
                     ),
                    modifier = Modifier.testTag("switch_${device.id}")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub Status Details Text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "STATUS PARAMETER:",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = device.statusText.uppercase(),
                    color = if (device.isOn) CyanHolo else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Slider customization for values (Persistent Sliders, Dimmed if device is off)
            if (device.type == "SLIDER") {
                Spacer(modifier = Modifier.height(10.dp))
                val minRange = if (device.id == "thermostat") 60f else 0f
                val maxRange = if (device.id == "thermostat") 85f else 100f
                val activeAlpha = if (device.isOn) 1f else 0.4f
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    if (!device.isOn) {
                        Text(
                            text = "DRAG SLIDER TO AUTO-ACTIVATE SYSTEM",
                            color = PurpleCyber.copy(alpha = 0.7f),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = minRange.toInt().toString(),
                            color = TextMuted.copy(alpha = activeAlpha),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Slider(
                            value = device.value,
                            onValueChange = { 
                                // Automatically powers on or modifies the device value
                                onSliderValueChange(it) 
                            },
                            valueRange = minRange..maxRange,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("slider_${device.id}"),
                            colors = SliderDefaults.colors(
                                thumbColor = if (device.isOn) CyanHolo else TextMuted,
                                activeTrackColor = if (device.isOn) CyanHolo else BorderColor,
                                inactiveTrackColor = BorderColor
                            )
                        )

                        Text(
                            text = maxRange.toInt().toString(),
                            color = TextMuted.copy(alpha = activeAlpha),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // High-Tech Option Presets Clickable Row for extreme customization
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = when (device.id) {
                    "living_room_light" -> listOf(20f to "DIM", 50f to "ECO", 100f to "MAX")
                    "thermostat" -> listOf(64f to "ECO", 72f to "COMF", 78f to "WARM")
                    "reactor_core" -> listOf(10f to "IDLE", 65f to "NOMIN", 100f to "MAX")
                    "vault_door" -> listOf(1f to "LOCK", 0f to "BYPASS", -1f to "")
                    "holo_projector" -> listOf(1f to "ACTIVATE", 0f to "POWER OFF", -1f to "")
                    else -> emptyList()
                }

                presets.forEach { pair ->
                    if (pair.second.isNotEmpty()) {
                        val isSelected = if (device.id == "vault_door" || device.id == "holo_projector") {
                            val presetIsOn = pair.first > 0f
                            device.isOn == presetIsOn
                        } else {
                            device.isOn && device.value == pair.first
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = if (isSelected) CyanHolo.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) CyanHolo else BorderColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (device.id == "vault_door" || device.id == "holo_projector") {
                                        val turnOn = pair.first > 0f
                                        if (device.isOn != turnOn) {
                                            onToggle()
                                        }
                                    } else {
                                        onSliderValueChange(pair.first)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pair.second,
                                color = if (isSelected) CyanHolo else TextMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}


// --- TAB 2: Calendar Ledger ---
@Composable
fun CalendarLedgerTab(viewModel: JarvisViewModel) {
    val events by viewModel.calendarEvents.collectAsState()
    
    // Bottom sheet dialog toggle states to insert new event
    var showAddForm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STARK APPOINTMENT LEDGER",
                color = PurpleCyber,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Button(
                onClick = { showAddForm = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PurpleCyber.copy(alpha = 0.15f),
                    contentColor = PurpleCyber
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PurpleCyber.copy(alpha = 0.5f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.testTag("add_event_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Inject log",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "INJECT",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO DIAGNOSTIC EXPERIMENTS SCHEDULED. LEDGER VACANT, SIR.",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(events, key = { it.id }) { item ->
                    EventRowLogItem(
                        event = item,
                        onDelete = { viewModel.deleteCalendarEvent(item.id, item.title) }
                    )
                }
            }
        }
    }

    if (showAddForm) {
        AddEventDialog(
            onDismiss = { showAddForm = false },
            onSave = { title, date, time, desc, loc ->
                viewModel.addCalendarEvent(title, date, time, desc, loc)
                showAddForm = false
            }
        )
    }
}

@Composable
fun EventRowLogItem(event: CalendarEventEntity, onDelete: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark)
            .border(1.dp, PurpleCyber.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title.uppercase(),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DATE: ${event.date}",
                            color = PurpleCyber,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(TextMuted, CircleShape)
                        )
                        Text(
                            text = "TIME: ${event.time} GST",
                            color = CyanHolo,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Delete event trigger
                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("delete_${event.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Purge event",
                        tint = Color.Red.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "LOCATION: " + event.location.uppercase(),
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = event.description,
                color = TextMain,
                fontSize = 12.sp,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 16.sp
            )
        }
    }
}

// Finished Custom Border stroke helper clean


@Composable
fun AddEventDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, date: String, time: String, desc: String, location: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("2026-06-07") }
    var time by remember { mutableStateOf("12:00") }
    var desc by remember { mutableStateOf("") }
    var loc by remember { mutableStateOf("Stark Workshop") }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = "INJECT SCHEDULE TELEMETRY",
                color = PurpleCyber,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        },
        containerColor = SurfaceDark,
        textContentColor = TextMain,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Title") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleCyber,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = PurpleCyber,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("add_event_title")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurpleCyber,
                            focusedLabelColor = PurpleCyber
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("Time (HH:MM)") },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurpleCyber,
                            focusedLabelColor = PurpleCyber
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = loc,
                    onValueChange = { loc = it },
                    label = { Text("Location State") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleCyber,
                        focusedLabelColor = PurpleCyber
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Details & Diagnostics Parameters") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Color.White),
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleCyber,
                        focusedLabelColor = PurpleCyber
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (title.isNotBlank()) {
                        onSave(title, date, time, desc, loc)
                    }
                },
                modifier = Modifier.testTag("save_event_btn")
            ) {
                Text(
                    text = "EXECUTE", 
                    color = PurpleCyber,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(
                    text = "ABORT", 
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}


// --- TAB 3: Web News Analyzer & Telemetry ---
@Composable
fun WebAnalyzerTab(viewModel: JarvisViewModel) {
    val analysisState by viewModel.webAnalysisState.collectAsState()
    var searchInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "REAL-TIME WEB DATA MINING",
            color = EmeraldBio,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )

        // Web analytical input query field
        OutlinedTextField(
            value = searchInput,
            onValueChange = { searchInput = it },
            placeholder = { 
                Text(
                    "Insert URL or search sector (e.g. Clean Energy Surging)", 
                    fontSize = 12.sp,
                    color = TextMuted
                ) 
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Web logo",
                    tint = EmeraldBio
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (searchInput.isNotBlank()) {
                    viewModel.performWebAnalysis(searchInput)
                }
            }),
            trailingIcon = {
                if (searchInput.isNotBlank()) {
                    IconButton(onClick = { 
                        viewModel.performWebAnalysis(searchInput)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Execute web scrape",
                            tint = EmeraldBio
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldBio,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("web_search_field")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // State machine display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (val state = analysisState) {
                is WebAnalysisState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "Scraper Standby",
                                tint = BorderColor,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "AWAITING SEARCH CHANNELS, SIR.",
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                is WebAnalysisState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = EmeraldBio,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                color = EmeraldBio,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "STARK NETWORK BYPASS ACTIVE",
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                is WebAnalysisState.Success -> {
                    WebReportRender(report = state.report)
                }
            }
        }
    }
}

@Composable
fun WebReportRender(report: WebReportModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // High Level Score Bento Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Glow Index Score
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .border(1.dp, EmeraldBio.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "GLOBAL INDEX",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${report.score}",
                            color = EmeraldBio,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "STARK MATCH",
                            color = EmeraldBio.copy(alpha = 0.6f),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Sentiment & URL Domain info
                Box(
                    modifier = Modifier
                        .weight(1.8f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "SENTIMENT TILT",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = report.sentiment.uppercase(),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "URL SOURCE: " + report.url.take(30) + "...",
                            color = EmeraldBio,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Title and summary panel
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = report.title.uppercase(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "INTELLIGENCE REPORT:",
                        color = EmeraldBio,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = report.summary,
                        color = TextMain,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Metrics Table Row bento style
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "METADATA TELEMETRY PACKETS",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    report.metrics.forEach { (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "> $key:",
                                color = TextMain,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = value.uppercase(),
                                color = EmeraldBio,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Scraped key takeaway bullet points
        item {
            Text(
                text = "TAKEAWAY KEY VECTORS",
                color = EmeraldBio,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        items(report.bullets) { bullet ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark.copy(alpha = 0.5f))
                    .border(1.dp, BorderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "◆",
                    color = EmeraldBio,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 10.dp, top = 2.dp)
                )
                Text(
                    text = bullet,
                    color = TextMain,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


// --- Persistent bottom input controller for manually writing messages, or simulated mic voice activation ---
@Composable
fun CommandInputPanel(viewModel: JarvisViewModel) {
    val isListening by viewModel.isListening.collectAsState()
    val speechBuffer by viewModel.speechInputBuffer.collectAsState()
    val voiceWaveformLevels by viewModel.voiceWaveformLevels.collectAsState()
    
    var localText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    viewModel.sendUserMessage(spokenText, isVoice = true)
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, BorderColor, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(16.dp)
    ) {
        // If voice activated simulated recording is active, display anim waveform soundwaves
        if (isListening) {
            Text(
                text = "AETHER VOCAL CAPTURE STREAMING...",
                color = CyanHolo,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 6.dp)
            )
            
            // Audio level simulated soundwaves (16 high-tech moving vertical blocks)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                voiceWaveformLevels.forEach { amp ->
                    val blockHeight = (amp * 28).coerceIn(4f, 28f).dp
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .width(4.dp)
                            .height(blockHeight)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(CyanHolo, PurpleCyber)
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            // Realtime responsive simulated dictation placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Cycle real Stark words
                val phrases = listOf(
                    "Aether, turn off the lights and secure the armor lock.",
                    "Aether, schedule Pepper Potts briefing tomorrow at nine.",
                    "Aether, scan web node for surge on quantum mechanics.",
                    "Aether, trigger optimal arc reactor core levels."
                )
                
                Text(
                    text = if (speechBuffer.isBlank()) "\"TAP PRESETS ABOVE OR TAP MIC BUTTON AGAIN TO SEND SIMULATION\"" else "\"$speechBuffer\"",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontStyle = if (speechBuffer.isBlank()) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Helpful simulation text input during listening mode
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { viewModel.setSimulatedVoiceInput("Aether, lock down the main armory vault.") },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("LOCKED CORE", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Button(
                    onClick = { viewModel.setSimulatedVoiceInput("Aether, scan web for Mars colonization news.") },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("SCAN NET", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Normal bottom keyboard input field with terminal theme
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = localText,
                onValueChange = { localText = it },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                placeholder = { 
                    Text(
                        "Command Aether...", 
                        fontSize = 12.sp, 
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    ) 
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (localText.isNotBlank()) {
                        viewModel.sendUserMessage(localText, isVoice = false)
                        localText = ""
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanHolo,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = SurfaceDark.copy(alpha = 0.5f),
                    unfocusedContainerColor = SurfaceDark.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("text_input_field")
            )

            // Submit text command
            if (localText.isNotBlank()) {
                IconButton(
                    onClick = {
                        viewModel.sendUserMessage(localText, isVoice = false)
                        localText = ""
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(CyanHolo, CircleShape)
                        .testTag("send_msg_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send packet",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // Dual Action voice system controls: Simulated Spectral Visualizer or Real-World SpeechToText Microphone
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Futuristic Spectral Simulator Trigger
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(if (isListening) PurpleCyber.copy(alpha = 0.3f) else SurfaceDark, CircleShape)
                            .border(1.dp, if (isListening) PurpleCyber else CyanHolo, CircleShape)
                            .clickable { viewModel.toggleVoiceListening() }
                            .testTag("mic_voice_sim_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.SettingsVoice,
                            contentDescription = "Simulate voice system",
                            tint = if (isListening) PurpleCyber else CyanHolo,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 2. Real-World Speech-to-Text Native Microphone System
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(PurpleCyber, CircleShape)
                            .clickable {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ta-IN")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "PESUNGAL / SPEAK NOW (Tamil-English Mix)")
                                }
                                try {
                                    speechRecognizerLauncher.launch(intent)
                                } catch (e: Exception) {
                                    // Graciously fail if speech recognition service is temporarily absent in workspace simulator box
                                }
                            }
                            .testTag("mic_voice_real_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Real speech recognition microphone",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// Class marker finished clean



// --- TAB NAVIGATION CONTROLLER (Futuristic Glow Buttons) ---
@Composable
fun JarvisBottomBar(currentTab: Int, onTabSelected: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .border(1.dp, BorderColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .windowInsetsPadding(WindowInsets.navigationBars) // Adaptive safety paddings
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            JarvisBarItem(
                icon = Icons.Default.Adjust,
                label = "CORE",
                isSelected = currentTab == 0,
                activeColor = CyanHolo,
                onClick = { onTabSelected(0) },
                tag = "tab_core"
            )

            JarvisBarItem(
                icon = Icons.Default.HomeWork,
                label = "AUTO",
                isSelected = currentTab == 1,
                activeColor = CyanHolo,
                onClick = { onTabSelected(1) },
                tag = "tab_auto"
            )

            JarvisBarItem(
                icon = Icons.Default.CalendarMonth,
                label = "LEDGER",
                isSelected = currentTab == 2,
                activeColor = PurpleCyber,
                onClick = { onTabSelected(2) },
                tag = "tab_ledger"
            )

            JarvisBarItem(
                icon = Icons.Default.Search,
                label = "WEB",
                isSelected = currentTab == 3,
                activeColor = EmeraldBio,
                onClick = { onTabSelected(3) },
                tag = "tab_web"
            )
        }
    }
}

@Composable
fun JarvisBarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) activeColor else TextMuted,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            color = if (isSelected) activeColor else TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )

        // Micro active marker line below label
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .width(12.dp)
                    .height(2.dp)
                    .background(activeColor, RoundedCornerShape(1.dp))
            )
        }
    }
}

// --- Main Aether Dialogue Popup Screen Overlay ---
@Composable
fun AetherPopupScreenOverlay(viewModel: JarvisViewModel) {
    val isListening by viewModel.isListening.collectAsState()
    val isResponding by viewModel.isJarvisResponding.collectAsState()
    val voiceWaveformLevels by viewModel.voiceWaveformLevels.collectAsState()
    val chatLogs by viewModel.chatLogs.collectAsState()
    val speechBuffer by viewModel.speechInputBuffer.collectAsState()
    
    val lastSpeaker = chatLogs.lastOrNull { it.sender == "AETHER" || it.sender == "USER" }
    val dialogueText = when {
        isResponding -> {
            "DECIPHERING QUANTUM SPEECH STREAM AND FETCHING GOOGLE FACTS..."
        }
        isListening -> {
            if (speechBuffer.isBlank()) "\"Say 'off' to shutdown everything, or speak a command...\"" else "\"$speechBuffer\""
        }
        lastSpeaker != null -> {
            "${lastSpeaker.sender}: ${lastSpeaker.message}"
        }
        else -> {
            "Aether secure system linked. Awaiting command, Sir."
        }
    }
    
    // Background dimming container with high-tech radial overlay tint
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {} // block clicks passing through
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Futuristic Holographic terminal card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(listOf(CyanHolo, PurpleCyber)),
                    shape = RoundedCornerShape(24.dp)
                )
                .testTag("aether_popup_dialog_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Glow status header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(CyanHolo, CircleShape)
                        )
                        Text(
                            text = "AETHER SYSTEM HUD",
                            color = CyanHolo,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                    ) {
                        Text(
                            text = if (isListening) "LISTENING" else if (isResponding) "PROCESSING" else "STANDBY",
                            color = if (isListening) PurpleCyber else if (isResponding) CyanHolo else Color.White.copy(alpha = 0.7f),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Pulsing central high-tech core orb
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .drawBehind {
                            drawCircle(
                                color = CyanHolo.copy(alpha = 0.15f),
                                radius = size.minDimension / 2,
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    HolographicReactorCore(isActive = true, isResponding = isResponding)
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Glowing Cyber Waveforms inside the small popup screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isListening && voiceWaveformLevels.isNotEmpty()) {
                        voiceWaveformLevels.forEach { amp ->
                            val blockHeight = (amp * 32).coerceIn(4f, 32f).dp
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .width(3.dp)
                                    .height(blockHeight)
                                    .background(
                                        brush = Brush.verticalGradient(listOf(CyanHolo, PurpleCyber)),
                                        shape = RoundedCornerShape(1.5.dp)
                                    )
                            )
                        }
                    } else if (isResponding) {
                        // Alternate pulsating lines to indicate active digital telemetry
                        val transition = rememberInfiniteTransition(label = "pulse_telemetry")
                        val ampScalar by transition.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 0.9f,
                            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
                            label = "scalar"
                        )
                        List(16) { index ->
                            val waveHeight = (32f * ampScalar * (1f - (kotlin.math.abs(index - 8) / 10f))).coerceIn(4f, 32f).dp
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .width(3.dp)
                                    .height(waveHeight)
                                    .background(
                                        brush = Brush.verticalGradient(listOf(PurpleCyber, CyanHolo)),
                                        shape = RoundedCornerShape(1.5.dp)
                                    )
                            )
                        }
                    } else {
                        // Standby calm pulse lines
                        val transition = rememberInfiniteTransition(label = "standby_calm")
                        val ampScalar by transition.animateFloat(
                            initialValue = 4f,
                            targetValue = 10f,
                            animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "calm"
                        )
                        List(16) { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .width(3.dp)
                                    .height(if (index == 7 || index == 8) ampScalar.dp else 4.dp)
                                    .background(CyanHolo.copy(alpha = 0.5f), RoundedCornerShape(1.5.dp))
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Google Knowledge telemetry link badge inside popup
                Row(
                    modifier = Modifier
                        .background(CyanHolo.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .border(1.dp, CyanHolo.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Google Search",
                        tint = CyanHolo,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "GOOGLE KNOWLEDGE CONNECTED",
                        color = CyanHolo,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Dialogue console bubble
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = dialogueText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Quick commands trigger block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Stop Everything Button (OFF) - Executing user requested immediate halt/off parameters
                    Button(
                        onClick = { viewModel.stopEverything() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("shut_down_system_btn")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Shut down",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "SAY 'OFF'", 
                                color = Color.White, 
                                fontSize = 10.sp, 
                                fontFamily = FontFamily.Monospace, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Dismiss Standby Button
                    Button(
                        onClick = { viewModel.dismissAetherPopup() },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("popup_dismiss_btn")
                    ) {
                        Text(
                            text = "STANDBY", 
                            color = Color.White, 
                            fontSize = 10.sp, 
                            fontFamily = FontFamily.Monospace, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
