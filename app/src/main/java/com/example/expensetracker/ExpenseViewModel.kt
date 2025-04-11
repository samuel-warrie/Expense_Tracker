package com.example.expensetracker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    init {
        NotificationHelper.createNotificationChannel(application)
        listenToExpenses()
    }

    fun addExpense(amount: Double, category: String) {
        val expense = Expense(
            id = db.collection("expenses").document().id,
            amount = amount,
            category = category,
            timestamp = Timestamp.now()
        )
        viewModelScope.launch {
            db.collection("expenses").document(expense.id).set(expense).await()
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            db.collection("expenses").document(expenseId).delete().await()
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
        if (total >= 100 && _notificationsEnabled.value) {
            NotificationHelper.showBudgetAlert(getApplication(), total)
        }
    }
}