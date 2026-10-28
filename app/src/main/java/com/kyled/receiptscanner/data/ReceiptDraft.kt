package com.kyled.receiptscanner.data

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * The editable state of a receipt on the review screen. Every field is a String so the user can
 * type freely; [validate] and [toSaveParams] convert back to typed values.
 */
data class ReceiptDraft(
    val id: String? = null,
    val title: String = "",
    val merchantName: String = "",
    val merchantAddress: String = "",
    val date: String = "",
    val time: String = "",
    val currency: String = "",
    val subtotal: String = "",
    val tax: String = "",
    val tip: String = "",
    val total: String = "",
    val paymentMethod: String = "",
    val lines: List<DraftLine> = emptyList(),
    val extras: List<DraftExtra> = emptyList(),
    val imagePath: String? = null,
)

data class DraftLine(
    val key: Long = nextKey(),
    val description: String = "",
    val quantity: String = "1",
    val unitPrice: String = "",
    val amount: String = "",
)

data class DraftExtra(
    val key: Long = nextKey(),
    val label: String = "",
    val value: String = "",
)

private var keyCounter = 0L
private fun nextKey(): Long = ++keyCounter

fun ExtractedReceipt.toDraft(): ReceiptDraft = ReceiptDraft(
    merchantName = merchantName.orEmpty(),
    merchantAddress = merchantAddress.orEmpty(),
    date = purchaseDate.orEmpty(),
    time = purchaseTime?.take(5).orEmpty(),
    currency = currency.orEmpty(),
    subtotal = subtotal.toMoneyText(),
    tax = tax.toMoneyText(),
    tip = tip.toMoneyText(),
    total = total.toMoneyText(),
    paymentMethod = paymentMethod.orEmpty(),
    lines = items.map { it.toDraftLine() },
    extras = extra.toDraftExtras(),
    imagePath = imagePath,
)

fun Receipt.toDraft(): ReceiptDraft = ReceiptDraft(
    id = id,
    title = title,
    merchantName = merchantName.orEmpty(),
    merchantAddress = merchantAddress.orEmpty(),
    date = purchaseDate.orEmpty(),
    time = purchaseTime?.take(5).orEmpty(),
    currency = currency.orEmpty(),
    subtotal = subtotal.toMoneyText(),
    tax = tax.toMoneyText(),
    tip = tip.toMoneyText(),
    total = total.toMoneyText(),
    paymentMethod = paymentMethod.orEmpty(),
    lines = items.sortedBy { it.lineNo }.map { it.toDraftLine() },
    extras = extra.toDraftExtras(),
    imagePath = imagePath,
)

private fun ReceiptItem.toDraftLine() = DraftLine(
    description = description,
    quantity = quantity?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() } ?: "1",
    unitPrice = unitPrice.toMoneyText(),
    amount = amount.toMoneyText(),
)

private fun JsonObject.toDraftExtras(): List<DraftExtra> = map { (label, value) ->
    DraftExtra(label = label, value = (value as? JsonPrimitive)?.contentOrNull ?: value.toString())
}

fun Double?.toMoneyText(): String = this?.let { "%.2f".format(java.util.Locale.ROOT, it) }.orEmpty()

/** Returns a message describing the first problem, or null if the draft can be saved. */
fun ReceiptDraft.validate(): String? {
    if (title.isBlank()) return "Add a title before saving"
    if (title.length > 200) return "Keep the title under 200 characters"
    if (date.isNotBlank() && date.trim().toLocalDateOrNull() == null) return "Date must look like 2026-10-27"
    if (time.isNotBlank() && time.trim().toLocalTimeOrNull() == null) return "Time must look like 13:45"
    if (currency.isNotBlank() && !currency.trim().uppercase().matches(Regex("[A-Z]{3}"))) {
        return "Currency must be a 3-letter code like EUR"
    }
    val money = listOf("Subtotal" to subtotal, "Tax" to tax, "Tip" to tip, "Total" to total)
    money.firstOrNull { (_, v) -> v.isNotBlank() && v.toDecimalOrNull() == null }?.let { (name, _) ->
        return "$name isn't a number"
    }
    lines.forEachIndexed { i, line ->
        if (line.description.isBlank()) return "Line ${i + 1} needs a description"
        if (listOf(line.quantity, line.unitPrice, line.amount).any { it.isNotBlank() && it.toDecimalOrNull() == null }) {
            return "Line ${i + 1} has a value that isn't a number"
        }
    }
    return null
}

/** Arguments for the save_receipt database function. */
fun ReceiptDraft.toSaveParams(): JsonObject = buildJsonObject {
    put("p_receipt", buildJsonObject {
        if (id != null) put("id", JsonPrimitive(id))
        put("title", JsonPrimitive(title.trim()))
        put("merchant_name", merchantName.textOrNull())
        put("merchant_address", merchantAddress.textOrNull())
        put("purchase_date", date.trim().toLocalDateOrNull()?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
        put("purchase_time", time.trim().toLocalTimeOrNull()?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
        put("currency", currency.trim().uppercase().textOrNull())
        put("subtotal", subtotal.decimalOrNull())
        put("tax", tax.decimalOrNull())
        put("tip", tip.decimalOrNull())
        put("total", total.decimalOrNull())
        put("payment_method", paymentMethod.textOrNull())
        put("extra", buildJsonObject {
            extras.filter { it.label.isNotBlank() }.forEach { put(it.label.trim(), JsonPrimitive(it.value.trim())) }
        })
        put("image_path", imagePath?.let { JsonPrimitive(it) } ?: JsonNull)
    })
    put("p_items", buildJsonArray {
        lines.forEach { line ->
            add(buildJsonObject {
                put("description", JsonPrimitive(line.description.trim()))
                put("quantity", line.quantity.decimalOrNull().takeUnless { it is JsonNull } ?: JsonPrimitive(1))
                put("unit_price", line.unitPrice.decimalOrNull())
                put("amount", line.amount.decimalOrNull())
            })
        }
    })
}

fun String.toDecimalOrNull(): BigDecimal? = trim().replace(',', '.').toBigDecimalOrNull()

private fun String.decimalOrNull(): JsonElement = toDecimalOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
private fun String.textOrNull(): JsonElement = trim().takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull

private fun String.toLocalDateOrNull(): LocalDate? =
    try { LocalDate.parse(this) } catch (e: DateTimeParseException) { null }

private fun String.toLocalTimeOrNull(): LocalTime? =
    try { LocalTime.parse(this) } catch (e: DateTimeParseException) { null }

/** Sum of the line amounts, for the "lines add up to" hint on the review screen. */
fun ReceiptDraft.linesTotal(): BigDecimal? {
    val amounts = lines.mapNotNull { it.amount.toDecimalOrNull() }
    return if (amounts.isEmpty()) null else amounts.fold(BigDecimal.ZERO, BigDecimal::add)
}

