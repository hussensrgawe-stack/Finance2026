
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

private val accountNames = listOf("نقدي", "شام كاش")

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

private fun balanceKey(
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
        balanceKey(account, currency), "0"
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
        .putString(balanceKey(account, currency), amount.toString())
        .apply()
}

private fun calculateBalance(
    context: Context,
    transactions: List<Transaction>,
    account: String,
    currency: String,
    excludedIndex: Int = -1
): Double {
    val opening = loadOpeningBalance(context, account, currency)

    var total = opening

    transactions.forEachIndexed { index, transaction ->
        if (index != excludedIndex &&
            transaction.account == account &&
            transaction.currency == currency
        ) {
            when (transaction.type) {
                "دخل" -> total += transaction.amount
                "مصروف" -> total -= transaction.amount
            }
        }
    }

    return total
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

    var description by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var expense by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SYP") }
    var account by remember { mutableStateOf("نقدي") }
    var openingText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    var accountMenuExpanded by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf(-1) }
    var refreshVersion by remember { mutableStateOf(0) }

    val transactions = remember(context) {
        mutableStateListOf<Transaction>().apply {
            addAll(loadTransactions(context))
        }
    }

    val openingBalance = remember(
        context, account, currency, refreshVersion
    ) {
        loadOpeningBalance(context, account, currency)
    }

    val selectedBalance = calculateBalance(
        context, transactions, account, currency
    )

    fun parseAmount(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()

    fun formatAmount(amount: Double): String =
        numberFormat.format(amount)

    fun clearForm() {
        description = ""
        salary = ""
        expense = ""
        editingIndex = -1
        errorMessage = ""
    }

    fun beginEditing(index: Int) {
        if (index !in transactions.indices) return

        val transaction = transactions[index]
        editingIndex = index
        description = transaction.title
        currency = transaction.currency
        account = transaction.account
        salary = if (transaction.type == "دخل") {
            transaction.amount.toString()
        } else {
            ""
        }
        expense = if (transaction.type == "مصروف") {
            transaction.amount.toString()
        } else {
            ""
        }
        openingText = loadOpeningBalance(
            context, transaction.account, transaction.currency
        ).toString()
        errorMessage = ""
    }

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
                Text("تابع أرصدتك ومصاريفك بسهولة")
            }

            // الأرصدة: صفان، في كل صف بطاقتان
            item {
                Text(
                    "الأرصدة المتاحة",
                    style = MaterialTheme.typography.titleLarge
                )

                @Composable
                fun BalanceCard(
                    title: String,
                    acc: String,
                    curr: String
                ) {
                    Card(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                formatAmount(
                                    calculateBalance(
                                        context,
                                        transactions,
                                        acc,
                                        curr
                                    )
                                ) + if (curr == "SYP") " ل.س" else " $",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BalanceCard("نقدي - الليرة السورية", "نقدي", "SYP")
                    BalanceCard("نقدي - الدولار الأمريكي", "نقدي", "USD")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BalanceCard(
                        "شام كاش - الليرة السورية", "شام كاش", "SYP"
                    )
                    BalanceCard(
                        "شام كاش - الدولار الأمريكي", "شام كاش", "USD"
                    )
                }
            }

            item {
                Text(
                    "الحساب والعملة",
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
                        label = { Text("الدولار الأمريكي") }
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
                        accountNames.forEach { acc ->
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
                    "الرصيد الحالي: ${formatAmount(selectedBalance)}" +
                        if (currency == "SYP") " ل.س" else " $"
                )
            }

            item {
                Text(
                    "الرصيد الافتتاحي",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = openingText,
                    onValueChange = {
                        openingText = it
                        errorMessage = ""
                    },
                    label = {
                        Text("الرصيد الافتتاحي لهذا الحساب")
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
                                "أدخل رصيداً صحيحاً غير سالب"
                        } else {
                            saveOpeningBalance(
                                context, account, currency, amount
                            )
                            refreshVersion++
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
                    if (editingIndex >= 0)
                        "تعديل الحركة المالية"
                    else "إضافة حركة مالية",
                    style = MaterialTheme.typography.titleLarge
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

                        val invalidInput =
                            (incomeText.isNotEmpty() && !validIncome) ||
                            (expenseText.isNotEmpty() && !validExpense)

                        when {
                            description.isBlank() -> {
                                errorMessage = "يرجى إدخال وصف الحركة"
                            }

                            invalidInput -> {
                                errorMessage =
                                    "أدخل مبالغ صحيحة أكبر من صفر"
                            }

                            !validIncome && !validExpense -> {
                                errorMessage =
                                    "أدخل الدخل أو المصروف على الأقل"
                            }

                            editingIndex >= 0 &&
                                validIncome && validExpense -> {
                                errorMessage =
                                    "عند التعديل أدخل دخلاً أو مصروفاً واحداً فقط"
                            }

                            editingIndex >= 0 &&
                                editingIndex !in transactions.indices -> {
                                errorMessage =
                                    "تعذر العثور على الحركة المراد تعديلها"
                            }

                            else -> {
                                val availableForEdit = if (
                                    editingIndex >= 0
                                ) {
                                    calculateBalance(
                                        context,
                                        transactions,
                                        account,
                                        currency,
                                        editingIndex
                                    )
                                } else {
                                    selectedBalance
                                }

                                val proposedIncome =
                                    if (validIncome) income!! else 0.0

                                if (
                                    validExpense &&
                                    cost!! > availableForEdit +
                                        (if (editingIndex < 0)
                                            proposedIncome else 0.0)
                                ) {
                                    errorMessage =
                                        "الرصيد غير كافٍ لهذا المصروف"
                                } else {
                                    if (editingIndex >= 0) {
                                        val updated = Transaction(
                                            title = description.trim(),
                                            amount = if (validIncome)
                                                income!! else cost!!,
                                            currency = currency,
                                            type = if (validIncome)
                                                "دخل" else "مصروف",
                                            account = account
                                        )

                                        transactions[editingIndex] = updated
                                    } else {
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
                                    }

                                    saveTransactions(
                                        context, transactions.toList()
                                    )
                                    refreshVersion++
                                    clearForm()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (editingIndex >= 0)
                            "حفظ التعديلات"
                        else "حفظ الحركة"
                    )
                }

                if (editingIndex >= 0) {
                    OutlinedButton(
                        onClick = { clearForm() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إلغاء التعديل")
                    }
                }
            }

            item {
                Text(
                    "الحركات المسجلة (${transactions.size})",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            items(
                items = transactions.mapIndexed { index, transaction ->
                    index to transaction
                }.reversed(),
                key = { it.first }
            ) { entry ->
                val index = entry.first
                val transaction = entry.second

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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

                        OutlinedButton(
                            onClick = { beginEditing(index) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تعديل الحركة")
                        }
                    }
                }
            }
        }
    }
}
