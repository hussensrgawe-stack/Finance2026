
package com.rasidi.smart

import android.content.Context
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat

data class Transaction(
    val title: String,
    val amount: Double,
    val currency: String,
    val type: String,
    val account: String
)

private const val PREFS_NAME = "rasidi_preferences"
private const val TRANSACTIONS_KEY = "saved_transactions"

private fun loadTransactions(context: Context): List<Transaction> {
    val prefs = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    val json = prefs.getString(TRANSACTIONS_KEY, null)
        ?: return emptyList()

    return try {
        val array = JSONArray(json)
        val result = mutableListOf<Transaction>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)

            result.add(
                Transaction(
                    title = item.getString("title"),
                    amount = item.getDouble("amount"),
                    currency = item.getString("currency"),
                    type = item.getString("type"),
                    account = item.optString("account", "نقدي")
                )
            )
        }

        result
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveTransactions(
    context: Context,
    transactions: List<Transaction>
) {
    val array = JSONArray()

    transactions.forEach { transaction ->
        val item = JSONObject().apply {
            put("title", transaction.title)
            put("amount", transaction.amount)
            put("currency", transaction.currency)
            put("type", transaction.type)
            put("account", transaction.account)
        }

        array.put(item)
    }

    context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    ).edit()
        .putString(TRANSACTIONS_KEY, array.toString())
        .apply()
}

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
    val context = androidx.compose.ui.platform.LocalContext.current

    var salary by remember { mutableStateOf("") }
    var expense by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SYP") }
    var account by remember { mutableStateOf("شام كاش") }
    var errorMessage by remember { mutableStateOf("") }

    val transactions = remember(context) {
        mutableStateListOf<Transaction>().apply {
            addAll(loadTransactions(context))
        }
    }

    val incomeTotal = transactions
        .filter { it.type == "دخل" && it.currency == currency }
        .sumOf { it.amount }

    val expenseTotal = transactions
        .filter { it.type == "مصروف" && it.currency == currency }
        .sumOf { it.amount }

    val numberFormat = remember {
        DecimalFormat("#,##0.##")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مصروفك") }
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
                Text(
                    "إدارة أموالك الشخصية",
                    style = MaterialTheme.typography.titleLarge
                )
                Text("تابع دخلك ومصاريفك بسهولة")
            }

            item {
                Text("العملة")

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                Text(
                    "إضافة حركة مالية",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        errorMessage = ""
                    },
                    label = { Text("وصف الحركة") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = salary,
                    onValueChange = {
                        salary = it
                        errorMessage = ""
                    },
                    label = { Text("دخل (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = expense,
                    onValueChange = {
                        expense = it
                        errorMessage = ""
                    },
                    label = { Text("مصروف (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                var expanded by remember {
                    mutableStateOf(false)
                }

                Box {
                    OutlinedButton(
                        onClick = { expanded = true }
                    ) {
                        Text("الحساب: $account")
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
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

                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        val incomeText = salary.trim()
                        val expenseText = expense.trim()

                        val income = incomeText
                            .replace(',', '.')
                            .toDoubleOrNull()

                        val cost = expenseText
                            .replace(',', '.')
                            .toDoubleOrNull()

                        val validIncome = income != null &&
                            income.isFinite() && income > 0

                        val validExpense = cost != null &&
                            cost.isFinite() && cost > 0

                        val invalidIncome = incomeText.isNotEmpty() &&
                            !validIncome

                        val invalidExpense = expenseText.isNotEmpty() &&
                            !validExpense

                        when {
                            description.isBlank() -> {
                                errorMessage = "يرجى إدخال وصف الحركة"
                            }

                            invalidIncome || invalidExpense -> {
                                errorMessage =
                                    "يرجى إدخال مبالغ صحيحة أكبر من صفر"
                            }

                            !validIncome && !validExpense -> {
                                errorMessage =
                                    "أدخل مبلغ الدخل أو المصروف على الأقل"
                            }

                            else -> {
                                if (validIncome) {
                                    transactions.add(
                                        Transaction(
                                            title = description.trim(),
                                            amount = income!!,
                                            currency = currency,
                                            type = "دخل",
                                            account = account
                                        )
                                    )
                                }

                                if (validExpense) {
                                    transactions.add(
                                        Transaction(
                                            title = description.trim(),
                                            amount = cost!!,
                                            currency = currency,
                                            type = "مصروف",
                                            account = account
                                        )
                                    )
                                }

                                saveTransactions(
                                    context,
                                    transactions.toList()
                                )

                                salary = ""
                                expense = ""
                                description = ""
                                errorMessage = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ الحركة")
                }
            }

            item {
                Text(
                    "ملخص الحساب",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    "إجمالي الدخل: " +
                        "${numberFormat.format(incomeTotal)} $currency"
                )

                Text(
                    "إجمالي المصروفات: " +
                        "${numberFormat.format(expenseTotal)} $currency"
                )

                Text(
                    "الرصيد المسجل: " +
                        "${numberFormat.format(incomeTotal - expenseTotal)} $currency"
                )
            }

            item {
                Text(
                    "الحركات المسجلة (${transactions.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(
                items = transactions.reversed(),
                key = {
                    "${it.title}_${it.amount}_${it.currency}_${it.type}_${it.account}_${transactions.indexOf(it)}"
                }
            ) { transaction ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            transaction.title,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            "${transaction.type}: " +
                                "${numberFormat.format(transaction.amount)} " +
                                transaction.currency
                        )

                        Text("الحساب: ${transaction.account}")
                    }
                }
            }
        }
    }
}
