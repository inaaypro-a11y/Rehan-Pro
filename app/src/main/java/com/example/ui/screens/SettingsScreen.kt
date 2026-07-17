package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentName: String,
    isDarkTheme: Boolean,
    aiProvider: String,
    customApiKey: String,
    onSaveName: (String) -> Unit,
    onToggleTheme: () -> Unit,
    onSaveAiProvider: (String) -> Unit,
    onSaveCustomApiKey: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editName by remember { mutableStateOf(currentName) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }
    
    var showKeyInput by remember { mutableStateOf(false) }
    var keyText by remember { mutableStateOf(customApiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceBackground),
                modifier = Modifier.shadow(1.dp)
            )
        },
        containerColor = SpaceBackground,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SpaceBackground, Color(0xFF0F172A))
                    )
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header settings text
                Text(
                    text = "⚙️ RehanProAI Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Profile card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "User Profile",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlueAccent,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Name", fontSize = 11.sp, color = TextMuted)
                                Text(text = currentName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    editName = currentName
                                    showEditDialog = true
                                },
                                modifier = Modifier.testTag("edit_profile_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = BrandBlueAccent)
                            }
                        }
                    }
                }

                // Preferences Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(
                            text = "Preferences",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlueAccent
                        )

                        // Theme Toggle button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LightMode, contentDescription = "Theme", tint = Color.White)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = "Dark / Light Mode", color = Color.White, fontSize = 15.sp)
                            }
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = { onToggleTheme() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BrandBlueAccent,
                                    checkedTrackColor = BrandBlue.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.testTag("theme_switch")
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

                        // AI Engine selection
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "🤖 AI Engine Provider",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { onSaveAiProvider("openai") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (aiProvider == "openai") BrandBlue else BubbleAISlate
                                    ),
                                    modifier = Modifier.weight(1.5f).height(42.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("OpenAI (GPT-4o)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { onSaveAiProvider("gemini") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (aiProvider == "gemini") BrandBlue else BubbleAISlate
                                    ),
                                    modifier = Modifier.weight(1.5f).height(42.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Gemini 1.5 Flash", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

                        // Custom API key block
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔑 Custom API Key (Optional)",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    onClick = { showKeyInput = !showKeyInput },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (showKeyInput) "Hide Input" else "Configure Key", color = BrandBlueAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (showKeyInput) {
                                OutlinedTextField(
                                    value = keyText,
                                    onValueChange = {
                                        keyText = it
                                        onSaveCustomApiKey(it)
                                    },
                                    placeholder = { Text("Paste your OpenAI or Gemini key here", color = TextMuted, fontSize = 13.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = BrandBlueAccent,
                                        unfocusedBorderColor = BubbleAISlate,
                                        focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                                        unfocusedContainerColor = Color.Black.copy(alpha = 0.1f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "If provided, this key overrides the default workspace environment key. Leave blank to use system defaults.",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            } else {
                                Text(
                                    text = if (customApiKey.isNotEmpty()) "🔒 Custom key configured (Overrides default)" else "🌐 Using default build environment credentials",
                                    color = if (customApiKey.isNotEmpty()) BrandBlueAccent else TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Danger Actions Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Danger Zone",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Button(
                            onClick = { showConfirmClearDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("clear_history_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear Chats")
                                Text("Clear Chat History", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Back Button
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Back to Chat", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    // Edit Name Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.trim().isNotEmpty()) {
                            onSaveName(editName.trim())
                            showEditDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            title = { Text("Edit Profile Name", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandBlueAccent,
                        unfocusedBorderColor = BubbleAISlate
                    )
                )
            },
            containerColor = CardBackground,
            shape = RoundedCornerShape(20.dp)
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
            text = {
                Text(
                    text = "This will permanently delete all chat session histories and individual messages. This action is irreversible.",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            containerColor = CardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
