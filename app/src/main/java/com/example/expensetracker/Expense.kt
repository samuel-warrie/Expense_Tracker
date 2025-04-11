package com.example.expensetracker
import com.google.firebase.Timestamp
data class Expense(
    val id: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val timestamp: Timestamp = Timestamp.now()
)