package com.kyled.receiptscanner.export

import com.kyled.receiptscanner.data.Receipt
import com.kyled.receiptscanner.data.total
import com.kyled.receiptscanner.export.XlsxWriter.Align
import com.kyled.receiptscanner.export.XlsxWriter.Borders
import com.kyled.receiptscanner.export.XlsxWriter.NumberFormat
import com.kyled.receiptscanner.export.XlsxWriter.Style
import com.kyled.receiptscanner.export.XlsxWriter.Value
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// Columns of the claim form.
private const val A = 0
private const val B = 1
private const val C = 2
private const val D = 3
private const val E = 4
private const val F = 5
private const val G = 6
private const val H = 7

/** The form always has room for at least this many entries, like the blank template. */
private const val MIN_ENTRY_ROWS = 18
private const val FIRST_ENTRY_ROW = 8

/**
 * Writes one month of receipts as the "Entertainment & Expense Claims" form: same columns, merged
 * cells, shading, Rand format and formulas as the company's .xls template, with section B, totals,
 * signature and authorisation blocks left for filling in by hand.
 */
fun writeClaimForm(claimant: String, month: YearMonth, receipts: List<Receipt>, file: File) {
    val x = XlsxWriter("Claims ${month.shortLabel()}")
    listOf(10.8, 23.8, 22.8, 14.0, 10.8, 14.0, 13.9, 14.0).forEachIndexed { col, width -> x.columnWidth(col, width) }

    val plain = Style()
    val bold = Style(bold = true)
    val right = Style(align = Align.RIGHT)
    val boldRight = Style(bold = true, align = Align.RIGHT)
    val header = Style(bold = true, shaded = true, borders = Borders.BOX, align = Align.CENTER)
    val box = Style(borders = Borders.BOX, align = Align.LEFT)
    val shadedLine = Style(shaded = true, borders = Borders(bottom = true), align = Align.CENTER)
    val money = Style(borders = Borders.BOX, format = NumberFormat.RAND)
    val shadedMoney = Style(shaded = true, borders = Borders.BOX, align = Align.RIGHT, format = NumberFormat.RAND)
    val bar = Style(bold = true, shaded = true, borders = Borders.BOX, align = Align.LEFT)

    // Header
    x.rowHeight(2, 19.2)
    x.rowHeight(3, 19.2)
    x.text(2, A, "Name", right)
    x.merge(2, B, C, Value.Text(claimant), shadedLine)
    x.text(2, F, "Month Ending", Style(align = Align.CENTER))
    x.merge(2, G, H, Value.Date(month.atEndOfMonth()), shadedLine.copy(format = NumberFormat.MONTH))
    x.text(5, A, "N.B. : VOUCHERS MUST BE ATTACHED", plain)
    x.merge(6, A, F, Value.Text("A.   Entertainment"), bold.copy(align = Align.LEFT))

    // Section A: one row per receipt
    x.text(7, A, "Date", header)
    x.merge(7, B, C, Value.Text("Nature of Expense"), header)
    x.merge(7, D, E, Value.Text("Company"), header)
    x.merge(7, F, G, Value.Text("Names"), header)
    x.text(7, H, "Amount", header)

    val sorted = receipts.sortedWith(compareBy<Receipt> { it.expenseDate }.thenBy { it.createdAt })
    val entryRows = maxOf(sorted.size, MIN_ENTRY_ROWS)
    val lastEntryRow = FIRST_ENTRY_ROW + entryRows - 1
    for (i in 0 until entryRows) {
        val row = FIRST_ENTRY_ROW + i
        val r = sorted.getOrNull(i)
        x.set(row, A, r?.let { Value.Date(LocalDate.parse(it.expenseDate)) }, box.copy(align = Align.CENTER, format = NumberFormat.DATE))
        x.merge(row, B, C, r?.let { Value.Text(it.natureOfExpense) }, box)
        x.merge(row, D, E, r?.let { Value.Text(it.company) }, box)
        x.merge(row, F, G, r?.let { Value.Text(it.names) }, box)
        x.set(row, H, r?.let { Value.Number(BigDecimal.valueOf(it.amount)) }, money)
    }

    val subTotalA = sorted.total()
    val sub = lastEntryRow + 1
    x.rowHeight(sub, 15.6)
    x.text(sub, G, "Ent. Sub Total A", right)
    x.set(sub, H, Value.Formula("SUM(H$FIRST_ENTRY_ROW:H$lastEntryRow)", subTotalA), shadedMoney)

    // Section B: motor car, left blank for hand entry
    val zero = BigDecimal.ZERO
    x.merge(sub + 2, A, H, Value.Text("B.   Motor Car Cash Disbursement/ (Private Mileage Costs)"), bar)
    x.rowHeight(sub + 3, 7.95)
    x.merge(sub + 4, B, C, Value.Text("Company"), header)
    x.merge(sub + 4, E, F, Value.Text("Private"), header)
    listOf("Petrol", "Oil", "Other").forEachIndexed { i, label ->
        val row = sub + 5 + i
        x.text(row, A, label, right)
        x.merge(row, B, C, null, money)
        x.merge(row, E, F, null, money)
    }
    val carTotals = sub + 8
    x.merge(carTotals, B, C, Value.Formula("B${sub + 5}+B${sub + 6}+B${sub + 7}", zero), shadedMoney)
    x.merge(carTotals, E, F, Value.Formula("E${sub + 5}+E${sub + 6}+E${sub + 7}", zero), shadedMoney)
    val subTotalB = sub + 9
    x.text(subTotalB, G, "Car Sub Total B  ", right)
    x.set(subTotalB, H, Value.Formula("B$carTotals+E$carTotals", zero), shadedMoney)
    x.merge(sub + 10, A, H, null, bar)

    // Totals
    val totalRow = sub + 11
    val advanceRow = sub + 12
    val dueRow = sub + 13
    x.text(totalRow, G, "Total (A+B)", right)
    x.set(totalRow, H, Value.Formula("H$sub+H$subTotalB", subTotalA), money.copy(bold = true, align = Align.RIGHT))
    x.text(advanceRow, G, "Less Advance", right)
    x.set(advanceRow, H, null, money)
    x.text(dueRow, G, "Amount Due ", right)
    x.set(dueRow, H, Value.Formula("H$totalRow-H$advanceRow", subTotalA), shadedMoney.copy(bold = true))

    // Speedometer and exchange rate
    x.text(dueRow, A, "Speedometer Reading.", bold.copy(align = Align.LEFT))
    x.text(dueRow + 1, A, "Month End:", Style(align = Align.LEFT))
    x.set(dueRow + 1, B, null, Style(bold = true, borders = Borders.BOX, align = Align.RIGHT, format = NumberFormat.DECIMAL))
    x.text(dueRow + 1, C, "Km", plain)
    x.text(dueRow + 1, G, "REO: US \$ 1:00=", right)
    x.text(dueRow + 2, A, "Start Month:", Style(align = Align.LEFT))
    x.set(dueRow + 2, B, null, Style(borders = Borders.BOX, align = Align.RIGHT, format = NumberFormat.DECIMAL))
    x.text(dueRow + 2, C, "Km", plain)
    x.text(dueRow + 2, G, "US \$ 1:00", right)
    x.set(
        dueRow + 3, B,
        Value.Formula("B${dueRow + 1}-B${dueRow + 2}", zero),
        Style(bold = true, shaded = true, borders = Borders.BOX, align = Align.RIGHT, format = NumberFormat.DECIMAL),
    )
    x.text(dueRow + 3, C, "Km", plain)

    // Signature and authorisation
    val signatureRow = dueRow + 6
    x.text(signatureRow, F, "Signature", boldRight)
    x.merge(signatureRow, G, H, null, shadedLine)
    x.rowHeight(signatureRow + 1, 9.0)
    for (col in A..H) x.set(signatureRow + 1, col, null, Style(borders = Borders(bottom = true)))
    val authRow = signatureRow + 2
    x.merge(authRow, A, H, Value.Text("AUTHORISATION"), bar)
    listOf("H.O.D", "Div. Man/Director", "MD", "Checked By: (Cash Book)").forEachIndexed { i, label ->
        x.merge(authRow + 1, i * 2, i * 2 + 1, Value.Text(label), header.copy(bold = false))
        x.merge(authRow + 2, i * 2, i * 2 + 1, null, Style(borders = Borders.BOX))
    }
    x.rowHeight(authRow + 2, 43.95)

    file.parentFile?.mkdirs()
    file.outputStream().use { x.writeTo(it) }
}

/** "Entertainment & Expense Claims Oct 2026.xlsx" */
fun claimFileName(month: YearMonth): String = "Entertainment & Expense Claims ${month.shortLabel()}.xlsx"

/** "Oct 2026" */
private fun YearMonth.shortLabel(): String = format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.UK))
