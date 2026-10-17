
package com.rasidi.smart

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.getDefault())
        .format(Date(timestamp))

private fun loadTransactions(context: Context): List<Transaction> {
    val json = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    ).getString(TRANSACTIONS_KEY, null) ?: return emptyList()

    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Transaction(
                title = item.getString("title"),
                amount = item.getDouble("amount"),
                currency = item.getString("currency"),
                type = item.getString("type"),
                account = item.optString("account", "نقدي"),
                timestamp = item.optLong(
                    "timestamp", System.currentTimeMillis()
                )
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveTransactions(
    context: Context,
    transactions: List<Transaction>
) {
    val array = JSONArray()
    transactions.forEach { t ->
        array.put(JSONObject().apply {
            put("title", t.title)
            put("amount", t.amount)
            put("currency", t.currency)
            put("type", t.type)
            put("account", t.account)
            put("timestamp", t.timestamp)
        })
    }
    context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    ).edit().putString(TRANSACTIONS_KEY, array.toString()).apply()
}

private fun balanceKey(account: String, currency: String): String {
    val key = if (account == "نقدي") "cash" else "sham"
    return "opening_${key}_${currency}"
}

private fun loadOpeningBalance(
    context: Context, account: String, currency: String
): Double {
    return context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    ).getString(balanceKey(account, currency), "0")
        ?.toDoubleOrNull() ?: 0.0
}

private fun saveOpeningBalance(
    context: Context,
    account: String,
    currency: String,
    amount: Double
) {
    context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    ).edit().putString(
        balanceKey(account, currency), amount.toString()
    ).apply()
}

private fun calculateBalance(
    context: Context,
    transactions: List<Transaction>,
    account: String,
    currency: String,
    excludedIndex: Int = -1
): Double {
    var total = loadOpeningBalance(context, account, currency)
    transactions.forEachIndexed { index, t ->
        if (index != excludedIndex &&
            t.account == account && t.currency == currency
        ) {
            if (t.type == "دخل") total += t.amount
            if (t.type == "مصروف") total -= t.amount
        }
    }
    return total
}

private fun csvEscape(value: String): String {
    return "\"" + value.replace("\"", "\"\"") + "\""
}

