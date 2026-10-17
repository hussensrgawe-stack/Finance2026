
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
import androidx.compose.ui.platform.LocalContext
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

private val accounts = listOf("نقدي", "شام كاش")

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
        array.put(JSONObject().apply {
            put("title", transaction.title)
            put("amount", transaction.amount)
            put("currency", transaction.currency)
            put("type", transaction.type)
            put("account", transaction.account)
        })
    }

    context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    ).edit()
        .putString(TRANSACTIONS_KEY, array.toString())
        .apply()
}

private fun openingBalanceKey(
    account: String,
    currency: String
): String {
    val accountKey = if (account == "نقدي") "cash" else "sham"
    return "opening_${accountKey}_${currency}"
}

private fun loadOpeningBalance(
    context: Context,
    account: String,
    currency: String
): Double {
    val prefs = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    return prefs.getString(
        openingBalanceKey(account, currency), "0"
    )?.toDoubleOrNull() ?: 0.0
}

private fun saveOpeningBalance(
    context: Context,
    account: String,
    currency: String,
    amount: Double
) {
    context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    ).edit()
        .putString(
            openingBalanceKey(account, currency),
            amount.toString()
        )
        .apply()
}

private fun calculateBalance(
    context: Context,
    transactions: List<Transaction>,
    account: String,
    currency: String
): Double {
    val opening = loadOpeningBalance(context, account, currency)

    val income = transactions
        .filter {
            it.account == account &&
                it.currency == currency &&
                it.type == "دخل"
        }
        .sumOf { it.amount }

    val expenses = transactions
        .filter {
            it.account == account &&
                it.currency == currency &&
                it.type == "مصروف"
        }
        .sumOf { it.amount }

    return opening + income - expenses
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
    val context = LocalContext.current
    val numberFormat = remember { DecimalFormat("#,##0.##") }

    var salary by remember { mutableStateOf("") }
    var expense by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SYP") }
    var account by remember { mutableStateOf("نقدي") }
    var openingText by remember { mutableStateOf("0") }
    var errorMessage by remember { mutableStateOf("") }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    val transactions = remember(context) {
        mutableStateListOf<Transaction>().apply {
            addAll(loadTransactions(context))
        }
    }

    val openingBalance = loadOpeningBalance(
        context, account, currency
    )

    val selectedBalance = calculateBalance(
        context, transactions, account, currency
    )

    fun parseAmount(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()

    fun formatAmount(amount: Double): String =
        numberFormat.format(amount)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("مصروفك") })
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
                Text("تابع أرصدتك ومصاريفك بشكل مستقل")
            }

            item {
                Text(
                    "الأرصدة المتاحة",
                    style = MaterialTheme.typography.titleLarge
                )

                accounts.forEach { acc ->
                    listOf("SYP", "USD").forEach { curr ->
                        val balance = calculateBalance(
                            context, transactions, acc, curr
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    "$acc — " +
                                        if (curr == "SYP")
                                            "الليرة السورية"
                                        else "الدولار الأمريكي",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${formatAmount(balance)} " +
                                        if (curr == "SYP") "ل.س" else "$",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "تحديد الحساب والعملة",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currency == "SYP",
                        onClick = {
                            currency = "SYP"
                            openingText = loadOpeningBalance(
                                context, account, "SYP"
                            ).toString()
                            errorMessage = ""
                        },
                        label = { Text("الليرة السورية") }
                    )
                    FilterChip(
                        selected = currency == "USD",
                        onClick = {
                            currency = "USD"
                            openingText = loadOpeningBalance(
                                context, account, "USD"
                            ).toString()
                            errorMessage = ""
                        },
                        label = { Text("الدولار") }
                    )
                }

                Box {
                    OutlinedButton(
                        onClick = { accountMenuExpanded = true }
                    ) {
                        Text("الحساب: $account")
                    }

                    DropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = {
                            accountMenuExpanded = false
                        }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc) },
                                onClick = {
                                    account = acc
                                    accountMenuExpanded = false
                                    openingText = loadOpeningBalance(
                                        context, acc, currency
                                    ).toString()
                                    errorMessage = ""
                                }
                            )
                        }
                    }
                }

                Text(
                    "الرصيد الحالي: " +
                        "${formatAmount(selectedBalance)} " +
                        if (currency == "SYP") "ل.س" else "$"
                )
            }

            item {
                Text(
                    "تحديد الرصيد الافتتاحي",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "أدخل المبلغ الموجود فعلياً في هذا الحساب. " +
                        "لا تدخل مجموع أرصدتك الأخرى هنا."
                )

                OutlinedTextField(
                    value = openingText,
                    onValueChange = {
                        openingText = it
                        errorMessage = ""
                    },
                    label = {
                        Text(
                            if (currency == "SYP")
                                "الرصيد الافتتاحي بالليرة"
                            else "الرصيد الافتتاحي بالدولار"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        val amount = parseAmount(openingText)
                        if (amount == null ||
                            !amount.isFinite() || amount < 0
                        ) {
                            errorMessage =
                                "أدخل رصيداً افتتاحياً صحيحاً غير سالب"
                        } else {
                            saveOpeningBalance(
                                context, account, currency, amount
                            )
                            errorMessage = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ الرصيد الافتتاحي")
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

                if (errorMessage.isNotBlank()) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        val incomeText = salary.trim()
                        val expenseText = expense.trim()
                        val income = parseAmount(incomeText)
                        val cost = parseAmount(expenseText)

                        val validIncome = income != null &&
                            income.isFinite() && income > 0
                        val validExpense = cost != null &&
                            cost.isFinite() && cost > 0

                        when {
                            description.isBlank() -> {
                                errorMessage = "يرجى إدخال وصف الحركة"
                            }

                            (incomeText.isNotEmpty() && !validIncome) ||
                                (expenseText.isNotEmpty() && !validExpense) -> {
                                errorMessage =
                                    "أدخل مبالغ صحيحة أكبر من صفر"
                            }

                            !validIncome && !validExpense -> {
                                errorMessage =
                                    "أدخل الدخل أو المصروف على الأقل"
                            }

                            validExpense &&
                                cost!! > selectedBalance +
                                (if (validIncome) income!! else 0.0) -> {
                                errorMessage =
                                    "الرصيد غير كافٍ لهذا المصروف"
                            }

                            else -> {
                                if (validIncome) {
                                    transactions.add(
                                        Transaction(
                                            description.trim(),
                                            income!!,
                                            currency,
                                            "دخل",
                                            account
                                        )
                                    )
                                }

                                if (validExpense) {
                                    transactions.add(
                                        Transaction(
                                            description.trim(),
                                            cost!!,
                                            currency,
                                            "مصروف",
                                            account
                                        )
                                    )
                                }

                                saveTransactions(
                                    context, transactions.toList()
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
                    "حركات الحساب المحدد",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            items(
                items = transactions.filter {
                    it.account == account && it.currency == currency
                }.reversed(),
                key = {
                    "${it.title}_${it.amount}_${it.currency}_" +
                        "${it.type}_${it.account}_" +
                        transactions.indexOf(it)
                }
            ) { transaction ->
                Card(modifier = Modifier.fillMaxWidth()) {
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
                                "${formatAmount(transaction.amount)} " +
                                if (transaction.currency == "SYP")
                                    "ل.س" else "$"
                        )
                        Text("الحساب: ${transaction.account}")
                    }
                }
            }
        }
    }
}
