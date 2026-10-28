package com.kyled.receiptscanner.export

import com.kyled.receiptscanner.data.Receipt
import com.kyled.receiptscanner.data.toDecimalOrNull
import java.io.File
import java.math.BigDecimal
import java.time.YearMonth
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaimFormTest {
    private fun receipt(date: String, nature: String, company: String, names: String, amount: Double) =
        Receipt(
            id = "$date-$nature",
            expenseDate = date,
            natureOfExpense = nature,
            company = company,
            names = names,
            amount = amount,
            createdAt = "${date}T12:00:00Z",
        )

    private val october = listOf(
        receipt("2026-10-14", "Supper", "HellermannTyton", "Pedro", 229.90),
        receipt("2026-10-01", "Cell Phone account", "HellermannTyton", "Pedro", 1100.0),
        receipt("2026-10-15", "Lunch & drinks <client>", "Cabstrut", "Pedro/Pieter Erasmus", 586.0),
    )

    /** Also leaves build/sample-claim-form.xlsx behind; CI uploads it for a visual check. */
    @Test
    fun writesTheClaimForm() {
        val file = File("build/sample-claim-form.xlsx")
        writeClaimForm("Pedro Dias Lobo", YearMonth.of(2026, 10), october, file)

        ZipFile(file).use { zip ->
            val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).reader().readText()
            // Entries sorted by date, starting on row 8 like the template.
            assertTrue(sheet.contains("""<c r="B8" s="""))
            assertTrue(sheet.indexOf("Cell Phone account") < sheet.indexOf("Supper"))
            assertTrue(sheet.contains("Lunch &amp; drinks &lt;client&gt;"))
            // 18 entry rows minimum, so the sub total sits on row 26 like the blank template.
            assertTrue(sheet.contains("<f>SUM(H8:H25)</f><v>1915.9</v>"))
            assertTrue(sheet.contains("""<mergeCell ref="B7:C7"/>"""))
            assertTrue(zip.getEntry("xl/styles.xml") != null)
        }
    }

    @Test
    fun claimFileNameUsesShortMonth() {
        assertEquals("Entertainment & Expense Claims Oct 2026.xlsx", claimFileName(YearMonth.of(2026, 10)))
    }

    @Test
    fun parsesTypedAmounts() {
        assertEquals(BigDecimal("1100"), "1100".toDecimalOrNull())
        assertEquals(BigDecimal("1100.50"), "R 1 100.50".toDecimalOrNull())
        assertEquals(BigDecimal("1100.50"), "1,100.50".toDecimalOrNull())
        assertEquals(BigDecimal("1100"), "1,100".toDecimalOrNull())
        assertEquals(BigDecimal("1100.5"), "1100,5".toDecimalOrNull())
        assertEquals(null, "abc".toDecimalOrNull())
    }
}
