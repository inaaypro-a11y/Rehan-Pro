package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.speech.tts.TextToSpeech
import android.util.Base64
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ChatMessageEntity
import com.example.data.database.ChatSessionEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessions: List<ChatSessionEntity>,
    activeSessionId: Int?,
    messages: List<ChatMessageEntity>,
    isGenerating: Boolean,
    pendingAttachment: String?,
    pendingAttachmentType: String?,
    userName: String,
    aiProvider: String,
    onSelectSession: (Int) -> Unit,
    onCreateSession: () -> Unit,
    onDeleteSession: (Int) -> Unit,
    onSendMessage: (String) -> Unit,
    onSetAttachment: (String?, String?) -> Unit,
    onUpdateAiProvider: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()
    
    var inputText by remember { mutableStateOf("") }
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var showVoiceOverlay by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }
    var spokenPromptPlayed by remember { mutableStateOf(false) }

    // Text To Speech engine
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    
    // Initialize TTS
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
        // Try English and Telugu languages
        tts.language = Locale.US
        ttsInstance = tts
        
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // Auto-scroll to bottom of chat when new messages arrive
    LaunchedEffect(messages.size, isGenerating) {
        val totalItems = messages.size + (if (isGenerating) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isTablet = screenWidthDp >= 720
    var isSidebarExpanded by remember { mutableStateOf(isTablet) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isTablet,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CardBackground,
                drawerContentColor = Color.White,
                modifier = Modifier.width(300.dp)
            ) {
                SidebarContent(
                    sessions = sessions,
                    activeSessionId = activeSessionId,
                    userName = userName,
                    onCreateSession = onCreateSession,
                    onSelectSession = onSelectSession,
                    onDeleteSession = onDeleteSession,
                    onNavigateToSettings = onNavigateToSettings,
                    onClearHistory = { showConfirmClearDialog = true },
                    onSessionSelected = { scope.launch { drawerState.close() } }
                )
            }
        },
        modifier = modifier
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (isTablet && isSidebarExpanded) {
                SidebarContent(
                    sessions = sessions,
                    activeSessionId = activeSessionId,
                    userName = userName,
                    onCreateSession = onCreateSession,
                    onSelectSession = onSelectSession,
                    onDeleteSession = onDeleteSession,
                    onNavigateToSettings = onNavigateToSettings,
                    onClearHistory = { showConfirmClearDialog = true },
                    onSessionSelected = {},
                    modifier = Modifier.width(300.dp)
                )
                // Add vertical divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(BubbleAISlate)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                val activeSessionTitle = sessions.find { it.id == activeSessionId }?.title ?: "Chat"
                                Text(
                                    text = activeSessionTitle,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        if (isTablet) {
                                            isSidebarExpanded = !isSidebarExpanded
                                        } else {
                                            scope.launch { drawerState.open() }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Menu Icon",
                                        tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                Box(
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    // Custom pill button for the active AI Provider dropdown
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .testTag("ai_provider_toggle_button")
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(BrandBlue.copy(alpha = 0.15f))
                                            .border(1.dp, BrandBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                            .clickable { showProviderMenu = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (aiProvider == "gemini") Icons.Default.AutoAwesome else Icons.Default.Bolt,
                                            contentDescription = "Active AI Provider",
                                            tint = BrandBlueAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (aiProvider == "gemini") "Gemini" else "OpenAI",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Dropdown indicator",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showProviderMenu,
                                        onDismissRequest = { showProviderMenu = false },
                                        modifier = Modifier
                                            .background(BubbleAISlate)
                                            .border(1.dp, BrandBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AutoAwesome,
                                                        contentDescription = "Gemini",
                                                        tint = if (aiProvider == "gemini") BrandBlueAccent else TextMuted,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = "Gemini AI",
                                                        color = if (aiProvider == "gemini") Color.White else TextMuted,
                                                        fontWeight = if (aiProvider == "gemini") FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onUpdateAiProvider("gemini")
                                                showProviderMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Bolt,
                                                        contentDescription = "OpenAI",
                                                        tint = if (aiProvider == "openai") BrandBlueAccent else TextMuted,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = "OpenAI GPT",
                                                        color = if (aiProvider == "openai") Color.White else TextMuted,
                                                        fontWeight = if (aiProvider == "openai") FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onUpdateAiProvider("openai")
                                                showProviderMenu = false
                                            }
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { showConfirmClearDialog = true },
                                    modifier = Modifier.testTag("clear_chat_header_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Clear All Chats",
                                        tint = Color.White
                                    )
                                }

                                IconButton(onClick = onNavigateToSettings) {
                                    Icon(Icons.Default.Settings, contentDescription = "Config Icon", tint = Color.White)
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = SpaceBackground
                            ),
                            modifier = Modifier.shadow(1.dp)
                        )
                    },
                    containerColor = SpaceBackground
                ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Background Glow accents
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(SpaceBackground, Color(0xFF0F172A))
                            )
                        )
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    
                    // Messages Lazy Column
                    if (messages.isEmpty() && !isGenerating) {
                        // Empty Chat onboarding state
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .background(BrandBlue.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI Glow",
                                        tint = BrandBlueAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "RehanProAI Active",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "How can I help you today, $userName? " +
                                            "Type a message, record your voice, or attach files to begin.",
                                    fontSize = 13.sp,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.widthIn(max = 280.dp),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                ChatBubbleItem(
                                    message = message,
                                    onSpeakText = { text ->
                                        if (isTtsReady) {
                                            ttsInstance?.stop()
                                            ttsInstance?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                                        }
                                    }
                                )
                            }

                            if (isGenerating) {
                                item {
                                    TypingIndicatorItem()
                                }
                            }
                        }
                    }

                    // Floating attachment visual block
                    if (pendingAttachment != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardBackground)
                                .border(1.dp, BubbleAISlate)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (pendingAttachmentType == "application/pdf") Icons.Default.Description else Icons.Default.Image,
                                contentDescription = "Attached Icon",
                                tint = BrandBlueAccent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (pendingAttachmentType == "application/pdf") "📄 PDF Document Attached" else "🖼️ Dynamic Image Selected",
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onSetAttachment(null, null) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove attachment", tint = Color.Red)
                            }
                        }
                    }

                    // Chat Input Panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBackground)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 📎 Attachment Button
                        IconButton(
                            onClick = { showAttachmentDialog = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attachment Picker", tint = TextMuted)
                        }

                        // 🎤 Voice AI Button
                        IconButton(
                            onClick = { showVoiceOverlay = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Assistant", tint = TextMuted)
                        }

                        // Expandable Text Field Input
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Message RehanProAI...", color = TextMuted) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SpaceBackground,
                                unfocusedContainerColor = SpaceBackground,
                                disabledContainerColor = SpaceBackground,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(max = 120.dp)
                                .testTag("message_input")
                                .border(1.dp, BubbleAISlate, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp)),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // ➤ Send Button
                        IconButton(
                            onClick = {
                                if (inputText.trim().isNotEmpty() || pendingAttachment != null) {
                                    onSendMessage(inputText)
                                    inputText = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(BrandBlue, CircleShape)
                                .testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }
    }

    // Attachment Chooser dialog (Simulated uploads)
    if (showAttachmentDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentDialog = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachmentDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            title = { Text("Select Attachment", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Option A: Stunning Galaxy Image
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Simulate loading an base64 image (pure visual test data)
                                onSetAttachment(getSpaceBase64Mock(), "image/jpeg")
                                showAttachmentDialog = false
                            },
                        colors = CardDefaults.cardColors(containerColor = BubbleAISlate)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = "Image Upload", tint = BrandBlueAccent)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("🖼️ Space Launch Visual", fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Simulates high-fidelity galactic image input.", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }

                    // Option B: Research PDF Document
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Simulate PDF upload context
                                onSetAttachment("MOCK_PDF_SUMMARIZER_CONTENT", "application/pdf")
                                showAttachmentDialog = false
                            },
                        colors = CardDefaults.cardColors(containerColor = BubbleAISlate)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, contentDescription = "PDF upload", tint = BrandBlueAccent)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("📄 Annual Financials Summary.pdf", fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Attaches sample PDF document text.", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }
            },
            containerColor = CardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Interactive Voice Speech-to-Text Overlay
    if (showVoiceOverlay) {
        VoiceAIOverlay(
            onClose = { showVoiceOverlay = false },
            onPromptRecorded = { spokenText ->
                inputText = spokenText
                showVoiceOverlay = false
                // Instantly send it to trigger Voice Assistant workflow
                onSendMessage(spokenText)
            }
        )
    }

    // Confirm Clear Dialog
    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showConfirmClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Clear All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            title = { Text("Clear All History?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all chat sessions and message history. This action cannot be undone.", color = Color.White.copy(alpha = 0.7f)) },
            containerColor = CardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun SidebarContent(
    sessions: List<ChatSessionEntity>,
    activeSessionId: Int?,
    userName: String,
    onCreateSession: () -> Unit,
    onSelectSession: (Int) -> Unit,
    onDeleteSession: (Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onClearHistory: () -> Unit,
    onSessionSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CardBackground)
            .padding(16.dp)
    ) {
        Text(
            text = "RehanProAI",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        HorizontalDivider(color = BubbleAISlate, thickness = 1.dp)

        Spacer(modifier = Modifier.height(16.dp))

        // New Chat Button
        Button(
            onClick = {
                onCreateSession()
                onSessionSelected()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("new_chat_button"),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Icon")
                Text("New Chat", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Chat History",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Chat threads list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(sessions) { session ->
                val isActive = session.id == activeSessionId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) BrandBlue else Color.Transparent)
                        .clickable {
                            onSelectSession(session.id)
                            onSessionSelected()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Message Thread",
                            tint = if (isActive) Color.White else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = session.title,
                            fontSize = 14.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Delete Thread Icon
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Thread",
                        tint = if (isActive) Color.White.copy(alpha = 0.7f) else TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                onDeleteSession(session.id)
                            }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Clear All Chats Button inside Sidebar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFDC2626).copy(alpha = 0.15f))
                .border(1.dp, Color(0xFFDC2626).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .clickable { onClearHistory() }
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = "Clear All History",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Clear All Chats",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFFEF4444)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider(color = BubbleAISlate, thickness = 1.dp)

        Spacer(modifier = Modifier.height(12.dp))

        // User Profile details at footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToSettings() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(BrandBlueAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userName.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = userName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Pro Plan Member",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextMuted
            )
        }
    }
}

