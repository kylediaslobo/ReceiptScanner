package com.kyled.receiptscanner.export

import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A minimal .xlsx writer for one formatted sheet: styled cells, merged ranges, formulas, column
 * widths, row heights and A4 print setup. An .xlsx file is a zip of XML parts, so this avoids pulling
 * Apache POI (several MB, and awkward on Android) into the APK.
 *
 * Rows are 1-based like Excel's; columns are 0-based (0 = A).
 */
class XlsxWriter(private val sheetName: String) {
    enum class Align { GENERAL, LEFT, CENTER, RIGHT }

    enum class NumberFormat(val id: Int, val code: String?) {
        GENERAL(0, null),
        DECIMAL(4, null),
        DATE(14, null),
        RAND(164, "\"R\"\\ #,##0.00"),
        MONTH(165, "mmm\\-yy"),
    }

    data class Borders(
        val left: Boolean = false,
        val right: Boolean = false,
        val top: Boolean = false,
        val bottom: Boolean = false,
    ) {
        companion object {
            val NONE = Borders()
            val BOX = Borders(left = true, right = true, top = true, bottom = true)
        }
    }

    data class Style(
        val bold: Boolean = false,
        /** The claim form's lavender shading. */
        val shaded: Boolean = false,
        val borders: Borders = Borders.NONE,
        val align: Align = Align.GENERAL,
        val format: NumberFormat = NumberFormat.GENERAL,
    )

    sealed interface Value {
        data class Text(val value: String) : Value
        data class Number(val value: BigDecimal) : Value
        data class Date(val value: LocalDate) : Value
        /** [cached] is shown by viewers that don't recalculate; Excel recalculates on open. */
        data class Formula(val expression: String, val cached: BigDecimal) : Value
    }

    private class Cell(val value: Value?, val style: Style)

    private val rows = sortedMapOf<Int, java.util.SortedMap<Int, Cell>>()
    private val rowHeights = mutableMapOf<Int, Double>()
    private val columnWidths = sortedMapOf<Int, Double>()
    private val merges = mutableListOf<String>()

    fun set(row: Int, col: Int, value: Value?, style: Style = Style()) {
        rows.getOrPut(row) { sortedMapOf() }[col] = Cell(value, style)
    }

    fun text(row: Int, col: Int, value: String, style: Style = Style()) = set(row, col, Value.Text(value), style)

    /** Merges [firstCol]..[lastCol] on one row. The style goes on every cell so borders and shading cover the range. */
    fun merge(row: Int, firstCol: Int, lastCol: Int, value: Value?, style: Style = Style()) {
        for (c in firstCol..lastCol) set(row, c, if (c == firstCol) value else null, style)
        merges += "${ref(firstCol, row)}:${ref(lastCol, row)}"
    }

    fun rowHeight(row: Int, points: Double) {
        rowHeights[row] = points
    }

    fun columnWidth(col: Int, characters: Double) {
        columnWidths[col] = characters
    }

