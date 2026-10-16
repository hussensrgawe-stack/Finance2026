package com.rasidi.smart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Transaction(
    val title: String,
    val amount: Double,
    val currency: String,
    val type: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FinanceApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp() {
    var salary by remember { mutableStateOf("") }
    var expense by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SYP") }
    var account by remember { mutableStateOf("شام كاش") }

    val transactions = remember { mutableStateListOf<Transaction>() }

    val incomeTotal = transactions
        .filter { it.type == "دخل" && it.currency == currency }
        .sumOf { it.amount }

    val expenseTotal = transactions
        .filter { it.type == "مصروف" && it.currency == currency }
        .sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("رصيدي الذكي") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("إدارة أموالك الشخصية", style = MaterialTheme.typography.titleLarge)
                Text("تابع دخلك ومصاريفك بسهولة")
            }

            item {
                Text("العملة")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currency == "SYP",
                        onClick = { currency = "SYP" },
                        label = { Text("الليرة السورية") }
                    )
                    FilterChip(
                        selected = currency == "USD",
                        onClick = { currency = "USD" },
                        label = { Text("الدولار") }
                    )
                }
            }

            item {
                Text("إضافة حركة مالية", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف الحركة") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = salary,
                    onValueChange = { salary = it },
                    label = { Text("دخل") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = expense,
                    onValueChange = { expense = it },
                    label = { Text("مصروف") },
                    modifier = Modifier.fillMaxWidth()
                )

                var expanded by remember { mutableStateOf(false) }

                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text("الحساب: $account")
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("شام كاش") },
                            onClick = {
                                account = "شام كاش"
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("نقدي") },
                            onClick = {
                                account = "نقدي"
                                expanded = false
                            }
                        )
                    }
                }

                Button(
                    onClick = {
                        val income = salary.toDoubleOrNull()
                        val cost = expense.toDoubleOrNull()

                        if (description.isNotBlank() && income != null && income > 0) {
                            transactions.add(
                                Transaction(description, income, currency, "دخل")
                            )
                        }

                        if (description.isNotBlank() && cost != null && cost > 0) {
                            transactions.add(
                                Transaction(description, cost, currency, "مصروف")
                            )
                        }

                        salary = ""
                        expense = ""
                        description = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ الحركة")
                }
            }

            item {
                Text("ملخص الحساب", style = MaterialTheme.typography.titleLarge)
                Text("إجمالي الدخل: $incomeTotal $currency")
                Text("إجمالي المصروفات: $expenseTotal $currency")
                Text("الرصيد المسجل: ${incomeTotal - expenseTotal} $currency")
            }

            item {
                Text("الحركات الأخيرة", style = MaterialTheme.typography.titleMedium)
            }

            items(transactions.reversed()) { transaction ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(transaction.title)
                        Text("${transaction.type}: ${transaction.amount} ${transaction.currency}")
                        Text("الحساب: $account")
                    }
                }
            }
        }
    }
}
