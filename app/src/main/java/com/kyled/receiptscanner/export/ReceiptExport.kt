package com.kyled.receiptscanner.export

import com.kyled.receiptscanner.data.Receipt
import com.kyled.receiptscanner.export.XlsxWriter.Cell
import com.kyled.receiptscanner.export.XlsxWriter.Column
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * PLACEHOLDER LAYOUT. Replace the columns and rows below with the exact spreadsheet layout once it's
 * been provided. Everything else (file writing, sharing) stays the same.
 *
 * Sheet 1 "Receipts": one row per receipt.
 * Sheet 2 "Line items": one row per line, with the receipt it belongs to.
 */
fun writeReceiptsWorkbook(receipts: List<Receipt>, file: File) {
    val xlsx = XlsxWriter()

    xlsx.addSheet(
        name = "Receipts",
        columns = listOf(
            Column("Date", 12.0),
            Column("Title", 28.0),
            Column("Merchant", 24.0),
            Column("Address", 32.0),
            Column("Currency", 9.0),
            Column("Subtotal", 11.0),
            Column("Tax", 10.0),
            Column("Tip", 10.0),
            Column("Total", 11.0),
            Column("Paid by", 14.0),
            Column("Other details", 36.0),
            Column("Saved", 12.0),
        ),
        rows = receipts.map { r ->
            listOf(
                date(r.purchaseDate),
                text(r.title),
                text(r.merchantName),
                text(r.merchantAddress),
                text(r.currency),
                number(r.subtotal),
                number(r.tax),
                number(r.tip),
                number(r.total),
                text(r.paymentMethod),
                text(r.extra.entries.joinToString("; ") { (k, v) -> "$k: ${(v as? JsonPrimitive)?.contentOrNull ?: v}" }),
                date(r.createdAt.take(10)),
            )
        },
    )

    xlsx.addSheet(
        name = "Line items",
        columns = listOf(
            Column("Date", 12.0),
            Column("Receipt", 28.0),
            Column("Merchant", 24.0),
            Column("Item", 36.0),
            Column("Qty", 7.0),
            Column("Unit price", 11.0),
            Column("Amount", 11.0),
            Column("Currency", 9.0),
        ),
        rows = receipts.flatMap { r ->
            r.items.sortedBy { it.lineNo }.map { item ->
                listOf(
                    date(r.purchaseDate),
                    text(r.title),
                    text(r.merchantName),
                    text(item.description),
                    number(item.quantity),
                    number(item.unitPrice),
                    number(item.amount),
                    text(r.currency),
                )
            }
        },
    )

    file.parentFile?.mkdirs()
    file.outputStream().use { xlsx.writeTo(it) }
}

private fun text(value: String?): Cell = if (value.isNullOrBlank()) Cell.Empty else Cell.Text(value)

private fun number(value: Double?): Cell = value?.let { Cell.Number(it) } ?: Cell.Empty

private fun date(value: String?): Cell {
    if (value.isNullOrBlank()) return Cell.Empty
    return try {
        Cell.Date(LocalDate.parse(value))
    } catch (e: DateTimeParseException) {
        Cell.Text(value)
    }
}