    fun writeTo(out: OutputStream) {
        val styles = rows.values.flatMap { it.values }.map { it.style }
        val styleIndex = (listOf(Style()) + styles).distinct().withIndex().associate { (i, s) -> s to i }

        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", CONTENT_TYPES)
            zip.put("_rels/.rels", ROOT_RELS)
            zip.put("xl/workbook.xml", workbook())
            zip.put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.put("xl/styles.xml", stylesXml(styleIndex.keys.toList()))
            zip.put("xl/worksheets/sheet1.xml", worksheet(styleIndex))
        }
    }

    private fun ZipOutputStream.put(name: String, xml: String) {
        putNextEntry(ZipEntry(name))
        write(xml.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun workbook() = buildString {
        append(XML_HEADER)
        append("""<workbook xmlns="$MAIN_NS" xmlns:r="$REL_NS"><sheets>""")
        append("""<sheet name="${escape(sheetName.take(31))}" sheetId="1" r:id="rId1"/>""")
        append("""</sheets><calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>""")
    }

    private fun worksheet(styleIndex: Map<Style, Int>) = buildString {
        append(XML_HEADER)
        append("""<worksheet xmlns="$MAIN_NS" xmlns:r="$REL_NS">""")
        append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
        append("""<sheetViews><sheetView workbookViewId="0"/></sheetViews>""")
        append("""<sheetFormatPr defaultRowHeight="15"/>""")
        if (columnWidths.isNotEmpty()) {
            append("<cols>")
            columnWidths.forEach { (c, w) -> append("""<col min="${c + 1}" max="${c + 1}" width="$w" customWidth="1"/>""") }
            append("</cols>")
        }
        append("<sheetData>")
        (rows.keys + rowHeights.keys).toSortedSet().forEach { r ->
            val height = rowHeights[r]?.let { """ ht="$it" customHeight="1"""" }.orEmpty()
            append("""<row r="$r"$height>""")
            rows[r]?.forEach { (c, cell) -> append(cellXml(ref(c, r), cell, styleIndex.getValue(cell.style))) }
            append("</row>")
        }
        append("</sheetData>")
        if (merges.isNotEmpty()) {
            append("""<mergeCells count="${merges.size}">""")
            merges.forEach { append("""<mergeCell ref="$it"/>""") }
            append("</mergeCells>")
        }
        append("""<pageMargins left="0.5" right="0.5" top="0.6" bottom="0.6" header="0.3" footer="0.3"/>""")
        append("""<pageSetup paperSize="9" orientation="portrait" fitToWidth="1" fitToHeight="0"/>""")
        append("</worksheet>")
    }

    private fun cellXml(ref: String, cell: Cell, style: Int): String = when (val v = cell.value) {
        null -> """<c r="$ref" s="$style"/>"""
        is Value.Text -> """<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">${escape(v.value)}</t></is></c>"""
        is Value.Number -> """<c r="$ref" s="$style"><v>${v.value.toPlainString()}</v></c>"""
        is Value.Date -> """<c r="$ref" s="$style"><v>${ChronoUnit.DAYS.between(EXCEL_EPOCH, v.value)}</v></c>"""
        is Value.Formula -> """<c r="$ref" s="$style"><f>${escape(v.expression)}</f><v>${v.cached.toPlainString()}</v></c>"""
    }

    private fun stylesXml(styles: List<Style>) = buildString {
        val fonts = (listOf(false) + styles.map { it.bold }).distinct()
        val borders = (listOf(Borders.NONE) + styles.map { it.borders }).distinct()
        val formats = NumberFormat.entries.filter { it.code != null }

        append(XML_HEADER)
        append("""<styleSheet xmlns="$MAIN_NS">""")
        append("""<numFmts count="${formats.size}">""")
        formats.forEach { append("""<numFmt numFmtId="${it.id}" formatCode="${escape(it.code!!)}"/>""") }
        append("</numFmts>")

        append("""<fonts count="${fonts.size}">""")
        fonts.forEach { bold ->
            append("<font>")
            if (bold) append("<b/>")
            append("""<sz val="9"/><name val="Segoe UI"/><family val="2"/></font>""")
        }
        append("</fonts>")

        // 0 and 1 are required by Excel; 2 is the form's lavender (#CCCCFF).
        append("""<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""")
        append("""<fill><patternFill patternType="solid"><fgColor rgb="FFCCCCFF"/><bgColor indexed="64"/></patternFill></fill></fills>""")

        append("""<borders count="${borders.size}">""")
        borders.forEach { b ->
            append("<border>")
            listOf("left" to b.left, "right" to b.right, "top" to b.top, "bottom" to b.bottom).forEach { (side, on) ->
                append(if (on) """<$side style="thin"><color auto="1"/></$side>""" else "<$side/>")
            }
            append("<diagonal/></border>")
        }
        append("</borders>")

        append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")
        append("""<cellXfs count="${styles.size}">""")
        styles.forEach { s ->
            val align = when (s.align) {
                Align.GENERAL -> ""
                Align.LEFT -> """ horizontal="left""""
                Align.CENTER -> """ horizontal="center""""
                Align.RIGHT -> """ horizontal="right""""
            }
            append(
                """<xf numFmtId="${s.format.id}" fontId="${fonts.indexOf(s.bold)}" fillId="${if (s.shaded) 2 else 0}" """ +
                    """borderId="${borders.indexOf(s.borders)}" xfId="0" applyNumberFormat="1" applyFont="1" """ +
                    """applyFill="1" applyBorder="1" applyAlignment="1"><alignment$align vertical="center"/></xf>""",
            )
        }
        append("</cellXfs>")
        append("""<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""")
        append("</styleSheet>")
    }

    private companion object {
        val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

        const val XML_HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
        const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

        const val CONTENT_TYPES =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""

        const val ROOT_RELS =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

        const val WORKBOOK_RELS =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

        fun ref(col: Int, row: Int): String {
            var n = col + 1
            val letters = StringBuilder()
            while (n > 0) {
                val rem = (n - 1) % 26
                letters.insert(0, 'A' + rem)
                n = (n - 1) / 26
            }
            return "$letters$row"
        }

        fun escape(s: String) = buildString {
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
    }
}