/**
 * Message Bubble View supporting custom canvasses for simulated AI drawings
 * and native base64 decoded rendering.
 */
@Composable
fun ChatBubbleItem(
    message: ChatMessageEntity,
    onSpeakText: (String) -> Unit
) {
    val isUser = message.role == "USER"
    val bubbleColor = if (isUser) BrandBlue else BubbleAISlate
    val alignment = if (isUser) Alignment.End else Alignment.Start

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing)) +
                    slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
            if (!isUser) {
                // AI Avatar Icon
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(28.dp)
                        .background(BrandBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Avatar",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Actual Bubble Card
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 2.dp,
                    bottomEnd = if (isUser) 2.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                modifier = Modifier.shadow(4.dp, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    
                    // Render Attachment Preview
                    if (message.attachmentPath != null) {
                        if (message.attachmentPath == "MOCK_IMAGE_ASSET") {
                            // procedural canvas retro synthwave render!
                            Text(
                                text = "🎨 LOCAL GRAPHIC GENERATOR ENGINES ACTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandBlueAccent,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            CustomSynthwaveCanvas()
                            Spacer(modifier = Modifier.height(8.dp))
                        } else if (message.attachmentPath.startsWith("data:image/")) {
                            // Standard base64 decoding
                            val base64String = message.attachmentPath.substringAfter(",")
                            val bitmap = remember(base64String) { base64String.decodeBase64ToBitmap() }
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "AI Rendered Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        } else if (message.attachmentType == "application/pdf") {
                            // Render PDF summarizer banner
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SpaceBackground),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = "PDF Doc", tint = Color.Red)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("📄 Research_Paper_Summarized.pdf", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    // Main Text Content
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    // AI response actions row
                    if (!isUser) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Speak Text button for Voice AI
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { onSpeakText(message.text) }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Read Aloud",
                                    tint = BrandBlueAccent.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Listen AI Voice",
                                    fontSize = 11.sp,
                                    color = BrandBlueAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Copy to Clipboard action button
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            val context = androidx.compose.ui.platform.LocalContext.current
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .testTag("copy_to_clipboard_button")
                                    .clickable {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(message.text))
                                        android.widget.Toast.makeText(context, "Copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy to Clipboard",
                                    tint = BrandBlueAccent.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Copy",
                                    fontSize = 11.sp,
                                    color = BrandBlueAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * Procedural Vector Cyber Synthwave landscape drawn purely on Jetpack Compose Canvas.
 */
@Composable
fun CustomSynthwaveCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "synthwave")
    val gridOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "grid"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
    ) {
        // Draw starry space sky gradient
        val skyGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF030712), Color(0xFF1E1B4B), Color(0xFF4C1D95))
        )
        drawRect(brush = skyGradient)

        // Draw neon space sun
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFF43F5E), Color(0xFFE11D48), Color(0xFFFBBF24))
            ),
            radius = 45f,
            center = Offset(size.width / 2, size.height * 0.45f)
        )

        // Draw Grid horizon
        val horizonY = size.height * 0.65f
        
        // Draw 3D wireframe perspective lines
        val lineStroke = Stroke(width = 2f)
        val numPerspectiveLines = 12
        for (i in 0..numPerspectiveLines) {
            val startX = size.width * (i.toFloat() / numPerspectiveLines)
            val endX = size.width / 2 + (startX - size.width / 2) * 2.5f
            drawLine(
                color = Color(0xFFD946EF),
                start = Offset(startX, horizonY),
                end = Offset(endX, size.height),
                strokeWidth = 2f
            )
        }

        // Draw moving horizontal lines for neon grid speed effect
        var yPos = horizonY + gridOffset
        while (yPos < size.height) {
            val weightMultiplier = (yPos - horizonY) / (size.height - horizonY)
            drawLine(
                color = Color(0xFFEC4899).copy(alpha = 0.8f),
                start = Offset(0f, yPos),
                end = Offset(size.width, yPos),
                strokeWidth = 1.5f + (2f * weightMultiplier)
            )
            yPos += 20f + (30f * weightMultiplier)
        }

        // Draw mountains silhouette on the horizon
        val mountainPath = Path().apply {
            moveTo(0f, horizonY)
            lineTo(size.width * 0.2f, horizonY - 25f)
            lineTo(size.width * 0.35f, horizonY - 10f)
            lineTo(size.width * 0.5f, horizonY - 45f)
            lineTo(size.width * 0.65f, horizonY - 15f)
            lineTo(size.width * 0.8f, horizonY - 35f)
            lineTo(size.width, horizonY)
            close()
        }
        drawPath(
            path = mountainPath,
            color = Color(0xFF110C24)
        )
        drawPath(
            path = mountainPath,
            color = Color(0xFFEC4899),
            style = Stroke(width = 2f)
        )
    }
}

