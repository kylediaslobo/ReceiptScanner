package com.kyled.receiptscanner.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A saved receipt row from public.receipts: one line on the expense claim form. */
@Serializable
data class Receipt(
    val id: String,
    @SerialName("expense_date") val expenseDate: String,
    @SerialName("nature_of_expense") val natureOfExpense: String,
    val company: String,
    val names: String,
    val amount: Double,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** What the extract-receipt Edge Function returns. Same field names as the receipts columns. */
@Serializable
data class ExtractedReceipt(
    @SerialName("expense_date") val expenseDate: String? = null,
    val amount: Double? = null,
    @SerialName("image_path") val imagePath: String? = null,
)

/** The two prompted fields from earlier receipts, used to suggest values on the review screen. */
@Serializable
data class PastChoice(
    @SerialName("nature_of_expense") val natureOfExpense: String,
    val company: String,
)

/** A stored photo, for the "Recent photos" recovery screen. */
@Serializable
data class StoredImage(
    val name: String,
    @SerialName("created_at") val createdAt: String,
)
