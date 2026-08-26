package com.example.antigravityfinance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.example.antigravityfinance.data.model.*
import com.example.antigravityfinance.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import com.example.antigravityfinance.theme.*

@Composable
fun WalletsScreen(
    viewModel: FinanceViewModel,
    onWalletClick: (Wallet) -> Unit,
    onViewAllTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wallets by viewModel.allWallets.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val language by viewModel.language.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Virtual Wallets".translate(language),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onViewAllTransactions) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = "All Transactions",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Wallet".translate(language))
                }
            }
        }

        if (wallets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No wallets created yet.".translate(language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(wallets, key = { it.id }) { wallet ->
                    val spent = transactions.filter { it.walletId == wallet.id && it.status == com.example.antigravityfinance.data.model.TransactionStatus.CONFIRMED && !it.isIncome }.sumOf { it.amount }
                    WalletCard(
                        wallet = wallet,
                        spentAmount = spent,
                        currencySymbol = currency.symbol,
                        language = language,
                        onClick = { onWalletClick(wallet) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddWalletDialog(
            language = language,
            onDismiss = { showAddDialog = false },
            onSave = { name, purpose, investedAmount, initialAmount, isDefault ->
                viewModel.addWallet(name, purpose, investedAmount, initialAmount, isDefault)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun WalletCard(
    wallet: Wallet,
    spentAmount: Double,
    currencySymbol: String,
    language: com.example.antigravityfinance.data.model.LanguageType,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (wallet.isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (wallet.isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (wallet.isDefault) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Default".translate(language),
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = "$currencySymbol${String.format("%,.2f", wallet.balance)}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!wallet.purpose.isNullOrBlank()) {
                Text(
                    text = wallet.purpose,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (wallet.initialAmount > 0.0) {
                val remaining = wallet.initialAmount - spentAmount
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Set: $currencySymbol${String.format("%,.0f", wallet.initialAmount)} | Spent: $currencySymbol${String.format("%,.0f", spentAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Remaining: $currencySymbol${String.format("%,.0f", remaining)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (remaining >= 0.0) AccentEmerald else MaterialTheme.colorScheme.error
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (spentAmount / wallet.initialAmount).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = if (spentAmount > wallet.initialAmount) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            if (wallet.name.contains("Savings", ignoreCase = true) || wallet.investedAmount > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Invested: $currencySymbol${String.format("%,.0f", wallet.investedAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentOrange
                    )
                    Text(
                        text = "Cash: $currencySymbol${String.format("%,.0f", wallet.balance - wallet.investedAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentEmerald
                    )
                }
            }
        }
    }
}

@Composable
fun AddWalletDialog(
    language: com.example.antigravityfinance.data.model.LanguageType,
    onDismiss: () -> Unit,
    onSave: (name: String, purpose: String?, investedAmount: Double, initialAmount: Double, isDefault: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var investedAmountText by remember { mutableStateOf("") }
    var initialAmountText by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151B18)),
            border = BorderStroke(1.dp, Color(0xFF26312C)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier.shadow(16.dp, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "New Virtual Wallet".translate(language),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Wallet Name".translate(language)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose / Description".translate(language)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = initialAmountText,
                    onValueChange = { initialAmountText = it },
                    label = { Text("Initial Set Amount (Limit)".translate(language)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = investedAmountText,
                    onValueChange = { investedAmountText = it },
                    label = { Text("Invested Portion (Optional)".translate(language)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Set as Default Wallet".translate(language), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel".translate(language))
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val invested = investedAmountText.toDoubleOrNull() ?: 0.0
                                val initial = initialAmountText.toDoubleOrNull() ?: 0.0
                                onSave(name.trim(), purpose.trim().ifBlank { null }, invested, initial, isDefault)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Create".translate(language))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WalletDetailScreen(
    walletId: Int,
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wallets by viewModel.allWallets.collectAsState()
    val wallet = remember(wallets, walletId) { wallets.find { it.id == walletId } }
    
    if (wallet == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Wallet not found.")
        }
        return
    }

    val currency by viewModel.currency.collectAsState()
    val language by viewModel.language.collectAsState()
    val context = LocalContext.current

    val transactions by viewModel.allTransactions.collectAsState()
    val walletTransactions = remember(transactions, walletId) {
        transactions.filter { it.walletId == walletId }
    }
    var showEditDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color(0xFF080A09),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101412))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF1B241F), RoundedCornerShape(12.dp))
                        .border(0.7.dp, Color(0xFF26312C), RoundedCornerShape(12.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFFF4F7F5),
                        modifier = Modifier.size(20.dp)
                    )
                }

                val nameParts = wallet.name.split(" ")
                val annotatedTitle = buildAnnotatedString {
                    if (nameParts.size > 1) {
                        val firstPart = nameParts.dropLast(1).joinToString(" ") + " "
                        val lastPart = nameParts.last()
                        withStyle(SpanStyle(color = Color(0xFFF4F7F5))) { append(firstPart) }
                        withStyle(SpanStyle(color = Color(0xFF19C37D))) { append(lastPart) }
                    } else {
                        withStyle(SpanStyle(color = Color(0xFF19C37D))) { append(wallet.name) }
                    }
                }

                Text(
                    text = annotatedTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF1B241F), RoundedCornerShape(12.dp))
                            .border(0.7.dp, Color(0xFF26312C), RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setDefaultWallet(wallet.id)
                                android.widget.Toast.makeText(context, "${wallet.name} set as default wallet", android.widget.Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (wallet.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Set Default",
                            tint = if (wallet.isDefault) Color(0xFFF5B942) else Color(0xFF9AA59F),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF1B241F), RoundedCornerShape(12.dp))
                            .border(0.7.dp, Color(0xFF26312C), RoundedCornerShape(12.dp))
                            .clickable { showEditDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF8CF5C5),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF1B241F), RoundedCornerShape(12.dp))
                            .border(0.7.dp, Color(0xFF26312C), RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.deleteWallet(
                                    wallet = wallet,
                                    onCornerCaseSuccess = {
                                        android.widget.Toast.makeText(context, "Wallet deleted successfully", android.widget.Toast.LENGTH_SHORT).show()
                                        onBack()
                                    },
                                    onError = { error ->
                                        android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF5C67),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080A09))
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF151B18), Color(0xFF101412)),
                            start = Offset(0f, 0f),
                            end = Offset.Infinite
                        )
                    )
                    .border(0.7.dp, Color(0xFF26312C), RoundedCornerShape(30.dp))
                    .drawBehind {
                        val curveColor = Color(0xFF0D6B47).copy(alpha = 0.15f)
                        val path1 = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.6f, size.height)
                            quadraticBezierTo(
                                size.width * 0.8f, size.height * 0.3f,
                                size.width, size.height * 0.2f
                            )
                        }
                        drawPath(
                            path = path1,
                            color = curveColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )
                        
                        val path2 = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.72f, size.height)
                            quadraticBezierTo(
                                size.width * 0.88f, size.height * 0.45f,
                                size.width, size.height * 0.35f
                            )
                        }
                        drawPath(
                            path = path2,
                            color = curveColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )

                        val glowHeight = 12.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF19C37D).copy(alpha = 0.12f)),
                                startY = size.height - glowHeight,
                                endY = size.height
                            ),
                            topLeft = Offset(0f, size.height - glowHeight),
                            size = androidx.compose.ui.geometry.Size(size.width, glowHeight)
                        )
                    }
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(1.5.dp)
                                    .height(12.dp)
                                    .background(Color(0xFF19C37D), RoundedCornerShape(1.dp))
                            )
                            Text(
                                text = "Current Balance".translate(language),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = InterFontFamily
                                ),
                                color = Color(0xFF9AA59F)
                            )
                        }
                        Text(
                            text = "${currency.symbol}${String.format("%,.2f", wallet.balance)}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = InterFontFamily,
                                fontSize = 32.sp
                            ),
                            color = Color(0xFFF4F7F5)
                        )
                        if (!wallet.purpose.isNullOrBlank()) {
                            Text(
                                text = wallet.purpose,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily),
                                color = Color(0xFF9AA59F)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color(0xFF1B241F), CircleShape)
                            .border(1.dp, Color(0xFF32E68C).copy(alpha = 0.5f), CircleShape)
                            .drawBehind {
                                drawCircle(
                                    color = Color(0xFF19C37D).copy(alpha = 0.08f),
                                    radius = size.minDimension / 2
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color(0xFF32E68C),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .background(Color(0xFF19C37D), RoundedCornerShape(1.5.dp))
                    )
                    Text(
                        text = "Transaction History".translate(language),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily
                        ),
                        color = Color(0xFFF4F7F5)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF1B241F), RoundedCornerShape(8.dp))
                        .border(0.7.dp, Color(0xFF26312C), RoundedCornerShape(8.dp))
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = "Filter",
                        tint = Color(0xFF19C37D),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (walletTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No transactions for this wallet.".translate(language),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = InterFontFamily),
                        color = Color(0xFF9AA59F)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(walletTransactions, key = { it.id }) { tx ->
                        var showMenu by remember { mutableStateOf(false) }
                        var showWalletSelection by remember { mutableStateOf(false) }

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd) {
                                    viewModel.deleteTransaction(tx)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Transaction deleted",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.undoDeleteTransaction(tx)
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = false,
                                backgroundContent = {
                                    val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                        Color.Red
                                    } else {
                                        Color.Transparent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(color, shape = RoundedCornerShape(12.dp))
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.White
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { },
                                            onLongClick = { showMenu = true }
                                        )
                                ) {
                                    TransactionRow(
                                        tx = tx,
                                        currency = currency,
                                        onClick = {}
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier
                                    .background(Color.Black)
                                    .border(0.5.dp, Color(0xFF26312C), RoundedCornerShape(8.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Transfer Transaction", color = Color(0xFFF4F7F5), fontFamily = InterFontFamily) },
                                    onClick = {
                                        showMenu = false
                                        showWalletSelection = true
                                    }
                                )
                            }

                            if (showWalletSelection) {
                                val otherWallets = wallets.filter { it.id != walletId }
                                if (otherWallets.isEmpty()) {
                                    android.widget.Toast.makeText(context, "No other wallets available", android.widget.Toast.LENGTH_SHORT).show()
                                    showWalletSelection = false
                                } else {
                                    var expandedWallets by remember { mutableStateOf(true) }
                                    DropdownMenu(
                                        expanded = expandedWallets,
                                        onDismissRequest = {
                                            expandedWallets = false
                                            showWalletSelection = false
                                        },
                                        modifier = Modifier
                                            .background(Color.Black)
                                            .border(0.5.dp, Color(0xFF26312C), RoundedCornerShape(8.dp))
                                    ) {
                                        otherWallets.forEach { targetWallet ->
                                            DropdownMenuItem(
                                                text = { Text(targetWallet.name, color = Color(0xFFF4F7F5), fontFamily = InterFontFamily) },
                                                onClick = {
                                                    viewModel.shiftTransactionWallet(tx.id, targetWallet.id)
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Transferred to ${targetWallet.name}",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                    expandedWallets = false
                                                    showWalletSelection = false
                                                }
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
}

    if (showEditDialog) {
        EditWalletDialog(
            wallet = wallet,
            language = language,
            onDismiss = { showEditDialog = false },
            onSave = { updatedWallet ->
                viewModel.updateWallet(updatedWallet)
                showEditDialog = false
            }
        )
    }

    if (showTransferDialog) {
        TransferDialog(
            sourceWallet = wallet,
            walletsList = wallets,
            currencySymbol = currency.symbol,
            language = language,
            onDismiss = { showTransferDialog = false },
            onTransfer = { toWalletId, amount, note ->
                viewModel.transferMoney(
                    fromWalletId = wallet.id,
                    toWalletId = toWalletId,
                    amount = amount,
                    note = note,
                    onSuccess = {
                        android.widget.Toast.makeText(context, "Transfer complete!", android.widget.Toast.LENGTH_SHORT).show()
                        showTransferDialog = false
                    },
                    onError = { error ->
                        android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }
}

// Wrapper extension to avoid name conflict in parameter closure
private fun FinanceViewModel.deleteWallet(wallet: Wallet, onCornerCaseSuccess: () -> Unit, onError: (String) -> Unit) {
    this.deleteWallet(wallet, onSuccess = onCornerCaseSuccess, onError = onError)
}

@Composable
fun EditWalletDialog(
    wallet: Wallet,
    language: com.example.antigravityfinance.data.model.LanguageType,
    onDismiss: () -> Unit,
    onSave: (Wallet) -> Unit
) {
    var name by remember { mutableStateOf(wallet.name) }
    var purpose by remember { mutableStateOf(wallet.purpose ?: "") }
    var investedAmountText by remember { mutableStateOf(wallet.investedAmount.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151B18)),
            border = BorderStroke(1.dp, Color(0xFF26312C)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier.shadow(16.dp, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Edit Wallet".translate(language),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Wallet Name".translate(language)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose / Description".translate(language)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = investedAmountText,
                    onValueChange = { investedAmountText = it },
                    label = { Text("Invested Portion".translate(language)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel".translate(language))
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(
                                    wallet.copy(
                                        name = name.trim(),
                                        purpose = purpose.trim().ifBlank { null },
                                        investedAmount = investedAmountText.toDoubleOrNull() ?: 0.0
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save".translate(language))
                    }
                }
            }
        }
    }
}

@Composable
fun TransferDialog(
    sourceWallet: Wallet,
    walletsList: List<Wallet>,
    currencySymbol: String,
    language: com.example.antigravityfinance.data.model.LanguageType,
    onDismiss: () -> Unit,
    onTransfer: (toWalletId: Int, amount: Double, note: String?) -> Unit
) {
    val destinationWallets = remember(walletsList, sourceWallet) {
        walletsList.filter { it.id != sourceWallet.id }
    }
    
    var selectedDestWallet by remember { mutableStateOf(destinationWallets.firstOrNull()) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151B18)),
            border = BorderStroke(1.dp, Color(0xFF26312C)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier.shadow(16.dp, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Transfer Funds".translate(language),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = "From: ${sourceWallet.name} (${currencySymbol}${sourceWallet.balance})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column {
                    Text("To Wallet".translate(language), style = MaterialTheme.typography.labelSmall)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Text(selectedDestWallet?.name ?: "Select Wallet".translate(language))
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .background(Color.Black)
                            .border(0.5.dp, Color(0xFF2C2C2E), RoundedCornerShape(8.dp))
                    ) {
                        destinationWallets.forEach { target ->
                            DropdownMenuItem(
                                text = { Text(target.name) },
                                onClick = {
                                    selectedDestWallet = target
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)".translate(language)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel".translate(language))
                    }
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            val dest = selectedDestWallet
                            if (amount > 0.0 && dest != null) {
                                onTransfer(dest.id, amount, note.trim().ifBlank { null })
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Transfer".translate(language))
                    }
                }
            }
        }
    }
}
