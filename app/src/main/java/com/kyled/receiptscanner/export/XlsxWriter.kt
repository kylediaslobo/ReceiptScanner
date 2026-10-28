package com.kyled.receiptscanner.export

import java.io.OutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A minimal .xlsx writer: text, numbers (2 decimals) and dates, a bold frozen header row and
 * column widths. An .xlsx file is a zip of XML parts, so this avoids pulling Apache POI
 * (several MB, and awkward on Android) into the APK.
 */
class XlsxWriter {
    sealed interface Cell {
        data class Text(val value: String) : Cell
        data class Number(val value: Double) : Cell
        data class Date(val value: LocalDate) : Cell
        data object Empty : Cell
    }

    data class Column(val header: String, val width: Double = 14.0)

    private data class Sheet(val name: String, val columns: List<Column>, val rows: List<List<Cell>>)

    private val sheets = mutableListOf<Sheet>()

    fun addSheet(name: String, columns: List<Column>, rows: List<List<Cell>>) {
        sheets += Sheet(name.take(31), columns, rows)
    }

    fun writeTo(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", contentTypes())
            zip.put("_rels/.rels", ROOT_RELS)
            zip.put("xl/workbook.xml", workbook())
            zip.put("xl/_rels/workbook.xml.rels", workbookRels())
            zip.put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, sheet -> zip.put("xl/worksheets/sheet${i + 1}.xml", worksheet(sheet)) }
        }
    }

    private fun ZipOutputStream.put(name: String, xml: String) {
        putNextEntry(ZipEntry(name))
        write(xml.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        sheets.indices.forEach { i ->
            append("""<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { i, sheet ->
            append("""<sheet name="${escape(sheet.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        sheets.indices.forEach { i ->
            append("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
        }
        append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun worksheet(sheet: Sheet) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        append("<cols>")
        sheet.columns.forEachIndexed { i, col ->
            append("""<col min="${i + 1}" max="${i + 1}" width="${col.width}" customWidth="1"/>""")
        }
        append("</cols><sheetData>")

        append("""<row r="1">""")
        sheet.columns.forEachIndexed { c, col -> append(textCell(ref(c, 1), col.header, STYLE_HEADER)) }
        append("</row>")

        sheet.rows.forEachIndexed { r, row ->
            val rowNum = r + 2
            append("""<row r="$rowNum">""")
            row.forEachIndexed { c, cell ->
                when (cell) {
                    is Cell.Text -> append(textCell(ref(c, rowNum), cell.value, STYLE_DEFAULT))
                    is Cell.Number -> append("""<c r="${ref(c, rowNum)}" s="$STYLE_MONEY"><v>${cell.value}</v></c>""")
                    is Cell.Date -> append("""<c r="${ref(c, rowNum)}" s="$STYLE_DATE"><v>${excelSerial(cell.value)}</v></c>""")
                    Cell.Empty -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun textCell(ref: String, value: String, style: Int) =
        """<c r="$ref" t="inlineStr" s="$style"><is><t xml:space="preserve">${escape(value)}</t></is></c>"""

    private fun ref(col: Int, row: Int): String {
        var n = col + 1
        val letters = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            letters.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return "$letters$row"
    }

    /** Excel stores dates as days since 1899-12-30. */
    private fun excelSerial(date: LocalDate): Long = ChronoUnit.DAYS.between(EXCEL_EPOCH, date)

    private fun escape(s: String) = buildString {
        s.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> if (ch == '\t' || ch == '\n' || ch == '\r' || ch >= ' ') append(ch)
            }
        }
    }

    private companion object {
        val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
        const val STYLE_DEFAULT = 0
        const val STYLE_HEADER = 1
        const val STYLE_MONEY = 2
        const val STYLE_DATE = 3

        const val ROOT_RELS =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

        // Cell formats: 0 default, 1 bold header, 2 number "0.00" (built-in 2), 3 date (built-in 14).
        const val STYLES =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
    }
}