/**
 * Gorgeous Typing Loading Indicator matching AI Studio standards.
 */
@Composable
fun TypingIndicatorItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    
    // Smooth bounce translationY animation for dots
    val dotOffsets = List(3) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -5f, // Bounces up 5dp
            animationSpec = infiniteRepeatable(
                animation = tween(400, delayMillis = index * 120, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_offset_$index"
        )
    }

    // Alpha fade in/out sync with the bounce
    val dotAlphas = List(3) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, delayMillis = index * 120, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_alpha_$index"
        )
    }

    // Breathing glow animation for AI avatar
    val avatarPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // AI Avatar with beautiful pulsating glow
        Box(
            modifier = Modifier.padding(end = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulse Ring
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .scale(avatarPulse)
                    .background(BrandBlue.copy(alpha = 0.15f), CircleShape)
            )
            // Core Avatar Circle
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BrandBlue, BrandBlueAccent)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Loading Avatar",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // Response Card with subtle modern gradient border
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = BubbleAISlate),
            modifier = Modifier
                .shadow(4.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(BubbleAISlate, BrandBlue.copy(alpha = 0.25f))
                    ),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "RehanProAI is thinking",
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 3) {
                        Box(
                            modifier = Modifier
                                .offset(y = dotOffsets[i].value.dp)
                                .size(5.dp)
                                .background(BrandBlueAccent.copy(alpha = dotAlphas[i].value), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated voice wave recording assistant layout.
 */
@Composable
fun VoiceAIOverlay(
    onClose: () -> Unit,
    onPromptRecorded: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val waveHeight1 by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val waveHeight2 by infiniteTransition.animateFloat(
        initialValue = 15f,
        targetValue = 75f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 100, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val waveHeight3 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(enabled = false) {} // block click propagation
        ) {
            Text(
                text = "Voice Assistant Active",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Talk naturally with RehanProAI...",
                fontSize = 13.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Animated speech waves
            Row(
                modifier = Modifier
                    .height(100.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(waveHeight1, waveHeight2, waveHeight3, waveHeight2, waveHeight1).forEachIndexed { _, animVal ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .width(6.dp)
                            .height(animVal.dp)
                            .background(BrandBlueAccent, RoundedCornerShape(3.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Mic Glowing circle button
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(BrandBlue, CircleShape)
                    .shadow(16.dp, CircleShape, spotColor = BrandBlueAccent)
                    .clickable {
                        // Mock Speech Recognition returning structured prompts!
                        val prompts = listOf(
                            "Recommend a creative space project idea in Telugu",
                            "Tell me a story about artificial intelligence",
                            "వాట్ ఈస్ రెహాన్ ప్రో ఏఐ",
                            "What is RehanProAI and how can I integrate it?"
                        )
                        onPromptRecorded(prompts.random())
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Stop Recording",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Tap circle to capture voice prompt",
                fontSize = 11.sp,
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Simple Base64 decoder extension
fun String.decodeBase64ToBitmap(): Bitmap? {
    return try {
        val decodedBytes = Base64.decode(this, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}

// Fast Base64 mock image of space galaxy (JPEG header format)
fun getSpaceBase64Mock(): String {
    return "/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAoHCBYWFRgWFhYZGRgZGhkaGBkaGhocGBgYHBgZGhkcGBocIS4gHB0rIRgYJjgmKy8xNTU1GiQ7QDs0Py40NTEBDAwMEA8QHhISHzQrISs0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NP/AABEIAOEA4QMBIgACEQEDEQH/xAAWAAEBAQAAAAAAAAAAAAAAAAAAAQL/xAAWEAEBAQAAAAAAAAAAAAAAAAAAASD/xAAWEQEBAQAAAAAAAAAAAAAAAAAAASD/2gAMAwEAAhEDEQY/ALQ0DQA0A0DQNA0DQA0A0A0A0A0A0A0A0DQNA0A0DQA0A0A0A0f/Z"
}

// --- Jetpack Compose Markdown Renderer ---

sealed class MarkdownBlock {
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class ListBlock(val items: List<String>, val ordered: Boolean) : MarkdownBlock()
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockView(code = block.code, language = block.language)
                }
                is MarkdownBlock.Paragraph -> {
                    ParagraphView(text = block.text)
                }
                is MarkdownBlock.ListBlock -> {
                    ListBlockView(items = block.items, ordered = block.ordered)
                }
            }
        }
    }
}

fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val parts = rawText.split("```")
    
    for (i in parts.indices) {
        val part = parts[i]
        if (i % 2 != 0) {
            // Code block
            val lines = part.split("\n")
            val firstLine = lines.firstOrNull()?.trim()
            val language = if (firstLine != null && firstLine.isNotEmpty() && !firstLine.any { it.isWhitespace() }) {
                firstLine
            } else {
                null
            }
            val codeLines = if (language != null) lines.drop(1) else lines
            val code = codeLines.joinToString("\n").trim()
            if (code.isNotEmpty()) {
                blocks.add(MarkdownBlock.CodeBlock(code, language))
            }
        } else {
            // Paragraphs and lists
            if (part.isEmpty()) continue
            val lines = part.split("\n")
            var currentListItems = mutableListOf<String>()
            var isOrderedList = false
            
            for (line in lines) {
                val trimmed = line.trim()
                val isBullet = trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ")
                val isNumbered = trimmed.matches(Regex("^\\d+\\.\\s+.*"))
                
                if (isBullet || isNumbered) {
                    val itemContent = if (isBullet) {
                        trimmed.substring(2).trim()
                    } else {
                        trimmed.replaceFirst(Regex("^\\d+\\.\\s+"), "").trim()
                    }
                    if (currentListItems.isEmpty()) {
                        isOrderedList = isNumbered
                    }
                    currentListItems.add(itemContent)
                } else {
                    if (currentListItems.isNotEmpty()) {
                        blocks.add(MarkdownBlock.ListBlock(currentListItems.toList(), isOrderedList))
                        currentListItems = mutableListOf()
                    }
                    if (trimmed.isNotEmpty()) {
                        blocks.add(MarkdownBlock.Paragraph(line))
                    }
                }
            }
            if (currentListItems.isNotEmpty()) {
                blocks.add(MarkdownBlock.ListBlock(currentListItems.toList(), isOrderedList))
            }
        }
    }
    return blocks
}

@Composable
fun parseInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    var i = 0
    val length = text.length
    
    while (i < length) {
        // Bold (** or __)
        if (i + 1 < length && (text[i] == '*' && text[i+1] == '*' || text[i] == '_' && text[i+1] == '_')) {
            val delimiter = text.substring(i, i + 2)
            val nextIndex = text.indexOf(delimiter, i + 2)
            if (nextIndex != -1) {
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                builder.append(text.substring(i + 2, nextIndex))
                builder.pop()
                i = nextIndex + 2
                continue
            }
        }
        
        // Inline Code (`)
        if (text[i] == '`') {
            val nextIndex = text.indexOf('`', i + 1)
            if (nextIndex != -1) {
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color.White.copy(alpha = 0.12f),
                    color = BrandBlueAccent
                ))
                builder.append(" ")
                builder.append(text.substring(i + 1, nextIndex))
                builder.append(" ")
                builder.pop()
                i = nextIndex + 1
                continue
            }
        }
        
        // Italic (* or _)
        if (text[i] == '*' || text[i] == '_') {
            val delimiter = text[i].toString()
            val nextIndex = text.indexOf(delimiter, i + 1)
            if (nextIndex != -1) {
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                builder.append(text.substring(i + 1, nextIndex))
                builder.pop()
                i = nextIndex + 1
                continue
            }
        }
        
        builder.append(text[i])
        i++
    }
    return builder.toAnnotatedString()
}

@Composable
fun CodeBlockView(code: String, language: String?) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = language?.uppercase() ?: "CODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Copied Code", code)
                        clipboard.setPrimaryClip(clip)
                        copied = true
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy Code",
                    tint = if (copied) Color.Green else TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (copied) "Copied!" else "Copy",
                    fontSize = 10.sp,
                    color = if (copied) Color.Green else TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFFE2E8F0),
                lineHeight = 18.sp
            )
        }
    }
    
    if (copied) {
        LaunchedEffect(Unit) {
            delay(2000)
            copied = false
        }
    }
}

@Composable
fun ParagraphView(text: String) {
    Text(
        text = parseInlineMarkdown(text),
        color = Color.White,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
}

@Composable
fun ListBlockView(items: List<String>, ordered: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = if (ordered) "${index + 1}. " else "• ",
                    color = BrandBlueAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = parseInlineMarkdown(item),
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
