
package com.rasidi.smart

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
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

private val currencies = listOf("SYP", "USD")
private val accounts = listOf("نقدي", "شام كاش")

private fun dateFormat(): SimpleDateFormat =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
    }

private fun todayString(): String = dateFormat().format(Date())

private fun parseDate(value: String): Long? {
    return try {
        val formatter = dateFormat()
        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position)
        if (parsed != null && position.index == value.length) {
            parsed.time
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

private fun startOfDay(timestamp: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

private fun nextDay(timestamp: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    calendar.add(Calendar.DAY_OF_MONTH, 1)
    return calendar.timeInMillis
}

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timestamp))

private fun money(value: Double): String =
    String.format(Locale.US, "%,.2f", value)

private fun loadTransactions(context: Context): MutableList<Transaction> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val result = mutableListOf<Transaction>()

    try {
        val array = JSONArray(prefs.getString(TRANSACTIONS_KEY, "[]") ?: "[]")

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val rawType = item.optString("type", "دخل")
            val type = when (rawType.lowercase(Locale.ROOT)) {
                "expense", "مصروف", "مصروفات" -> "مصروف"
                else -> "دخل"
            }

            result.add(
                Transaction(
                    title = item.optString("title", ""),
                    amount = item.optDouble("amount", 0.0),
                    currency = item.optString("currency", "SYP"),
                    type = type,
                    account = item.optString("account", "نقدي"),
                    timestamp = item.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
    } catch (_: Exception) {
        // Keep the app usable if saved data cannot be read.
    }

    return result
}

private fun saveTransactions(context: Context, transactions: List<Transaction>) {
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

    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(TRANSACTIONS_KEY, array.toString())
        .apply()
}

private fun openingKey(account: String, currency: String): String {
    val accountKey = if (account == "شام كاش") "sham" else "cash"
    return "opening_${accountKey}_${currency}"
}

private fun csvEscape(value: String): String =
    "\"" + value.replace("\"", "\"\"") + "\""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RasidiApp()
                }
            }
        }
    }
}

