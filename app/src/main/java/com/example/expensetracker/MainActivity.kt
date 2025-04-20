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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.FirebaseApp
import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
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

@RequiresApi(Build.VERSION_CODES.O)
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
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 1500,
            easing = LinearEasing
        ),
        label = stringResource(R.string.fade_animation)
    )

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 1500,
            easing = FastOutSlowInEasing
        ),
        label = stringResource(R.string.scale_animation)
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        onAnimationComplete()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFEDE7F6)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.welcome_to_expense_tracker),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .scale(scale)
                    .alpha(alpha),
                textAlign = TextAlign.Center,
                color = Color(0xFF6A1B9A)
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
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
    var errorMessage by remember { mutableStateOf(viewModel.errorMessage.value) }
    val isDeleting by viewModel.isDeleting.collectAsState()

    // State for category dropdown
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences(stringResource(R.string.expensetrackerprefs), Context.MODE_PRIVATE)
    val defaultCategories = listOf(
        stringResource(R.string.food),
        stringResource(R.string.travel), stringResource(R.string.shopping),
        stringResource(R.string.bills), stringResource(R.string.entertainment),
        stringResource(R.string.other)
    )
    val savedCategories = sharedPreferences.getString(stringResource(R.string.custom_categories), null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    val initialCategories = (defaultCategories + savedCategories).distinct()
    var categories by remember { mutableStateOf(initialCategories) }
    var expanded by remember { mutableStateOf(false) }

    // State for adding new category
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }

    // State for confirmation dialog
    var showDialog by remember { mutableStateOf(false) }

    // State for date range
    val dateFormatter = DateTimeFormatter.ofPattern(stringResource(R.string.mm_dd_yyyy), Locale.US)
    var startDate by remember {
        val savedStart = sharedPreferences.getLong("start_date", -1L)
        mutableStateOf(if (savedStart != -1L) Instant.ofEpochMilli(savedStart).atZone(ZoneId.systemDefault()).toLocalDate() else null)
    }
    var endDate by remember {
        val savedEnd = sharedPreferences.getLong("end_date", -1L)
        mutableStateOf(if (savedEnd != -1L) Instant.ofEpochMilli(savedEnd).atZone(ZoneId.systemDefault()).toLocalDate() else null)
    }
    var dateErrorMessage by remember { mutableStateOf<String?>(null) }

    // Filter expenses based on date range
    val filteredExpenses = remember(expenses, startDate, endDate) {
        if (startDate != null && endDate != null) {
            expenses.filter { expense ->
                val expenseDate = expense.timestamp.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                !expenseDate.isBefore(startDate) && !expenseDate.isAfter(endDate)
            }
        } else {
            expenses
        }
    }
    val filteredTotalExpenses = filteredExpenses.sumOf { it.amount }

    // Update total expenses in ViewModel for notifications
    LaunchedEffect(filteredTotalExpenses) {
        viewModel.updateTotalExpensesForNotifications(filteredTotalExpenses, startDate, endDate)
    }

    // Synchronize errorMessage with viewModel.errorMessage and time it out
    LaunchedEffect(viewModel.errorMessage) {
        errorMessage = viewModel.errorMessage.value
        if (errorMessage != null) {
            kotlinx.coroutines.delay(5000) // 5 seconds
            errorMessage = null
            viewModel.setErrorMessage(null)
        }
    }

    // Time out dateErrorMessage
    LaunchedEffect(dateErrorMessage) {
        if (dateErrorMessage != null) {
            kotlinx.coroutines.delay(5000) // 5 seconds
            dateErrorMessage = null
        }
    }

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
                text = stringResource(R.string.expense_tracker),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!isOnline) {
                Text(
                    text = stringResource(R.string.you_are_offline_changes_will_sync_when_you_reconnect),
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

            dateErrorMessage?.let {
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
                label = { Text(stringResource(R.string.amount)) },
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
                    label = { Text(stringResource(R.string.category)) },
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
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_new_category)) },
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
                    title = { Text(stringResource(R.string.add_new_category)) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = newCategory,
                                onValueChange = { newCategory = it },
                                label = { Text(stringResource(R.string.category_name)) },
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
                                    sharedPreferences.edit() {
                                        putString("custom_categories",
                                            updatedCategories.filter { it !in defaultCategories }
                                                .joinToString(",")
                                        )
                                    }
                                    category = newCategory
                                }
                                newCategory = ""
                                showAddCategoryDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.add))
                        }
                    },
                    dismissButton = {
                        Button(onClick = {
                            newCategory = ""
                            showAddCategoryDialog = false
                        }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            // Date Range Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = startDate?.format(dateFormatter) ?: "",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.start_date)) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    readOnly = true,
                    enabled = isOnline,
                    trailingIcon = {
                        IconButton(onClick = {
                            val picker = android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                                    val currentDate = LocalDate.now()
                                    if (selectedDate.isBefore(currentDate)) {
                                        dateErrorMessage = context.getString(
                                            R.string.you_cannot_select_a_previous_day_please_choose_a_date_on_or_after,
                                            currentDate.format(dateFormatter)
                                        )
                                    } else {
                                        startDate = selectedDate
                                        dateErrorMessage = null
                                        sharedPreferences.edit() {
                                            putLong("start_date",
                                                selectedDate.atStartOfDay(ZoneId.systemDefault())
                                                    .toInstant().toEpochMilli()
                                            )
                                        }
                                        if (endDate != null && endDate!!.isBefore(selectedDate)) {
                                            endDate = null
                                            sharedPreferences.edit() { remove("end_date") }
                                            dateErrorMessage = context.getString(
                                                R.string.end_date_cleared_please_select_a_new_end_date_on_or_after,
                                                selectedDate.format(dateFormatter)
                                            )
                                        }
                                    }
                                },
                                startDate?.year ?: LocalDate.now().year,
                                (startDate?.monthValue ?: LocalDate.now().monthValue) - 1,
                                startDate?.dayOfMonth ?: LocalDate.now().dayOfMonth
                            )
                            picker.show()
                        }) {
                            Text("📅")
                        }
                    }
                )
                OutlinedTextField(
                    value = endDate?.format(dateFormatter) ?: "",
                    onValueChange = {},
                    label = { Text("End Date") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    readOnly = true,
                    enabled = isOnline && startDate != null,
                    trailingIcon = {
                        IconButton(onClick = {
                            if (startDate != null) {
                                val picker = android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                                        if (selectedDate.isBefore(startDate)) {
                                            dateErrorMessage = context.getString(
                                                R.string.end_date_must_be_on_or_after_the_start_date,
                                                startDate!!.format(dateFormatter)
                                            )
                                        } else {
                                            endDate = selectedDate
                                            dateErrorMessage = null
                                            sharedPreferences.edit() {
                                                putLong("end_date",
                                                    selectedDate.atStartOfDay(ZoneId.systemDefault())
                                                        .toInstant().toEpochMilli()
                                                )
                                            }
                                        }
                                    },
                                    endDate?.year ?: startDate!!.year,
                                    (endDate?.monthValue ?: startDate!!.monthValue) - 1,
                                    endDate?.dayOfMonth ?: startDate!!.dayOfMonth
                                )
                                picker.show()
                            }
                        }) {
                            Text("📅")
                        }
                    }
                )
            }

            Button(
                onClick = {
                    startDate = null
                    endDate = null
                    dateErrorMessage = null
                    sharedPreferences.edit() {
                        remove("start_date")
                            .remove("end_date")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isOnline && (startDate != null || endDate != null)
            ) {
                Text(stringResource(R.string.clear_date_filter))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull() ?: 0.0
                    if (amountDouble > 0 && category.isNotBlank()) {
                        viewModel.addExpense(amountDouble, category, isOnline)
                    } else {
                        viewModel.setErrorMessage(context.getString(R.string.please_enter_a_valid_amount_and_category))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isOnline
            ) {
                Text(stringResource(R.string.add_expense))
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
                Text(stringResource(R.string.update_budget))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.enable_notifications),
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
                text = "Total Expenses: $${String.format(Locale.US, "%.2f", filteredTotalExpenses)}",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.expenses),
                style = MaterialTheme.typography.titleMedium
            )
            if (filteredExpenses.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_expenses_added_yet),
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
                    items(filteredExpenses) { expense ->
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
                                        contentDescription = stringResource(R.string.delete_expense),
                                        tint = Color(0xFF6200EE)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                            Text(stringResource(R.string.clear_all_expenses))
                        }
                    }
                }
            }

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text(stringResource(R.string.confirm_clear_all_expenses)) },
                    text = { Text(stringResource(R.string.are_you_sure_you_want_to_delete_all_expenses_this_action_cannot_be_undone)) },
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
                            Text(stringResource(R.string.yes_clear))
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