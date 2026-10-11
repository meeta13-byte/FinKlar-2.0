package com.example.antigravityfinance.ui.screens

import android.widget.Toast
import com.example.antigravityfinance.data.model.LanguageType
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antigravityfinance.ui.viewmodel.FinanceViewModel
import com.example.antigravityfinance.ui.viewmodel.OtpState
import com.example.antigravityfinance.ui.viewmodel.PhoneAuthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

enum class OnboardingPhase {
    ENTER_DETAILS,
    CHOOSE_LANGUAGE,
    VERIFY_OTP
}

@Composable
fun OnboardingScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var phase by remember { mutableStateOf(OnboardingPhase.ENTER_DETAILS) }

    // Form inputs
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }

    var agreedToTerms by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    val phoneAuth: PhoneAuthViewModel = viewModel()
    val otpState by phoneAuth.state.collectAsState()
    val currentLanguage by viewModel.language.collectAsState()

    val isSending = otpState is OtpState.Sending
    val isVerifying = otpState is OtpState.Verifying

    // Error states
    var nameError by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    var otpError by remember { mutableStateOf(false) }

    // Errors surfaced by Firebase Phone Auth (wrong code, too many requests, etc).
    val otpMessage = (otpState as? OtpState.Error)?.message

    LaunchedEffect(otpState) {
        when (val s = otpState) {
            is OtpState.CodeSent -> {
                phase = OnboardingPhase.VERIFY_OTP
                otpError = false
            }
            is OtpState.Success -> viewModel.completeRegistration(
                uid = s.uid,
                name = name.trim(),
                email = email.trim(),
                phone = "+91" + phone.trim()
            )
            else -> Unit
        }
    }

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Decorative background top banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(gradientBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Welcome to FinKlar 2.0",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                )
                Text(
                    text = "Offline-first smart personal money manager",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Card containing form
        Card(
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 190.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Crossfade(targetState = phase, label = "phase_crossfade") { currentPhase ->
                    when (currentPhase) {
                        OnboardingPhase.ENTER_DETAILS -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Create Your Account",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start
                                )
                                Text(
                                    text = "Please enter your details to register and verify your device via Twilio SMS.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = name,
                                    onValueChange = {
                                        name = it
                                        nameError = false
                                    },
                                    label = { Text("Full Name") },
                                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                    isError = nameError,
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = {
                                        email = it
                                        emailError = false
                                    },
                                    label = { Text("Email Address") },
                                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                                    isError = emailError,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { input ->
                                        if (input.length <= 10 && input.all { it.isDigit() }) {
                                            phone = input
                                            phoneError = false
                                        }
                                    },
                                    label = { Text("Phone Number") },
                                    placeholder = { Text("e.g. 9876543210", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                                    leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                                    isError = phoneError,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Terms and conditions acceptance row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = agreedToTerms,
                                        onCheckedChange = { agreedToTerms = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary,
                                            uncheckedColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "I agree to the ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "Terms & Conditions",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .clickable { showTermsDialog = true }
                                    )
                                }

                                if (showTermsDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showTermsDialog = false },
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        title = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Rounded.Security,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Terms & Encryption Policy",
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        },
                                        text = {
                                            Column(
                                                modifier = Modifier
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(vertical = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Text(
                                                    text = "Welcome to FinKlar 2.0, your secure offline-first financial partner.",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                
                                                Text(
                                                    text = "1. End-to-End Encryption\nAll your transaction data, account balances, and credentials are encrypted on-device using AES-256-GCM. Decryption keys are stored securely in Android KeyStore.",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                
                                                Text(
                                                    text = "2. Absolute Privacy\nFinKlar 2.0 is an offline-first app. Your financial records, SMS histories, and budgets are never stored, transmitted, or shared with external servers. All processing runs entirely on your device.",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                
                                                Text(
                                                    text = "3. Twilio SMS Verification\nWe use Twilio SMS verification to uniquely bind your account to this device. Your phone number is only used to verify your identity and is not shared with any third party.",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                
                                                Text(
                                                    text = "4. Consent\nBy registering, you acknowledge and agree that your data will be securely managed locally and that you take responsibility for securing your device with a PIN/biometrics.",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    agreedToTerms = true
                                                    showTermsDialog = false
                                                }
                                            ) {
                                                Text("Accept")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showTermsDialog = false }) {
                                                Text("Close")
                                            }
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        nameError = name.trim().isBlank()
                                        emailError = email.trim().isBlank() || !email.contains("@")
                                        phoneError = phone.trim().length != 10

                                        if (!nameError && !emailError && !phoneError) {
                                            phase = OnboardingPhase.CHOOSE_LANGUAGE
                                        } else {
                                            Toast.makeText(context, "Please correct all inputs", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = agreedToTerms,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text("Continue", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        OnboardingPhase.CHOOSE_LANGUAGE -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Select App Language".translate(currentLanguage),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start
                                )
                                Text(
                                    text = "Choose your preferred language for the interface and AI chatbot assistant.".translate(currentLanguage),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // English Card Option
                                Card(
                                    onClick = { viewModel.updateLanguage(LanguageType.ENGLISH) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (currentLanguage == LanguageType.ENGLISH) 
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) 
                                        else 
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    border = BorderStroke(
                                        width = 2.dp,
                                        color = if (currentLanguage == LanguageType.ENGLISH) 
                                            MaterialTheme.colorScheme.primary 
                                            else Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "English",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Use English for app interface & chatbot",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        if (currentLanguage == LanguageType.ENGLISH) {
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                // Hindi Card Option
                                Card(
                                    onClick = { viewModel.updateLanguage(LanguageType.HINDI) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (currentLanguage == LanguageType.HINDI) 
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) 
                                        else 
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    border = BorderStroke(
                                        width = 2.dp,
                                        color = if (currentLanguage == LanguageType.HINDI) 
                                            MaterialTheme.colorScheme.primary 
                                            else Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "हिंदी (Hindi)",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "ऐप इंटरफ़ेस और चैटबॉट के लिए हिंदी का उपयोग करें",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        if (currentLanguage == LanguageType.HINDI) {
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Send failures happen on this screen, so surface them here too -
                                // otherwise the button just appears to do nothing.
                                if (otpMessage != null) {
                                    Text(
                                        text = otpMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { phase = OnboardingPhase.ENTER_DETAILS },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                    ) {
                                        Text("Back".translate(currentLanguage), fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            val act = activity
                                            if (act == null) {
                                                Toast.makeText(context, "Could not start verification.", Toast.LENGTH_LONG).show()
                                            } else {
                                                phoneAuth.sendCode(act, "+91" + phone.trim())
                                            }
                                        },
                                        enabled = !isSending && activity != null,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1.5f)
                                            .height(50.dp)
                                    ) {
                                        if (isSending) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Text("Send OTP & Continue".translate(currentLanguage), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        OnboardingPhase.VERIFY_OTP -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Enter Verification Code".translate(currentLanguage),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start
                                )
                                Text(
                                    text = if (otpState is OtpState.TimedOut) {
                                        "Automatic code detection timed out. Enter the 6-digit code manually, or resend."
                                    } else {
                                        "${"We sent a 6-digit OTP code to".translate(currentLanguage)} +91$phone. ${"Please enter it below to confirm your phone number.".translate(currentLanguage)}"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                if (otpMessage != null) {
                                    Text(
                                        text = otpMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                OutlinedTextField(
                                    value = otp,
                                    onValueChange = {
                                        otp = it
                                        otpError = false
                                    },
                                    label = { Text("6-Digit OTP Code".translate(currentLanguage)) },
                                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                    isError = otpError,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { phase = OnboardingPhase.CHOOSE_LANGUAGE }) {
                                        Icon(Icons.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Back".translate(currentLanguage))
                                    }

                                    TextButton(
                                        onClick = {
                                            val act = activity
                                            if (act != null) phoneAuth.resendCode(act)
                                        },
                                        enabled = !isSending && activity != null
                                    ) {
                                        Text("Resend Code".translate(currentLanguage))
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        otpError = otp.trim().length != 6
                                        if (!otpError) {
                                            phoneAuth.verifyCode(otp.trim())
                                        } else {
                                            Toast.makeText(context, "OTP must be exactly 6 digits".translate(currentLanguage), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isVerifying,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    if (isVerifying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Text("Verify & Continue".translate(currentLanguage), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Divider(modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(16.dp))

            }
        }
    }
}

/** Resolves the hosting Activity, which PhoneAuthOptions.setActivity() requires. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
