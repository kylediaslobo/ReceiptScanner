package com.kyled.receiptscanner.data

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The editable state of a receipt on the review screen. Text fields stay Strings so the user can
 * type freely; [validate] and [toSaveParams] convert back to typed values.
 */
data class ReceiptDraft(
    val id: String? = null,
    /** ISO date, yyyy-MM-dd. Blank until read from the receipt or picked. */
    val date: String = "",
    val natureOfExpense: String = "",
    val company: String = "",
    val names: String = "",
    val amount: String = "",
    val imagePath: String? = null,
)

/** [names] is the claimant's default for the Names column; receipts don't print it. */
fun ExtractedReceipt.toDraft(names: String): ReceiptDraft = ReceiptDraft(
    date = expenseDate?.toLocalDateOrNull()?.toString().orEmpty(),
    names = names,
    amount = amount.toMoneyText(),
    imagePath = imagePath,
)

fun Receipt.toDraft(): ReceiptDraft = ReceiptDraft(
    id = id,
    date = expenseDate,
    natureOfExpense = natureOfExpense,
    company = company,
    names = names,
    amount = amount.toMoneyText(),
    imagePath = imagePath,
)

fun Double?.toMoneyText(): String = this?.let { "%.2f".format(Locale.ROOT, it) }.orEmpty()

/** Returns a message describing the first problem, or null if the draft can be saved. */
fun ReceiptDraft.validate(): String? {
    if (date.isBlank()) return "Pick the date"
    if (date.toLocalDateOrNull() == null) return "That date isn't valid"
    if (natureOfExpense.isBlank()) return "Add the nature of expense"
    if (company.isBlank()) return "Add the company"
    if (names.isBlank()) return "Add the names"
    if (amount.isBlank()) return "Add the amount"
    if (amount.toDecimalOrNull() == null) return "The amount isn't a number"
    listOf("Nature of expense" to natureOfExpense, "Company" to company, "Names" to names)
        .firstOrNull { (_, v) -> v.trim().length > 200 }
        ?.let { (name, _) -> return "$name is too long" }
    return null
}

/** Arguments for the save_receipt database function. */
fun ReceiptDraft.toSaveParams(): JsonObject = buildJsonObject {
    put("p_receipt", buildJsonObject {
        if (id != null) put("id", JsonPrimitive(id))
        put("expense_date", JsonPrimitive(date.trim()))
        put("nature_of_expense", JsonPrimitive(natureOfExpense.trim()))
        put("company", JsonPrimitive(company.trim()))
        put("names", JsonPrimitive(names.trim()))
        put("amount", JsonPrimitive(amount.toDecimalOrNull()))
        put("image_path", imagePath?.let { JsonPrimitive(it) } ?: JsonNull)
    })
}

/** Accepts "1100", "R 1 100.50", "1,100.50", "1,100" and "1100,50". */
fun String.toDecimalOrNull(): BigDecimal? {
    val compact = trim().removePrefix("R").replace(" ", "").replace(" ", "")
    val normalised = when {
        compact.contains('.') -> compact.replace(",", "")
        // A comma followed by 1 or 2 digits at the end is a decimal comma; otherwise it groups thousands.
        Regex(",\\d{1,2}$").containsMatchIn(compact) && compact.count { it == ',' } == 1 -> compact.replace(',', '.')
        else -> compact.replace(",", "")
    }
    return normalised.toBigDecimalOrNull()
}

fun String.toLocalDateOrNull(): LocalDate? =
    try { LocalDate.parse(trim()) } catch (e: DateTimeParseException) { null }

private val RAND = java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(Locale.US))

/** "R 1,100.00", like the claim form's Amount column. */
fun formatRand(amount: BigDecimal): String = "R " + RAND.format(amount)

fun formatRand(amount: Double): String = formatRand(BigDecimal.valueOf(amount))

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)

/** "27 Oct 2026", or the raw text if it isn't a date. */
fun formatDay(isoDate: String): String = isoDate.toLocalDateOrNull()?.format(DAY) ?: isoDate

/** "October 2026". */
fun YearMonth.label(): String = format(MONTH)

val Receipt.month: YearMonth get() = YearMonth.from(LocalDate.parse(expenseDate))

fun List<Receipt>.total(): BigDecimal = fold(BigDecimal.ZERO) { sum, r -> sum + BigDecimal.valueOf(r.amount) }
