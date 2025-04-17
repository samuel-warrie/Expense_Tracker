package com.example.expensetracker

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FirebaseFirestore.getInstance()
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses

    private val _totalExpenses = MutableStateFlow(0.0)
    val totalExpenses: StateFlow<Double> = _totalExpenses

    private val sharedPreferences = application.getSharedPreferences("ExpenseTrackerPrefs", Context.MODE_PRIVATE)
    private val _budget = MutableStateFlow(sharedPreferences.getFloat("budget", 1000.0f).toDouble())
    val budget: StateFlow<Double> = _budget

    private val _notificationsEnabled = MutableStateFlow(sharedPreferences.getBoolean("notifications_enabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    // Track the success of the last operation
    private val _operationSuccess = MutableStateFlow(true)
    val operationSuccess: StateFlow<Boolean> = _operationSuccess

    // Track the error message for failed operations
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Track whether a deletion operation is in progress
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting

    init {
        NotificationHelper.createNotificationChannel(application)
        // Check connectivity and clear cache if offline on startup
        val isOnline = checkConnectivity(application)
        viewModelScope.launch {
            if (!isOnline) {
                db.disableNetwork().await()
                db.clearPersistence().await()
            } else {
                db.enableNetwork().await()
            }
            listenToExpenses()
        }
    }

    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }

    private fun checkConnectivity(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        return if (network != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            false
        }
    }

    fun addExpense(amount: Double, category: String, isOnline: Boolean) {
        if (!isOnline) {
            _operationSuccess.value = false
            _errorMessage.value = "Cannot add expense: No internet connection"
            return
        }
        val expense = Expense(
            id = db.collection("expenses").document().id,
            amount = amount,
            category = category,
            timestamp = Timestamp.now()
        )
        viewModelScope.launch {
            Log.d("ExpenseViewModel", "addExpense started, isDeleting: ${_isDeleting.value}")
            _isDeleting.value = false // Reset isDeleting to ensure it's not stuck
            Log.d("ExpenseViewModel", "addExpense, isDeleting reset to: ${_isDeleting.value}")
            try {
                Log.d("ExpenseViewModel", "Starting to add expense: $amount, $category")
                // Ensure network is enabled
                db.enableNetwork().await()
                // Attempt to write to Firestore
                db.collection("expenses").document(expense.id).set(expense).await()
                Log.d("ExpenseViewModel", "Expense added successfully")
                _operationSuccess.value = true
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Failed to add expense: ${e.message}")
                _operationSuccess.value = false
                _errorMessage.value = "Failed to add expense: ${e.message}"
                // Disable network and clear cache to prevent queued writes
                db.disableNetwork().await()
                db.clearPersistence().await()
            } finally {
                Log.d("ExpenseViewModel", "addExpense finished, isDeleting: ${_isDeleting.value}")
            }
        }
    }

    fun deleteExpense(expenseId: String, isOnline: Boolean) {
        if (!isOnline) {
            _operationSuccess.value = false
            _errorMessage.value = "Cannot delete expense: No internet connection"
            return
        }
        viewModelScope.launch {
            Log.d("ExpenseViewModel", "deleteExpense started, isDeleting: ${_isDeleting.value}")
            _isDeleting.value = false // Reset isDeleting to ensure it's not stuck
            Log.d("ExpenseViewModel", "deleteExpense, isDeleting reset to: ${_isDeleting.value}")
            try {
                // Ensure network is enabled
                db.enableNetwork().await()
                db.collection("expenses").document(expenseId).delete().await()
                _operationSuccess.value = true
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Failed to delete expense: ${e.message}")
                _operationSuccess.value = false
                _errorMessage.value = "Failed to delete expense: ${e.message}"
                db.disableNetwork().await()
                db.clearPersistence().await()
            } finally {
                Log.d("ExpenseViewModel", "deleteExpense finished, isDeleting: ${_isDeleting.value}")
            }
        }
    }

    fun clearAllExpenses(isOnline: Boolean) {
        if (!isOnline) {
            _operationSuccess.value = false
            _errorMessage.value = "Cannot clear expenses: No internet connection"
            return
        }
        viewModelScope.launch {
            Log.d("ExpenseViewModel", "clearAllExpenses started, setting isDeleting to true")
            _isDeleting.value = true // Set deleting state to true
            try {
                // Ensure network is enabled
                db.enableNetwork().await()
                // Fetch all expenses and delete them
                val snapshot = db.collection("expenses").get().await()
                if (snapshot.isEmpty) {
                    _operationSuccess.value = true
                    _errorMessage.value = null
                    _isDeleting.value = false
                    Log.d("ExpenseViewModel", "clearAllExpenses: No expenses to delete, isDeleting: ${_isDeleting.value}")
                    return@launch
                }
                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.delete(db.collection("expenses").document(doc.id))
                }
                batch.commit().await()
                _operationSuccess.value = true
                _errorMessage.value = null
                Log.d("ExpenseViewModel", "clearAllExpenses completed successfully")
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Failed to clear expenses: ${e.message}")
                _operationSuccess.value = false
                _errorMessage.value = "Failed to clear expenses: ${e.message}"
            } finally {
                _isDeleting.value = false // Reset deleting state
                Log.d("ExpenseViewModel", "clearAllExpenses finished, isDeleting: ${_isDeleting.value}")
            }
        }
    }

    fun updateBudget(newBudget: Double) {
        if (newBudget > 0) {
            _budget.value = newBudget
            sharedPreferences.edit().putFloat("budget", newBudget.toFloat()).apply()
            checkBudget(_totalExpenses.value)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        sharedPreferences.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    private fun listenToExpenses() {
        db.collection("expenses").addSnapshotListener { snapshot, error ->
            if (error != null) {
                _errorMessage.value = "Failed to fetch expenses: ${error.message}"
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val expenseList = snapshot.documents.mapNotNull { it.toObject(Expense::class.java) }
                _expenses.value = expenseList
                val total = expenseList.sumOf { it.amount }
                _totalExpenses.value = total
                checkBudget(total)
            }
        }
    }

    private fun checkBudget(total: Double) {
        if (_notificationsEnabled.value) {
            val budgetValue = _budget.value
            // Check if total expenses are within $300 of the budget but not exceeding it
            if (total in (budgetValue - 300.0)..budgetValue) {
                NotificationHelper.showApproachingBudgetAlert(getApplication(), total, budgetValue)
            }
            // Check if total expenses exceed the budget
            if (total > budgetValue) {
                NotificationHelper.showBudgetAlert(getApplication(), total, budgetValue)
            }
        }
    }
}