
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
    } catch (_: Exception) {
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

private fun calculateTotal(
    transactions: List<Transaction>,
    account: String,
    currency: String,
    type: String
): Double {
    return transactions
        .filter {
            it.account == account &&
            it.currency == currency &&
            it.type == type
        }
        .sumOf { it.amount }
}

@Composable
private fun BalanceCard(
    title: String,
    amount: String,
    currencyLabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                amount,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                currencyLabel,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amount: String,
    currencyLabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                amount,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                currencyLabel,
                style = MaterialTheme.typography.bodySmall
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
    var incomeText by remember { mutableStateOf("") }
    var expenseText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SYP") }
    var account by remember { mutableStateOf("نقدي") }
    var openingText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    var accountMenuExpanded by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var refreshVersion by remember { mutableIntStateOf(0) }

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

    fun parseAmount(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()

    fun formatAmount(amount: Double): String =
        numberFormat.format(amount)

    fun currencyLabel(code: String): String =
        if (code == "SYP") "ل.س" else "$"

    fun clearForm() {
        description = ""
        incomeText = ""
        expenseText = ""
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

        incomeText = if (transaction.type == "دخل") {
            transaction.amount.toString()
        } else {
            ""
        }

        expenseText = if (transaction.type == "مصروف") {
            transaction.amount.toString()
        } else {
            ""
        }

        openingText = loadOpeningBalance(
            context,
            transaction.account,
            transaction.currency
        ).toString()

        errorMessage = ""
    }

    val selectedBalance = calculateBalance(
        context, transactions, account, currency
    )

    val selectedIncome = calculateTotal(
        transactions, account, currency, "دخل"
    )

    val selectedExpenses = calculateTotal(
        transactions, account, currency, "مصروف"
    )

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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // مقدمة لوحة التحكم
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "إدارة أموالك الشخصية",
                            style = MaterialTheme.typography.headlineSmall,
                            color =
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "كل أرصدتك وحركاتك المالية في مكان واحد",
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // بطاقات الأرصدة الأربعة
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
                        title = "نقدي - ليرة سورية",
                        amount = formatAmount(
                            calculateBalance(
                                context, transactions, "نقدي", "SYP"
                            )
                        ),
                        currencyLabel = "ل.س",
                        modifier = Modifier.weight(1f)
                    )

                    BalanceCard(
                        title = "نقدي - دولار",
                        amount = formatAmount(
                            calculateBalance(
                                context, transactions, "نقدي", "USD"
                            )
                        ),
                        currencyLabel = "$",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BalanceCard(
                        title = "شام كاش - ليرة",
                        amount = formatAmount(
                            calculateBalance(
                                context, transactions, "شام كاش", "SYP"
                            )
                        ),
                        currencyLabel = "ل.س",
                        modifier = Modifier.weight(1f)
                    )

                    BalanceCard(
                        title = "شام كاش - دولار",
                        amount = formatAmount(
                            calculateBalance(
                                context, transactions, "شام كاش", "USD"
                            )
                        ),
                        currencyLabel = "$",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // اختيار الحساب والعملة
            item {
                Text(
                    "الحساب والعملة",
                    style = MaterialTheme.typography.titleLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                        onClick = {
                            accountMenuExpanded = true
                        }
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

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "الرصيد الحالي",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${formatAmount(selectedBalance)} " +
                                currencyLabel(currency),
                            style = MaterialTheme.typography.headlineMedium,
                            color =
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // ملخص المقبوضات والمدفوعات
            item {
                Text(
                    "ملخص الحركة المالية",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    "$account • ${if (currency == "SYP") "الليرة السورية" else "الدولار الأمريكي"}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        title = "إجمالي المقبوضات",
                        amount = formatAmount(selectedIncome),
                        currencyLabel = currencyLabel(currency),
                        modifier = Modifier.weight(1f)
                    )

                    SummaryCard(
                        title = "إجمالي المدفوعات",
                        amount = formatAmount(selectedExpenses),
                        currencyLabel = currencyLabel(currency),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // الرصيد الافتتاحي
            item {
                Text(
                    "الرصيد الافتتاحي",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    "الرصيد المحفوظ: ${formatAmount(openingBalance)} " +
                        currencyLabel(currency)
                )

                OutlinedTextField(
                    value = openingText,
                    onValueChange = {
                        openingText = it
                        errorMessage = ""
                    },
                    label = {
                        Text("الرصيد الافتتاحي")
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
                                "أدخل رصيداً افتتاحياً صحيحاً غير سالب"
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

            // نموذج إضافة الحركة أو تعديلها
            item {
                Text(
                    if (editingIndex >= 0)
                        "تعديل حركة مالية"
                    else
                        "إضافة حركة مالية",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        errorMessage = ""
                    },
                    label = { Text("وصف الحركة") },
                    placeholder = { Text("مثال: راتب أو مشتريات") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = incomeText,
                    onValueChange = {
                        incomeText = it
                        errorMessage = ""
                    },
                    label = { Text("المقبوضات / الدخل") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = expenseText,
                    onValueChange = {
                        expenseText = it
                        errorMessage = ""
                    },
                    label = { Text("المدفوعات / المصروف") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    "اختر دخلاً أو مصروفاً واحداً عند تعديل حركة موجودة.",
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
                        val cleanIncome = incomeText.trim()
                        val cleanExpense = expenseText.trim()

                        val income = parseAmount(cleanIncome)
                        val cost = parseAmount(cleanExpense)

                        val validIncome =
                            income != null &&
                            income.isFinite() &&
                            income > 0

                        val validExpense =
                            cost != null &&
                            cost.isFinite() &&
                            cost > 0

                        val invalidInput =
                            (cleanIncome.isNotEmpty() && !validIncome) ||
                            (cleanExpense.isNotEmpty() && !validExpense)

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
                                    "أدخل مبلغ الدخل أو المصروف"
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
                                /*
                                 * نحسب الرصيد بعد استبعاد الحركة القديمة.
                                 * هذا مهم عند تغيير الحساب أو العملة أثناء التعديل.
                                 */
                                val excludedIndex =
                                    if (editingIndex >= 0) editingIndex else -1

                                val balanceWithoutOldTransaction =
                                    calculateBalance(
                                        context = context,
                                        transactions = transactions,
                                        account = account,
                                        currency = currency,
                                        excludedIndex = excludedIndex
                                    )

                                /*
                                 * عند إضافة دخل ومصروف معاً كحركتين جديدتين،
                                 * يُسمح باستخدام الدخل ضمن الرصيد المتاح للمصروف.
                                 * عند تعديل حركة، لا يوجد إلا نوع واحد.
                                 */
                                val incomeToInclude =
                                    if (editingIndex < 0 && validIncome) {
                                        income!!
                                    } else {
                                        0.0
                                    }

                                val availableForExpense =
                                    balanceWithoutOldTransaction +
                                        incomeToInclude

                                if (
                                    validExpense &&
                                    cost!! > availableForExpense
                                ) {
                                    errorMessage =
                                        "الرصيد غير كافٍ لهذا المصروف"
                                } else {
                                    if (editingIndex >= 0) {
                                        transactions[editingIndex] =
                                            Transaction(
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
                                                account = account
                                            )
                                    } else {
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
                        if (editingIndex >= 0)
                            "حفظ التعديلات"
                        else
                            "حفظ الحركة"
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

            // أحدث الحركات أولاً
            item {
                Text(
                    "أحدث الحركات المالية",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "عدد الحركات المسجلة: ${transactions.size}",
                    style = MaterialTheme.typography.bodySmall
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

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 1.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            transaction.title,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            "${transaction.type}: " +
                                "${formatAmount(transaction.amount)} " +
                                currencyLabel(transaction.currency),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (transaction.type == "دخل") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )

                        Text(
                            "الحساب: ${transaction.account}",
                            style = MaterialTheme.typography.bodyMedium
                        )

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
