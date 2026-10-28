package com.kyled.receiptscanner.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import java.io.File
import java.time.YearMonth
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ReceiptRepository(
    private val supabase: SupabaseClient,
    private val json: Json,
    cacheDir: File,
) {
    /** Where the camera writes photos before they're uploaded. Shared with the FileProvider paths. */
    val capturesDir: File = File(cacheDir, "captures")

    /** Where Excel exports are written before being shared. */
    val exportsDir: File = File(cacheDir, "exports")

    private val bucket get() = supabase.storage.from(BUCKET)

    /** Every receipt, newest expense first. */
    suspend fun list(): List<Receipt> =
        supabase.from(RECEIPTS)
            .select {
                order("expense_date", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Receipt>()

    suspend fun get(id: String): Receipt =
        supabase.from(RECEIPTS)
            .select { filter { eq("id", id) } }
            .decodeSingle<Receipt>()

    suspend fun delete(id: String) {
        supabase.from(RECEIPTS).delete { filter { eq("id", id) } }
    }

    /** Deletes every receipt dated in [month], after it has been exported. */
    suspend fun deleteMonth(month: YearMonth) {
        supabase.from(RECEIPTS).delete {
            filter {
                gte("expense_date", month.atDay(1).toString())
                lte("expense_date", month.atEndOfMonth().toString())
            }
        }
    }

    /** Inserts or updates the receipt. Returns the receipt id. */
    suspend fun save(draft: ReceiptDraft): String =
        supabase.postgrest.rpc("save_receipt", draft.toSaveParams()).decodeAs<String>()

    /** Nature-of-expense and company values from recent receipts, newest first, for suggestions. */
    suspend fun pastChoices(): List<PastChoice> =
        supabase.from(RECEIPTS)
            .select(Columns.list("nature_of_expense", "company")) {
                order("created_at", Order.DESCENDING)
                limit(300)
            }
            .decodeList<PastChoice>()

    /** Uploads a JPEG to {userId}/{random}.jpg and returns that storage path. */
    suspend fun uploadImage(file: File): String {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("You're signed out. Sign in again.")
        val path = "$userId/${UUID.randomUUID()}.jpg"
        bucket.upload(path, file.readBytes()) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        return path
    }

    suspend fun extract(imagePath: String): ExtractedReceipt {
        val response = supabase.functions.invoke(
            function = "extract-receipt",
            body = buildJsonObject { put("image_path", imagePath) },
        )
        return json.decodeFromString<ExtractedReceipt>(response.bodyAsText())
    }

    suspend fun imageUrl(path: String): String = bucket.createSignedUrl(path, 10.minutes)

    suspend fun recentImages(): List<StoredImage> =
        supabase.postgrest.rpc("list_recent_receipt_images").decodeList<StoredImage>()

    companion object {
        const val BUCKET = "receipt-images"
        private const val RECEIPTS = "receipts"
    }
}

/** Turns an exception into a sentence the UI can show. Edge Functions return {"error": "..."}. */
fun Throwable.toUserMessage(): String {
    val raw = message.orEmpty()
    Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.let { return it.groupValues[1] }
    return when (this) {
        is RestException -> "The server said no: ${error.ifBlank { "unknown error" }}"
        is java.io.IOException -> "No connection. Check your internet and try again."
        else -> raw.ifBlank { "Something went wrong. Try again." }
    }
}
