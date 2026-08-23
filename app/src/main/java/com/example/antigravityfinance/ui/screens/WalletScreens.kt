package com.example.antigravityfinance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import com.example.antigravityfinance.data.model.*
import com.example.antigravityfinance.ui.viewmodel.FinanceViewModel
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(wallet.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!wallet.isDefault) {
                        IconButton(
                            onClick = {
                                viewModel.setDefaultWallet(wallet.id)
                                android.widget.Toast.makeText(context, "${wallet.name} set as default wallet", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.StarBorder,
                                contentDescription = "Set as Default",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Default Wallet",
                            tint = AccentOrange,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(
                        onClick = {
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
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Current Balance".translate(language),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${currency.symbol}${String.format("%,.2f", wallet.balance)}",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (!wallet.purpose.isNullOrBlank()) {
                        Text(
                            text = wallet.purpose,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (wallet.name.contains("Savings", ignoreCase = true) || wallet.investedAmount > 0.0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Invested".translate(language), style = MaterialTheme.typography.labelSmall)
                                Text("${currency.symbol}${String.format("%,.2f", wallet.investedAmount)}", fontWeight = FontWeight.Bold, color = AccentOrange)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Available Cash".translate(language), style = MaterialTheme.typography.labelSmall)
                                Text("${currency.symbol}${String.format("%,.2f", wallet.balance - wallet.investedAmount)}", fontWeight = FontWeight.Bold, color = AccentEmerald)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { showTransferDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Transfer Funds".translate(language))
                    }
                }
            }

            Text(
                text = "Transaction History".translate(language),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (walletTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No transactions for this wallet.".translate(language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        TransactionRow(
                            tx = tx,
                            currency = currency,
                            onClick = {}
                        )
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
