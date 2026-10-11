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
import androidx.compose.ui.graphics.Brush
import com.example.antigravityfinance.theme.InterFontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import com.example.antigravityfinance.service.security.BiometricGate

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
    val context = LocalContext.current

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
        val pinLockedForMs by viewModel.pinLockedForMs.collectAsState()

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
                    lockoutRemainingMs = pinLockedForMs,
                    attemptsRemaining = viewModel.pinAttemptsRemaining(),
                    onPinSubmitted = { pin -> viewModel.submitPin(pin) },
                    // Unlocks only on a real BiometricPrompt success. This previously
                    // submitted a hardcoded PIN, which was an authentication bypass.
                    onFingerprintClick = if (BiometricGate.isAvailable(context)) {
                        { BiometricGate.authenticate(context) { viewModel.unlockViaBiometrics() } }
                    } else null
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
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(start = 20.dp, end = 20.dp, bottom = 26.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(59.dp)
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(30.dp))
                        .background(
                            color = Color(0xE4101412),
                            shape = RoundedCornerShape(30.dp)
                        )
                        .border(1.dp, Color(0xFF26312C), RoundedCornerShape(30.dp)),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleTabs.forEach { tab ->
                        val isSelected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) { activeTab = tab },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 6.dp)
                                        .width(20.dp)
                                        .height(3.dp)
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(Color(0xFF32E68C), Color(0xFF8CF5C5))
                                            ),
                                            shape = RoundedCornerShape(1.5.dp)
                                        )
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.displayName,
                                    tint = if (isSelected) Color(0xFF32E68C) else Color(0xFF9AA59F),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.displayName.translate(language),
                                    color = if (isSelected) Color(0xFF32E68C) else Color(0xFF9AA59F),
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = InterFontFamily
                                )
                            }
                        }
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
                MainTab.DASHBOARD -> DashboardScreen(viewModel = viewModel, snackbarHostState = snackbarHostState)
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
