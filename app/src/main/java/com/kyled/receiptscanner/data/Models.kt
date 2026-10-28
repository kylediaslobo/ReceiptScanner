package com.kyled.receiptscanner.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A saved receipt row from public.receipts, with its lines embedded. */
@Serializable
data class Receipt(
    val id: String,
    val title: String,
    @SerialName("merchant_name") val merchantName: String? = null,
    @SerialName("merchant_address") val merchantAddress: String? = null,
    @SerialName("purchase_date") val purchaseDate: String? = null,
    @SerialName("purchase_time") val purchaseTime: String? = null,
    val currency: String? = null,
    val subtotal: Double? = null,
    val tax: Double? = null,
    val tip: Double? = null,
    val total: Double? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    val extra: JsonObject = JsonObject(emptyMap()),
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("receipt_items") val items: List<ReceiptItem> = emptyList(),
)

@Serializable
data class ReceiptItem(
    @SerialName("line_no") val lineNo: Int = 0,
    val description: String,
    val quantity: Double? = 1.0,
    @SerialName("unit_price") val unitPrice: Double? = null,
    val amount: Double? = null,
)

/** What the extract-receipt Edge Function returns. Same field names as the receipts columns. */
@Serializable
data class ExtractedReceipt(
    @SerialName("merchant_name") val merchantName: String? = null,
    @SerialName("merchant_address") val merchantAddress: String? = null,
    @SerialName("purchase_date") val purchaseDate: String? = null,
    @SerialName("purchase_time") val purchaseTime: String? = null,
    val currency: String? = null,
    val subtotal: Double? = null,
    val tax: Double? = null,
    val tip: Double? = null,
    val total: Double? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    val items: List<ReceiptItem> = emptyList(),
    val extra: JsonObject = JsonObject(emptyMap()),
    @SerialName("image_path") val imagePath: String? = null,
)

/** A stored photo, for the "Recent photos" recovery screen. */
@Serializable
data class StoredImage(
    val name: String,
    @SerialName("created_at") val createdAt: String,
)
