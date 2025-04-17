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
import androidx.core.content.edit

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
            try {
                // Ensure network is enabled
                db.enableNetwork().await()
                // Attempt to write to Firestore and wait for server confirmation
                db.collection("expenses").document(expense.id).set(expense).await()
                _operationSuccess.value = true
                _errorMessage.value = null
            } catch (e: Exception) {
                _operationSuccess.value = false
                _errorMessage.value = "Failed to add expense: No internet connection"
                // Disable network and clear cache to prevent queued writes
                db.disableNetwork().await()
                db.clearPersistence().await()
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
            try {
                // Ensure network is enabled
                db.enableNetwork().await()
                db.collection("expenses").document(expenseId).delete().await()
                _operationSuccess.value = true
                _errorMessage.value = null
            } catch (e: Exception) {
                _operationSuccess.value = false
                _errorMessage.value = "Failed to delete expense: No internet connection"
                db.disableNetwork().await()
                db.clearPersistence().await()
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
            try {
                // Ensure network is enabled
                db.enableNetwork().await()
                // Fetch all expenses and delete them
                val snapshot = db.collection("expenses").get().await()
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.delete(db.collection("expenses").document(doc.id))
                }
                batch.commit().await()
                _operationSuccess.value = true
                _errorMessage.value = null
            } catch (e: Exception) {
                _operationSuccess.value = false
                _errorMessage.value = "Failed to clear expenses: No internet connection"
                db.disableNetwork().await()
                db.clearPersistence().await()
            }
        }
    }

    fun updateBudget(newBudget: Double) {
        if (newBudget > 0) {
            _budget.value = newBudget
            sharedPreferences.edit() { putFloat("budget", newBudget.toFloat()) }
            checkBudget(_totalExpenses.value)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        sharedPreferences.edit() { putBoolean("notifications_enabled", enabled) }
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