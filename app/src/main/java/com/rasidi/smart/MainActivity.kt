
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Transaction(
    val title: String,
    val amount: Double,
    val currency: String,
    val type: String,
    val account: String,
    val timestamp: Long = System.currentTimeMillis()
)

private const val PREFS_NAME = "rasidi_preferences"
private const val TRANSACTIONS_KEY = "saved_transactions"

private val accountNames = listOf("نقدي", "شام كاش")

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat(
        "yyyy/MM/dd - HH:mm",
        Locale.getDefault()
    ).format(Date(timestamp))
}

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
                    account = item.optString("account", "نقدي"),
                    timestamp = item.optLong(
                        "timestamp",
                        System.currentTimeMillis()
                    )
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
        array.put(
            JSONObject().apply {
                put("title", transaction.title)
                put("amount", transaction.amount)
                put("currency", transaction.currency)
                put("type", transaction.type)
                put("account", transaction.account)
                put("timestamp", transaction.timestamp)
            }
        )
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
    var total = loadOpeningBalance(context, account, currency)

    transactions.forEachIndexed { index, transaction ->
        if (
            index != excludedIndex &&
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

@Composable
private fun BalanceCard(
    title: String,
    amount: String,
    currencyLabel: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "$amount $currencyLabel",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
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

    var searchText by remember { mutableStateOf("") }
    var filterAccount by remember { mutableStateOf("الكل") }
    var filterCurrency by remember { mutableStateOf("الكل") }
    var deleteIndex by remember { mutableStateOf(-1) }

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
        openingText = loadOpeningBalance(
            context, account, currency
        ).toString()
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

    val visibleTransactions = transactions
        .mapIndexed { index, transaction -> index to transaction }
        .filter { entry ->
            val transaction = entry.second
            val matchesSearch = transaction.title.contains(
                searchText.trim(),
                ignoreCase = true
            )
            val matchesAccount = filterAccount == "الكل" ||
                transaction.account == filterAccount
            val matchesCurrency = filterCurrency == "الكل" ||
                transaction.currency == filterCurrency

            matchesSearch && matchesAccount && matchesCurrency
        }
        .sortedByDescending { it.second.timestamp }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("رصيدي الذكي") })
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

            item {
                Text(
                    "الأرصدة المتاحة",
                    style = MaterialTheme.typography.titleLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BalanceCard(
                        "نقدي - الليرة السورية",
                        formatAmount(
                            calculateBalance(
                                context, transactions, "نقدي", "SYP"
                            )
                        ),
                        "ل.س",
                        Modifier.weight(1f)
                    )
                    BalanceCard(
                        "نقدي - الدولار الأمريكي",
                        formatAmount(
                            calculateBalance(
                                context, transactions, "نقدي", "USD"
                            )
                        ),
                        "$",
                        Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BalanceCard(
                        "شام كاش - الليرة السورية",
                        formatAmount(
                            calculateBalance(
                                context, transactions, "شام كاش", "SYP"
                            )
                        ),
                        "ل.س",
                        Modifier.weight(1f)
                    )
                    BalanceCard(
                        "شام كاش - الدولار الأمريكي",
                        formatAmount(
                            calculateBalance(
                                context, transactions, "شام كاش", "USD"
                            )
                        ),
                        "$",
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                Text(
                    "الحساب والعملة",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currency == "SYP",
                        onClick = {
                            currency = "SYP"
                            openingText = loadOpeningBalance(
                                context, account, currency
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
                                context, account, currency
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
                    "الرصيد الحالي: ${formatAmount(selectedBalance)} " +
                        if (currency == "SYP") "ل.س" else "$"
                )
            }

            item {
                Text(
                    "الرصيد الافتتاحي",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "الرصيد الافتتاحي المحفوظ: " +
                        formatAmount(openingBalance) +
                        if (currency == "SYP") " ل.س" else " $"
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
                        if (
                            amount == null ||
                            !amount.isFinite() ||
                            amount < 0
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
                    if (editingIndex >= 0) {
                        "تعديل الحركة المالية"
                    } else {
                        "إضافة حركة مالية"
                    },
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

                Text(
                    "سيُسجّل التاريخ والوقت تلقائياً عند الحفظ.",
                    style = MaterialTheme.typography.bodySmall
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
                                val oldTransaction =
                                    if (editingIndex >= 0) {
                                        transactions[editingIndex]
                                    } else {
                                        null
                                    }

                                val excludedIndex =
                                    if (
                                        oldTransaction != null &&
                                        oldTransaction.account == account &&
                                        oldTransaction.currency == currency
                                    ) {
                                        editingIndex
                                    } else {
                                        -1
                                    }

                                val availableBalance = calculateBalance(
                                    context,
                                    transactions,
                                    account,
                                    currency,
                                    excludedIndex
                                )

                                val proposedIncome =
                                    if (validIncome) income!! else 0.0

                                val availableForExpense =
                                    availableBalance +
                                        if (
                                            editingIndex < 0 && validIncome
                                        ) {
                                            proposedIncome
                                        } else {
                                            0.0
                                        }

                                if (
                                    validExpense &&
                                    cost!! > availableForExpense
                                ) {
                                    errorMessage =
                                        "الرصيد غير كافٍ لهذا المصروف"
                                } else {
                                    if (editingIndex >= 0) {
                                        val updated = Transaction(
                                            title = description.trim(),
                                            amount = if (validIncome) {
                                                income!!
                                            } else {
                                                cost!!
                                            },
                                            currency = currency,
                                            type = if (validIncome) {
                                                "دخل"
                                            } else {
                                                "مصروف"
                                            },
                                            account = account,
                                            timestamp = oldTransaction!!.timestamp
                                        )

                                        transactions[editingIndex] = updated
                                    } else {
                                        val now = System.currentTimeMillis()

                                        if (validIncome) {
                                            transactions.add(
                                                Transaction(
                                                    title = description.trim(),
                                                    amount = income!!,
                                                    currency = currency,
                                                    type = "دخل",
                                                    account = account,
                                                    timestamp = now
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
                                                    account = account,
                                                    timestamp = now
                                                )
                                            )
                                        }
                                    }

                                    saveTransactions(
                                        context,
                                        transactions.toList()
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
                        if (editingIndex >= 0) {
                            "حفظ التعديلات"
                        } else {
                            "حفظ الحركة"
                        }
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
                    "البحث والتصفية",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("ابحث باسم الحركة أو وصفها") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("تصفية حسب الحساب")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterAccount == "الكل",
                        onClick = { filterAccount = "الكل" },
                        label = { Text("الكل") }
                    )
                    FilterChip(
                        selected = filterAccount == "نقدي",
                        onClick = { filterAccount = "نقدي" },
                        label = { Text("نقدي") }
                    )
                    FilterChip(
                        selected = filterAccount == "شام كاش",
                        onClick = { filterAccount = "شام كاش" },
                        label = { Text("شام كاش") }
                    )
                }

                Text("تصفية حسب العملة")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterCurrency == "الكل",
                        onClick = { filterCurrency = "الكل" },
                        label = { Text("كل العملات") }
                    )
                    FilterChip(
                        selected = filterCurrency == "SYP",
                        onClick = { filterCurrency = "SYP" },
                        label = { Text("الليرة السورية") }
                    )
                    FilterChip(
                        selected = filterCurrency == "USD",
                        onClick = { filterCurrency = "USD" },
                        label = { Text("الدولار") }
                    )
                }

                Text(
                    "النتائج: ${visibleTransactions.size} من " +
                        "${transactions.size} حركة"
                )
            }

            item {
                Text(
                    "الحركات المسجلة",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (visibleTransactions.isEmpty()) {
                item {
                    Text("لا توجد حركات تطابق البحث أو الفلاتر.")
                }
            }

            items(
                items = visibleTransactions,
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
                                if (transaction.currency == "SYP") {
                                    "ل.س"
                                } else {
                                    "$"
                                }
                        )

                        Text("الحساب: ${transaction.account}")
                        Text(
                            "العملة: " +
                                if (transaction.currency == "SYP") {
                                    "الليرة السورية"
                                } else {
                                    "الدولار الأمريكي"
                                }
                        )

                        Text(
                            "التاريخ والوقت: " +
                                formatDateTime(transaction.timestamp),
                            style = MaterialTheme.typography.bodySmall
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { beginEditing(index) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("تعديل")
                            }

                            OutlinedButton(
                                onClick = { deleteIndex = index },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor =
                                        MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("حذف")
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteIndex in transactions.indices) {
        val transactionToDelete = transactions[deleteIndex]

        AlertDialog(
            onDismissRequest = { deleteIndex = -1 },
            title = { Text("تأكيد حذف الحركة") },
            text = {
                Text(
                    "هل أنت متأكد من حذف حركة " +
                        "\"${transactionToDelete.title}\"؟ " +
                        "سيُعاد احتساب الرصيد بعد الحذف."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        transactions.removeAt(deleteIndex)
                        saveTransactions(context, transactions.toList())

                        if (editingIndex == deleteIndex) {
                            clearForm()
                        } else if (editingIndex > deleteIndex) {
                            editingIndex--
                        }

                        deleteIndex = -1
                        refreshVersion++
                    }
                ) {
                    Text("نعم، احذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteIndex = -1 }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
