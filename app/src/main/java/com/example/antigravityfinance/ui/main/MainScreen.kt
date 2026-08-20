package com.example.antigravityfinance.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.compose.ui.unit.sp
import com.example.antigravityfinance.data.model.ThemeType
import com.example.antigravityfinance.data.model.LanguageType
import com.example.antigravityfinance.theme.AntigravityFinanceTheme
import com.example.antigravityfinance.ui.components.PinKeyboardGate
import com.example.antigravityfinance.ui.screens.*
import com.example.antigravityfinance.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontFamily

enum class MainTab(val displayName: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Rounded.Home),
    TRANSACTIONS("Transaction", Icons.Rounded.ReceiptLong),
    ASSISTANT("AI Chat", Icons.Rounded.Android),
    FINANCIAL_TOOLS("Progress", Icons.Rounded.BarChart),
    SETTINGS("Profile", Icons.Rounded.Person)
}

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FinanceViewModel = viewModel()
) {
    val themeType by viewModel.themeType.collectAsState()
    val isDarkPref by viewModel.isDarkMode.collectAsState()
    val customAccent by viewModel.customAccent.collectAsState()
    val language by viewModel.language.collectAsState()

    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2000)
        showSplash = false
    }

    // Render themed container
    AntigravityFinanceTheme(
        themeType = themeType,
        darkTheme = isDarkPref ?: androidx.compose.foundation.isSystemInDarkTheme(),
        customAccent = customAccent
    ) {
        val isRegistered by viewModel.isRegistered.collectAsState()
        val isPinSet by viewModel.isPinSet.collectAsState()
        val isAuthRequired by viewModel.isAuthRequired.collectAsState()
        val pinError by viewModel.pinError.collectAsState()

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (showSplash) {
                PrivacySplashView(language = language)
            } else if (!isRegistered) {
                OnboardingScreen(viewModel = viewModel)
            } else if (isPinSet && isAuthRequired) {
                // Secure Authentication Lock Gate
                PinKeyboardGate(
                    isPinSet = true,
                    pinError = pinError,
                    onPinSubmitted = { pin -> viewModel.submitPin(pin) },
                    onFingerprintClick = { viewModel.submitPin("1234") } // Mock biometric bypass
                )
            } else {
                // Main Application Workspace
                MainWorkspace(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainWorkspace(viewModel: FinanceViewModel) {
    var activeTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    val visibleTabs = remember { MainTab.values() }
    val language by viewModel.language.collectAsState()

    Scaffold(
        bottomBar = {
            val outlineColor = Color(0xFF1F1F1F)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF070707))
                    .drawBehind {
                        drawLine(
                            color = outlineColor,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleTabs.forEach { tab ->
                    val isSelected = activeTab == tab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { activeTab = tab }
                            .width(72.dp)
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color(0xFF00FF66).copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                                    .border(2.dp, Color(0xFF00FF66), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.displayName,
                                    tint = Color(0xFF00FF66),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.displayName,
                                    tint = Color(0xFF8E8E93),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tab.displayName.translate(language),
                            color = if (isSelected) Color(0xFF00FF66) else Color(0xFF8E8E93),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = activeTab,
            modifier = Modifier.padding(innerPadding),
            label = "tab_fade"
        ) { tab ->
            when (tab) {
                MainTab.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                MainTab.TRANSACTIONS -> TransactionsScreen(viewModel = viewModel)
                MainTab.ASSISTANT -> AssistantScreen(viewModel = viewModel)
                MainTab.FINANCIAL_TOOLS -> FinancialToolsScreen(viewModel = viewModel)
                MainTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PrivacySplashView(language: LanguageType) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrivacyPolicyContent(
                language = language,
                useThemeColors = false
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Loading indicator
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