@Composable
private fun RasidiApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var transactions by remember {
        mutableStateOf(loadTransactions(context).toList())
    }

    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf("SYP") }
    var selectedAccount by remember { mutableStateOf("نقدي") }
    var selectedType by remember { mutableStateOf("دخل") }
    var searchText by remember { mutableStateOf("") }
    var filterAccount by remember { mutableStateOf("الكل") }
    var filterCurrency by remember { mutableStateOf("الكل") }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var deleteTransaction by remember { mutableStateOf<Transaction?>(null) }
    var openingAccount by remember { mutableStateOf("نقدي") }
    var openingCurrency by remember { mutableStateOf("SYP") }
    var openingText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    var reportStart by remember { mutableStateOf(todayString()) }
    var reportEnd by remember { mutableStateOf(todayString()) }
    var reportAccount by remember { mutableStateOf("الكل") }
    var reportCurrency by remember { mutableStateOf("SYP") }
    var exportMessage by remember { mutableStateOf("") }

    val currentReportStart by rememberUpdatedState(reportStart)
    val currentReportEnd by rememberUpdatedState(reportEnd)
    val currentReportAccount by rememberUpdatedState(reportAccount)
    val currentReportCurrency by rememberUpdatedState(reportCurrency)
    val currentTransactions by rememberUpdatedState(transactions)

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val startValue = currentReportStart
                val endValue = currentReportEnd
                val startParsed = parseDate(startValue)
                val endParsed = parseDate(endValue)

                if (startParsed == null || endParsed == null || endParsed < startParsed) {
                    exportMessage = "تحقق من تاريخ البداية والنهاية قبل التصدير."
                    return@rememberLauncherForActivityResult
                }

                val startMillis = startOfDay(startParsed)
                val endExclusive = nextDay(startOfDay(endParsed))

                val exportTransactions = currentTransactions.filter { transaction ->
                    transaction.timestamp >= startMillis &&
                        transaction.timestamp < endExclusive &&
                        (currentReportAccount == "الكل" ||
                            transaction.account == currentReportAccount) &&
                        transaction.currency == currentReportCurrency
                }.sortedBy { it.timestamp }

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                        writer.write("\uFEFF")
                        writer.write(
                            listOf(
                                "التاريخ",
                                "الوقت",
                                "الوصف",
                                "النوع",
                                "الحساب",
                                "المبلغ",
                                "العملة"
                            ).joinToString(",") { csvEscape(it) }
                        )
                        writer.write("\r\n")

                        exportTransactions.forEach { transaction ->
                            val date = SimpleDateFormat(
                                "yyyy-MM-dd",
                                Locale.US
                            ).format(Date(transaction.timestamp))
                            val time = SimpleDateFormat(
                                "HH:mm:ss",
                                Locale.US
                            ).format(Date(transaction.timestamp))

                            val row = listOf(
                                date,
                                time,
                                transaction.title,
                                transaction.type,
                                transaction.account,
                                String.format(Locale.US, "%.2f", transaction.amount),
                                transaction.currency
                            )
                            writer.write(row.joinToString(",") { csvEscape(it) })
                            writer.write("\r\n")
                        }
                    }
                }

                exportMessage = "تم تجهيز ملف التقرير للتصدير."
            } catch (e: Exception) {
                exportMessage = "تعذر تصدير الملف: ${e.localizedMessage ?: "خطأ غير معروف"}"
            }
        }
    }

    val reportStartParsed = parseDate(reportStart)
    val reportEndParsed = parseDate(reportEnd)
    val reportDatesValid =
        reportStartParsed != null &&
            reportEndParsed != null &&
            reportEndParsed >= reportStartParsed

    val reportTransactions = if (reportDatesValid) {
        val startMillis = startOfDay(reportStartParsed!!)
        val endExclusive = nextDay(startOfDay(reportEndParsed!!))

        transactions.filter { transaction ->
            transaction.timestamp >= startMillis &&
                transaction.timestamp < endExclusive &&
                (reportAccount == "الكل" || transaction.account == reportAccount) &&
                transaction.currency == reportCurrency
        }.sortedByDescending { it.timestamp }
    } else {
        emptyList()
    }

    val totalIncome = reportTransactions
        .filter { it.type == "دخل" }
        .sumOf { it.amount }

    val totalExpense = reportTransactions
        .filter { it.type == "مصروف" }
        .sumOf { it.amount }

    val filteredTransactions = transactions
        .filter {
            it.title.contains(searchText, ignoreCase = true) &&
                (filterAccount == "الكل" || it.account == filterAccount) &&
                (filterCurrency == "الكل" || it.currency == filterCurrency)
        }
        .sortedByDescending { it.timestamp }

    fun balanceFor(account: String, currency: String): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val opening = prefs.getString(openingKey(account, currency), "0")
            ?.toDoubleOrNull() ?: 0.0

        val movement = transactions
            .filter { it.account == account && it.currency == currency }
            .sumOf { if (it.type == "دخل") it.amount else -it.amount }

        return opening + movement
    }

    fun persist(updated: List<Transaction>) {
        transactions = updated
        saveTransactions(context, updated)
    }

    fun saveMovement() {
        val cleanTitle = title.trim()
        val amount = amountText.replace(",", "").trim().toDoubleOrNull()

        if (cleanTitle.isEmpty()) {
            message = "يرجى إدخال وصف الحركة."
            return
        }
        if (amount == null || amount <= 0.0) {
            message = "يرجى إدخال مبلغ صحيح أكبر من صفر."
            return
        }

        val old = editingTransaction
        val available = balanceFor(selectedAccount, selectedCurrency) -
            if (old != null &&
                old.account == selectedAccount &&
                old.currency == selectedCurrency &&
                old.type == "مصروف"
            ) old.amount else 0.0

        if (selectedType == "مصروف" && amount > available) {
            message = "الرصيد المتاح غير كافٍ لهذه الحركة."
            return
        }

        val updatedTransaction = Transaction(
            title = cleanTitle,
            amount = amount,
            currency = selectedCurrency,
            type = selectedType,
            account = selectedAccount,
            timestamp = old?.timestamp ?: System.currentTimeMillis()
        )

        val updated = transactions.toMutableList()
        if (old != null) {
            val index = updated.indexOf(old)
            if (index >= 0) updated[index] = updatedTransaction
            else updated.add(updatedTransaction)
        } else {
            updated.add(updatedTransaction)
        }

        persist(updated)
        title = ""
        amountText = ""
        editingTransaction = null
        message = "تم حفظ الحركة بنجاح."
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "رصيدي الذكي",
                style = MaterialTheme.typography.headlineMedium
            )
            Text("إدارة الأرصدة والحركات والتقارير المالية")
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("الأرصدة الحالية", style = MaterialTheme.typography.titleLarge)
                    accounts.forEach { account ->
                        Text(account, style = MaterialTheme.typography.titleMedium)
                        currencies.forEach { currency ->
                            Text("$currency: ${money(balanceFor(account, currency))}")
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Text("تسجيل الرصيد الافتتاحي", style = MaterialTheme.typography.titleLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { account ->
                    OutlinedButton(onClick = { openingAccount = account }) {
                        Text(if (openingAccount == account) "✓ $account" else account)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                currencies.forEach { currency ->
                    OutlinedButton(onClick = { openingCurrency = currency }) {
                        Text(if (openingCurrency == currency) "✓ $currency" else currency)
                    }
                }
            }

            OutlinedTextField(
                value = openingText,
                onValueChange = { openingText = it },
                label = { Text("قيمة الرصيد الافتتاحي") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val amount = openingText.replace(",", "").toDoubleOrNull()
                    if (amount == null || amount < 0.0) {
                        message = "أدخل رصيداً افتتاحياً صحيحاً."
                    } else {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString(
                                openingKey(openingAccount, openingCurrency),
                                amount.toString()
                            )
                            .apply()
                        openingText = ""
                        message = "تم حفظ الرصيد الافتتاحي."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ الرصيد الافتتاحي")
            }
        }

        item {
            HorizontalDivider()
            Text(
                if (editingTransaction == null) "إضافة حركة مالية" else "تعديل الحركة المالية",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("وصف الحركة") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("المبلغ") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("نوع الحركة")
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedType == "دخل",
                    onClick = { selectedType = "دخل" }
                )
                Text("مقبوضات")
                RadioButton(
                    selected = selectedType == "مصروف",
                    onClick = { selectedType = "مصروف" }
                )
                Text("مدفوعات")
            }

            Text("الحساب")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { account ->
                    OutlinedButton(onClick = { selectedAccount = account }) {
                        Text(if (selectedAccount == account) "✓ $account" else account)
                    }
                }
            }

            Text("العملة")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                currencies.forEach { currency ->
                    OutlinedButton(onClick = { selectedCurrency = currency }) {
                        Text(if (selectedCurrency == currency) "✓ $currency" else currency)
                    }
                }
            }

            Button(onClick = { saveMovement() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (editingTransaction == null) "حفظ الحركة" else "حفظ التعديلات")
            }

            if (editingTransaction != null) {
                OutlinedButton(
                    onClick = {
                        editingTransaction = null
                        title = ""
                        amountText = ""
                        message = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إلغاء التعديل")
                }
            }

            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            HorizontalDivider()
            Text("البحث وتصفية الحركات", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("ابحث في وصف الحركة") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("تصفية الحساب")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("الكل") + accounts).forEach { account ->
                    OutlinedButton(onClick = { filterAccount = account }) {
                        Text(if (filterAccount == account) "✓ $account" else account)
                    }
                }
            }

            Text("تصفية العملة")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("الكل") + currencies).forEach { currency ->
                    OutlinedButton(onClick = { filterCurrency = currency }) {
                        Text(if (filterCurrency == currency) "✓ $currency" else currency)
                    }
                }
            }

            Text("عدد الحركات المعروضة: ${filteredTransactions.size}")
        }

        items(filteredTransactions) { transaction ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(transaction.title, style = MaterialTheme.typography.titleMedium)
                    Text("${transaction.type}: ${money(transaction.amount)} ${transaction.currency}")
                    Text("الحساب: ${transaction.account}")
                    Text("التاريخ والوقت: ${formatDateTime(transaction.timestamp)}")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                editingTransaction = transaction
                                title = transaction.title
                                amountText = transaction.amount.toString()
                                selectedCurrency = transaction.currency
                                selectedAccount = transaction.account
                                selectedType = transaction.type
                                message = ""
                            }
                        ) {
                            Text("تعديل")
                        }

                        OutlinedButton(onClick = { deleteTransaction = transaction }) {
                            Text("حذف")
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Text("تقرير مالي حسب الفترة", style = MaterialTheme.typography.titleLarge)
            Text("أدخل التاريخين بالصيغة yyyy-MM-dd. تاريخ النهاية يشمل اليوم كاملاً.")

            OutlinedTextField(
                value = reportStart,
                onValueChange = { reportStart = it },
                label = { Text("تاريخ البداية yyyy-MM-dd") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = reportEnd,
                onValueChange = { reportEnd = it },
                label = { Text("تاريخ النهاية yyyy-MM-dd") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val today = todayString()
                    reportStart = today
                    reportEnd = today
                }) {
                    Text("اليوم")
                }

                OutlinedButton(onClick = {
                    val calendar = Calendar.getInstance()
                    reportEnd = dateFormat().format(calendar.time)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    reportStart = dateFormat().format(calendar.time)
                }) {
                    Text("هذا الشهر")
                }
            }

            Text("الحساب المشمول بالتقرير")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("الكل") + accounts).forEach { account ->
                    OutlinedButton(onClick = { reportAccount = account }) {
                        Text(if (reportAccount == account) "✓ $account" else account)
                    }
                }
            }

            Text("عملة التقرير")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                currencies.forEach { currency ->
                    OutlinedButton(onClick = { reportCurrency = currency }) {
                        Text(if (reportCurrency == currency) "✓ $currency" else currency)
                    }
                }
            }

            if (!reportDatesValid) {
                Text(
                    "التاريخ غير صحيح. تأكد من استخدام yyyy-MM-dd وأن تاريخ النهاية ليس قبل البداية.",
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text("ملخص التقرير", style = MaterialTheme.typography.titleMedium)
                        Text("الفترة: $reportStart إلى $reportEnd")
                        Text("الحساب: $reportAccount")
                        Text("العملة: $reportCurrency")
                        Text("عدد الحركات: ${reportTransactions.size}")
                        Text("إجمالي المقبوضات: ${money(totalIncome)} $reportCurrency")
                        Text("إجمالي المدفوعات: ${money(totalExpense)} $reportCurrency")
                        Text(
                            "صافي الحركة: ${money(totalIncome - totalExpense)} $reportCurrency"
                        )
                    }
                }

                Button(
                    onClick = {
                        val safeStart = reportStart.replace("-", "")
                        val safeEnd = reportEnd.replace("-", "")
                        exportLauncher.launch(
                            "Rasidi_${safeStart}_to_${safeEnd}.csv"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تصدير حركات التقرير إلى CSV")
                }

                Text("الحركات المطابقة للتقرير: ${reportTransactions.size}")

                reportTransactions.forEach { transaction ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(transaction.title, style = MaterialTheme.typography.titleMedium)
                            Text("${transaction.type}: ${money(transaction.amount)} ${transaction.currency}")
                            Text("${transaction.account} • ${formatDateTime(transaction.timestamp)}")
                        }
                    }
                }
            }

            if (exportMessage.isNotBlank()) {
                Text(exportMessage, color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "تذكير: احتفظ بنسخة من ملف CSV بعد تصديره، ولا تشارك بياناتك المالية أو ملفاتك الخاصة مع الآخرين."
            )
        }
    }

    if (deleteTransaction != null) {
        AlertDialog(
            onDismissRequest = { deleteTransaction = null },
            title = { Text("تأكيد الحذف") },
            text = {
                Text("هل تريد حذف الحركة «${deleteTransaction?.title}»؟ لا يمكن التراجع عن هذا الإجراء.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleteTransaction
                    if (target != null) {
                        val updated = transactions.toMutableList()
                        val index = updated.indexOf(target)
                        if (index >= 0) {
                            updated.removeAt(index)
                            persist(updated)
                            message = "تم حذف الحركة."
                        }
                    }
                    deleteTransaction = null
                }) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTransaction = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