private fun createCsv(transactions: List<Transaction>): String {
    val lines = mutableListOf(
        listOf(
            "التاريخ والوقت", "الوصف", "النوع",
            "الحساب", "المبلغ", "العملة"
        ).joinToString(",") { csvEscape(it) }
    )

    transactions.sortedBy { it.timestamp }.forEach { t ->
        lines.add(
            listOf(
                formatDateTime(t.timestamp),
                t.title,
                t.type,
                t.account,
                t.amount.toString(),
                t.currency
            ).joinToString(",") { csvEscape(it) }
        )
    }

    // BOM يساعد Excel على قراءة النص العربي بصورة صحيحة.
    return "\uFEFF" + lines.joinToString("\r\n")
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
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text("$amount $currencyLabel",
                style = MaterialTheme.typography.titleMedium)
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
    var editingIndex by remember { mutableIntStateOf(-1) }
    var refreshVersion by remember { mutableIntStateOf(0) }

    var searchText by remember { mutableStateOf("") }
    var filterAccount by remember { mutableStateOf("الكل") }
    var filterCurrency by remember { mutableStateOf("الكل") }
    var deleteIndex by remember { mutableIntStateOf(-1) }

    // خيارات التقرير
    var reportPeriod by remember { mutableStateOf("اليوم") }
    var reportAccount by remember { mutableStateOf("الكل") }
    var reportCurrency by remember { mutableStateOf("SYP") }
    var exportMessage by remember { mutableStateOf("") }

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

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(
                        createCsv(transactions.toList()).toByteArray(
                            Charsets.UTF_8
                        )
                    )
                } ?: throw IllegalStateException("تعذر فتح الملف")

                exportMessage = "تم حفظ ملف CSV بنجاح."
            } catch (e: Exception) {
                exportMessage = "تعذر حفظ الملف: ${e.localizedMessage}"
            }
        } else {
            exportMessage = "تم إلغاء التصدير."
        }
    }

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
        val t = transactions[index]
        editingIndex = index
        description = t.title
        currency = t.currency
        account = t.account
        salary = if (t.type == "دخل") t.amount.toString() else ""
        expense = if (t.type == "مصروف") t.amount.toString() else ""
        openingText = loadOpeningBalance(
            context, t.account, t.currency
        ).toString()
        errorMessage = ""
    }

    val visibleTransactions = transactions
        .mapIndexed { index, t -> index to t }
        .filter { entry ->
            val t = entry.second
            t.title.contains(searchText.trim(), ignoreCase = true) &&
                (filterAccount == "الكل" || t.account == filterAccount) &&
                (filterCurrency == "الكل" || t.currency == filterCurrency)
        }
        .sortedByDescending { it.second.timestamp }

    // تحديد الحركات الداخلة في التقرير حسب اليوم أو الشهر.
    val todayKey = SimpleDateFormat(
        "yyyy-MM-dd", Locale.getDefault()
    ).format(Date())

    val monthKey = SimpleDateFormat(
        "yyyy-MM", Locale.getDefault()
    ).format(Date())

    val reportTransactions = transactions.filter { t ->
        val dateKey = if (reportPeriod == "اليوم") {
            SimpleDateFormat(
                "yyyy-MM-dd", Locale.getDefault()
            ).format(Date(t.timestamp))
        } else {
            SimpleDateFormat(
                "yyyy-MM", Locale.getDefault()
            ).format(Date(t.timestamp))
        }

        val periodKey = if (reportPeriod == "اليوم") {
            todayKey
        } else {
            monthKey
        }

        dateKey == periodKey &&
            (reportAccount == "الكل" || t.account == reportAccount) &&
            t.currency == reportCurrency
    }

    val totalIncome = reportTransactions
        .filter { it.type == "دخل" }
        .sumOf { it.amount }

    val totalExpense = reportTransactions
        .filter { it.type == "مصروف" }
        .sumOf { it.amount }

    val netTotal = totalIncome - totalExpense

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
                Text("إدارة أموالك الشخصية",
                    style = MaterialTheme.typography.titleLarge)
                Text("تابع أرصدتك ومصاريفك بسهولة")
            }

            item {
                Text("الأرصدة المتاحة",
                    style = MaterialTheme.typography.titleLarge)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BalanceCard(
                        "نقدي - ليرة",
                        formatAmount(calculateBalance(
                            context, transactions, "نقدي", "SYP"
                        )),
                        "ل.س",
                        Modifier.weight(1f)
                    )
                    BalanceCard(
                        "نقدي - دولار",
                        formatAmount(calculateBalance(
                            context, transactions, "نقدي", "USD"
                        )),
                        "$",
                        Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BalanceCard(
                        "شام كاش - ليرة",
                        formatAmount(calculateBalance(
                            context, transactions, "شام كاش", "SYP"
                        )),
                        "ل.س",
                        Modifier.weight(1f)
                    )
                    BalanceCard(
                        "شام كاش - دولار",
                        formatAmount(calculateBalance(
                            context, transactions, "شام كاش", "USD"
                        )),
                        "$",
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                Text("الحساب والعملة",
                    style = MaterialTheme.typography.titleMedium)

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
                        onDismissRequest = { accountMenuExpanded = false }
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

                Text("الرصيد الحالي: ${formatAmount(selectedBalance)} " +
                    if (currency == "SYP") "ل.س" else "$")
            }

            item {
                Text("الرصيد الافتتاحي",
                    style = MaterialTheme.typography.titleMedium)
                Text("الرصيد المحفوظ: ${formatAmount(openingBalance)} " +
                    if (currency == "SYP") "ل.س" else "$")

                OutlinedTextField(
                    value = openingText,
                    onValueChange = {
                        openingText = it
                        errorMessage = ""
                    },
                    label = { Text("الرصيد الافتتاحي") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        val amount = parseAmount(openingText)
                        if (amount == null || !amount.isFinite() || amount < 0) {
                            errorMessage = "أدخل رصيداً صحيحاً غير سالب"
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
                    if (editingIndex >= 0) "تعديل الحركة المالية"
                    else "إضافة حركة مالية",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف الحركة") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = salary,
                    onValueChange = { salary = it },
                    label = { Text("دخل (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = expense,
                    onValueChange = { expense = it },
                    label = { Text("مصروف (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("سيُسجّل التاريخ والوقت تلقائياً.")

                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
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
                            description.isBlank() ->
                                errorMessage = "يرجى إدخال وصف الحركة"

                            (incomeText.isNotEmpty() && !validIncome) ||
                                (expenseText.isNotEmpty() && !validExpense) ->
                                errorMessage = "أدخل مبالغ صحيحة أكبر من صفر"

                            !validIncome && !validExpense ->
                                errorMessage = "أدخل الدخل أو المصروف"

                            editingIndex >= 0 && validIncome && validExpense ->
                                errorMessage = "عند التعديل أدخل دخلاً أو مصروفاً واحداً فقط"

                            editingIndex >= 0 &&
                                editingIndex !in transactions.indices ->
                                errorMessage = "تعذر العثور على الحركة"

                            else -> {
                                val old = if (editingIndex >= 0) {
                                    transactions[editingIndex]
                                } else null

                                val excludedIndex = if (
                                    old != null &&
                                    old.account == account &&
                                    old.currency == currency
                                ) editingIndex else -1

                                val available = calculateBalance(
                                    context, transactions, account,
                                    currency, excludedIndex
                                )
                                val availableForExpense = available +
                                    if (editingIndex < 0 && validIncome) {
                                        income!!
                                    } else 0.0

                                if (validExpense && cost!! > availableForExpense) {
                                    errorMessage = "الرصيد غير كافٍ لهذا المصروف"
                                } else {
                                    if (editingIndex >= 0) {
                                        transactions[editingIndex] = Transaction(
                                            title = description.trim(),
                                            amount = if (validIncome) income!! else cost!!,
                                            currency = currency,
                                            type = if (validIncome) "دخل" else "مصروف",
                                            account = account,
                                            timestamp = old!!.timestamp
                                        )
                                    } else {
                                        val now = System.currentTimeMillis()
                                        if (validIncome) {
                                            transactions.add(Transaction(
                                                description.trim(), income!!,
                                                currency, "دخل", account, now
                                            ))
                                        }
                                        if (validExpense) {
                                            transactions.add(Transaction(
                                                description.trim(), cost!!,
                                                currency, "مصروف", account, now
                                            ))
                                        }
                                    }

                                    saveTransactions(context, transactions.toList())
                                    refreshVersion++
                                    clearForm()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (editingIndex >= 0) "حفظ التعديلات" else "حفظ الحركة")
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

            // قسم التقارير المالية
            item {
                HorizontalDivider()
                Text("التقارير المالية",
                    style = MaterialTheme.typography.titleLarge)

                Text("اختر الفترة")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = reportPeriod == "اليوم",
                        onClick = { reportPeriod = "اليوم" },
                        label = { Text("تقرير اليوم") }
                    )
                    FilterChip(
                        selected = reportPeriod == "الشهر",
                        onClick = { reportPeriod = "الشهر" },
                        label = { Text("تقرير الشهر الحالي") }
                    )
                }

                Text("الحساب")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = reportAccount == "الكل",
                        onClick = { reportAccount = "الكل" },
                        label = { Text("كل الحسابات") }
                    )
                    FilterChip(
                        selected = reportAccount == "نقدي",
                        onClick = { reportAccount = "نقدي" },
                        label = { Text("نقدي") }
                    )
                    FilterChip(
                        selected = reportAccount == "شام كاش",
                        onClick = { reportAccount = "شام كاش" },
                        label = { Text("شام كاش") }
                    )
                }

                Text("عملة التقرير")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = reportCurrency == "SYP",
                        onClick = { reportCurrency = "SYP" },
                        label = { Text("الليرة السورية") }
                    )
                    FilterChip(
                        selected = reportCurrency == "USD",
                        onClick = { reportCurrency = "USD" },
                        label = { Text("الدولار الأمريكي") }
                    )
                }

                val currencyLabel = if (reportCurrency == "SYP") "ل.س" else "$"
                val periodLabel = if (reportPeriod == "اليوم") {
                    "اليوم: $todayKey"
                } else {
                    "الشهر الحالي: $monthKey"
                }

                Text(periodLabel, style = MaterialTheme.typography.titleMedium)
                Text("عدد الحركات: ${reportTransactions.size}")

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("إجمالي المقبوضات: ${formatAmount(totalIncome)} $currencyLabel")
                        Text("إجمالي المدفوعات: ${formatAmount(totalExpense)} $currencyLabel")
                        HorizontalDivider()
                        Text(
                            "صافي الحركة: ${formatAmount(netTotal)} $currencyLabel",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Button(
                    onClick = {
                        exportMessage = ""
                        exportLauncher.launch("Rasidi_Transactions.csv")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تصدير جميع الحركات إلى CSV / Excel")
                }

                Text(
                    "سيتم تصدير جميع الحركات المسجلة، وليس التقرير المعروض فقط.",
                    style = MaterialTheme.typography.bodySmall
                )

                if (exportMessage.isNotBlank()) {
                    Text(exportMessage)
                }
            }

            item {
                HorizontalDivider()
                Text("البحث والتصفية",
                    style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("ابحث باسم الحركة أو وصفها") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("تصفية الحساب")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                Text("تصفية العملة")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterCurrency == "الكل",
                        onClick = { filterCurrency = "الكل" },
                        label = { Text("كل العملات") }
                    )
                    FilterChip(
                        selected = filterCurrency == "SYP",
                        onClick = { filterCurrency = "SYP" },
                        label = { Text("الليرة") }
                    )
                    FilterChip(
                        selected = filterCurrency == "USD",
                        onClick = { filterCurrency = "USD" },
                        label = { Text("الدولار") }
                    )
                }

                Text("النتائج: ${visibleTransactions.size} من ${transactions.size} حركة")
            }

            item {
                Text("الحركات المسجلة",
                    style = MaterialTheme.typography.titleLarge)
            }

            if (visibleTransactions.isEmpty()) {
                item { Text("لا توجد حركات تطابق البحث أو الفلاتر.") }
            }

            items(visibleTransactions, key = { it.first }) { entry ->
                val index = entry.first
                val t = entry.second

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium)
                        Text("${t.type}: ${formatAmount(t.amount)} " +
                            if (t.currency == "SYP") "ل.س" else "$")
                        Text("الحساب: ${t.account}")
                        Text("التاريخ والوقت: ${formatDateTime(t.timestamp)}",
                            style = MaterialTheme.typography.bodySmall)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    contentColor = MaterialTheme.colorScheme.error
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
        val t = transactions[deleteIndex]
        AlertDialog(
            onDismissRequest = { deleteIndex = -1 },
            title = { Text("تأكيد حذف الحركة") },
            text = {
                Text("هل تريد حذف الحركة \"${t.title}\"؟ سيعاد احتساب الرصيد.")
            },
            confirmButton = {
                TextButton(onClick = {
                    transactions.removeAt(deleteIndex)
                    saveTransactions(context, transactions.toList())
                    if (editingIndex == deleteIndex) {
                        clearForm()
                    } else if (editingIndex > deleteIndex) {
                        editingIndex--
                    }
                    deleteIndex = -1
                    refreshVersion++
                }) {
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
