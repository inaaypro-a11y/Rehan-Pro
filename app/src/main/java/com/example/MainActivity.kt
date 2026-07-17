package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ChatViewModel = viewModel()
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            
            MyApplicationTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                
                val userName by viewModel.userName.collectAsState()
                val sessions by viewModel.sessions.collectAsState()
                val activeSessionId by viewModel.activeSessionId.collectAsState()
                val messages by viewModel.activeSessionMessages.collectAsState()
                val isGenerating by viewModel.isGenerating.collectAsState()
                val pendingAttachment by viewModel.pendingAttachment.collectAsState()
                val pendingAttachmentType by viewModel.pendingAttachmentType.collectAsState()
                val aiProvider by viewModel.aiProvider.collectAsState()
                val customApiKey by viewModel.customApiKey.collectAsState()

                NavHost(
                    navController = navController,
                    startDestination = "welcome",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("welcome") {
                        WelcomeScreen(
                            onStartChat = {
                                if (userName == "Guest" || userName.isEmpty()) {
                                    navController.navigate("login")
                                } else {
                                    navController.navigate("chat")
                                }
                            }
                        )
                    }
                    composable("login") {
                        LoginScreen(
                            currentName = userName,
                            onLoginSuccess = { selectedName ->
                                viewModel.updateProfileName(selectedName)
                                navController.navigate("chat") {
                                    popUpTo("welcome") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("chat") {
                        ChatScreen(
                            sessions = sessions,
                            activeSessionId = activeSessionId,
                            messages = messages,
                            isGenerating = isGenerating,
                            pendingAttachment = pendingAttachment,
                            pendingAttachmentType = pendingAttachmentType,
                            userName = userName,
                            aiProvider = aiProvider,
                            onSelectSession = { viewModel.selectSession(it) },
                            onCreateSession = { viewModel.createNewSession() },
                            onDeleteSession = { viewModel.deleteSession(it) },
                            onSendMessage = { viewModel.sendMessage(it) },
                            onSetAttachment = { path, type -> viewModel.setAttachment(path, type) },
                            onUpdateAiProvider = { viewModel.updateAiProvider(it) },
                            onNavigateToSettings = { navController.navigate("settings") },
                            onClearHistory = { viewModel.clearAllChats() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            currentName = userName,
                            isDarkTheme = isDarkTheme,
                            aiProvider = aiProvider,
                            customApiKey = customApiKey,
                            onSaveName = { viewModel.updateProfileName(it) },
                            onToggleTheme = { viewModel.toggleTheme() },
                            onSaveAiProvider = { viewModel.updateAiProvider(it) },
                            onSaveCustomApiKey = { viewModel.updateCustomApiKey(it) },
                            onClearHistory = { viewModel.clearAllChats() },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
