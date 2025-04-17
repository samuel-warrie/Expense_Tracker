package com.example.expensetracker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.FirebaseApp
import android.Manifest
import android.os.Build
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            // Permission denied handling can be added here if needed
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AppNavigation()
        }
    }
}

@Composable
fun AppNavigation() {
    var showWelcomeScreen by remember { mutableStateOf(true) }

    if (showWelcomeScreen) {
        WelcomeScreen(
            onAnimationComplete = {
                showWelcomeScreen = false
            }
        )
    } else {
        ExpenseTrackerApp()
    }
}

@Composable
fun WelcomeScreen(onAnimationComplete: () -> Unit) {
    // Animation state for fade-in and scale
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 1500,
            easing = LinearEasing
        ),
        label = "Fade Animation"
    )

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 1500,
            easing = FastOutSlowInEasing
        ),
        label = "Scale Animation"
    )

    // Trigger the transition to the main screen after the animation completes
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000) // Delay to match animation duration + a bit extra
        onAnimationComplete()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFEDE7F6) // Light Purple background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Welcome to Expense Tracker",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .scale(scale)
                    .alpha(alpha),
                textAlign = TextAlign.Center,
                color = Color(0xFF6A1B9A) // Deep Purple
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerApp(viewModel: ExpenseViewModel = viewModel()) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var budgetInput by remember { mutableStateOf("") }
    val expenses by viewModel.expenses.collectAsState()
    val totalExpenses by viewModel.totalExpenses.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val operationSuccess by viewModel.operationSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()

    // State for category dropdown
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("ExpenseTrackerPrefs", Context.MODE_PRIVATE)
    val defaultCategories = listOf("Food", "Travel", "Shopping", "Bills", "Entertainment", "Other")
    val savedCategories = sharedPreferences.getString("custom_categories", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    val initialCategories = (defaultCategories + savedCategories).distinct()
    var categories by remember { mutableStateOf(initialCategories) }
    var expanded by remember { mutableStateOf(false) }

    // State for adding new category
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }

    // State for confirmation dialog
    var showDialog by remember { mutableStateOf(false) }

    val isOnline by remember {
        mutableStateOf(
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).run {
                val network = activeNetwork
                if (network != null) {
                    val capabilities: NetworkCapabilities? = getNetworkCapabilities(network)
                    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                } else {
                    false
                }
            }
        )
    }

    // Clear fields when operation succeeds
    LaunchedEffect(operationSuccess) {
        if (operationSuccess) {
            amount = ""
            category = ""
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Expense Tracker",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!isOnline) {
                Text(
                    text = "You are offline. Changes will sync when you reconnect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isOnline
            )

            // Category Dropdown using ExposedDropdownMenuBox
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded && isOnline },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = isOnline),
                    enabled = isOnline,
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                expanded = false
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    // Add New Category option
                    DropdownMenuItem(
                        text = { Text("Add New Category") },
                        onClick = {
                            showAddCategoryDialog = true
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Dialog for adding a new category
            if (showAddCategoryDialog) {
                AlertDialog(
                    onDismissRequest = { showAddCategoryDialog = false },
                    title = { Text("Add New Category") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = newCategory,
                                onValueChange = { newCategory = it },
                                label = { Text("Category Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newCategory.isNotBlank() && newCategory !in categories) {
                                    val updatedCategories = categories + newCategory
                                    categories = updatedCategories
                                    // Save to SharedPreferences
                                    sharedPreferences.edit()
                                        .putString("custom_categories", updatedCategories.filter { it !in defaultCategories }.joinToString(","))
                                        .apply()
                                    category = newCategory // Set the new category as selected
                                }
                                newCategory = ""
                                showAddCategoryDialog = false
                            }
                        ) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        Button(onClick = {
                            newCategory = ""
                            showAddCategoryDialog = false
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Add Expense Button without Loading Indicator
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull() ?: 0.0
                    if (amountDouble > 0 && category.isNotBlank()) {
                        viewModel.addExpense(amountDouble, category, isOnline)
                    } else {
                        viewModel.setErrorMessage("Please enter a valid amount and category")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isOnline
            ) {
                Text("Add Expense")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = budgetInput,
                onValueChange = { budgetInput = it },
                label = { Text("Set Budget (Current: $$budget)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isOnline
            )
            Button(
                onClick = {
                    val newBudget = budgetInput.toDoubleOrNull() ?: 0.0
                    if (newBudget > 0) {
                        viewModel.updateBudget(newBudget)
                        budgetInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isOnline
            ) {
                Text("Update Budget")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Enable Notifications",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.toggleNotifications(enabled)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Total Expenses: $${String.format(Locale.US, "%.2f", totalExpenses)}",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Expenses:",
                style = MaterialTheme.typography.titleMedium
            )
            if (expenses.isEmpty()) {
                Text(
                    text = "No expenses added yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .border(1.dp, Color.Gray)
                        .padding(8.dp)
                ) {
                    items(expenses) { expense ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = expense.category,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", expense.amount)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        viewModel.deleteExpense(expense.id, isOnline)
                                    },
                                    enabled = isOnline
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Expense",
                                        tint = Color(0xFF6200EE) // Purple color
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Clear All Expenses Button with Loading Indicator
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = { showDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isOnline,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6200EE)
                            )
                        ) {
                            Text("Clear All Expenses")
                        }
                    }
                }
            }

            // Confirmation Dialog
            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Confirm Clear All Expenses") },
                    text = { Text("Are you sure you want to delete all expenses? This action cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.clearAllExpenses(isOnline)
                                showDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6200EE)
                            )
                        ) {
                            Text("Yes, Clear")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}