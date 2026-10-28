package com.kyled.receiptscanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyled.receiptscanner.data.DraftExtra
import com.kyled.receiptscanner.data.DraftLine
import com.kyled.receiptscanner.data.ReceiptDraft
import com.kyled.receiptscanner.data.linesTotal
import com.kyled.receiptscanner.data.toDecimalOrNull
import com.kyled.receiptscanner.ui.theme.LocalPaperColors
import com.kyled.receiptscanner.ui.theme.ReceiptType

/**
 * The receipt drawn as a slip of paper (design B). With [editable] every value becomes a field;
 * tapping a line opens its quantity / unit price / delete controls.
 */
@Composable
fun PaperReceipt(
    draft: ReceiptDraft,
    editable: Boolean,
    onChange: (ReceiptDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPaperColors.current
    var expandedLine by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .background(colors.paper, RoundedCornerShape(4.dp))
            .border(0.5.dp, colors.rule, RoundedCornerShape(4.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        // Header: merchant, address, date and time
        ReceiptField(
            value = draft.merchantName,
            onValueChange = { onChange(draft.copy(merchantName = it)) },
            editable = editable,
            placeholder = "MERCHANT",
            style = ReceiptType.heading,
            textAlign = TextAlign.Center,
            capitalization = KeyboardCapitalization.Words,
            modifier = Modifier.fillMaxWidth(),
        )
        ReceiptField(
            value = draft.merchantAddress,
            onValueChange = { onChange(draft.copy(merchantAddress = it)) },
            editable = editable,
            placeholder = "Address",
            style = ReceiptType.small.copy(color = colors.inkMuted),
            textAlign = TextAlign.Center,
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReceiptField(
                value = draft.date,
                onValueChange = { onChange(draft.copy(date = it)) },
                editable = editable,
                placeholder = "YYYY-MM-DD",
                style = ReceiptType.small.copy(color = colors.inkMuted),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(112.dp),
            )
            if (editable || (draft.date.isNotBlank() && draft.time.isNotBlank())) {
                Text(" · ", style = ReceiptType.small, color = colors.inkMuted)
            }
            ReceiptField(
                value = draft.time,
                onValueChange = { onChange(draft.copy(time = it)) },
                editable = editable,
                placeholder = "HH:MM",
                style = ReceiptType.small.copy(color = colors.inkMuted),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp),
            )
        }

        DashedDivider()

        // Line items
        if (draft.lines.isEmpty() && !editable) {
            Text("No line items", style = ReceiptType.small, color = colors.inkMuted)
        }
        draft.lines.forEach { line ->
            LineRow(
                line = line,
                editable = editable,
                expanded = editable && expandedLine == line.key,
                onToggle = { expandedLine = if (expandedLine == line.key) null else line.key },
                onChange = { updated ->
                    onChange(draft.copy(lines = draft.lines.map { if (it.key == line.key) updated else it }))
                },
                onDelete = {
                    onChange(draft.copy(lines = draft.lines.filterNot { it.key == line.key }))
                },
            )
        }
        if (editable) {
            AddRowButton("+ add line") {
                val line = DraftLine(quantity = "1")
                expandedLine = line.key
                onChange(draft.copy(lines = draft.lines + line))
            }
        }

        DashedDivider()

        // Totals
        AmountRow("Subtotal", draft.subtotal, editable) { onChange(draft.copy(subtotal = it)) }
        AmountRow("Tax", draft.tax, editable) { onChange(draft.copy(tax = it)) }
        AmountRow("Tip", draft.tip, editable) { onChange(draft.copy(tip = it)) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TOTAL", style = ReceiptType.total, color = colors.ink)
            Spacer(Modifier.width(8.dp))
            ReceiptField(
                value = draft.currency,
                onValueChange = { onChange(draft.copy(currency = it.uppercase().take(3))) },
                editable = editable,
                placeholder = "CUR",
                style = ReceiptType.total.copy(color = colors.inkMuted),
                capitalization = KeyboardCapitalization.Characters,
                modifier = Modifier.width(52.dp),
            )
            Spacer(Modifier.weight(1f))
            ReceiptField(
                value = draft.total,
                onValueChange = { onChange(draft.copy(total = it)) },
                editable = editable,
                placeholder = "0.00",
                style = ReceiptType.total,
                textAlign = TextAlign.End,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.width(120.dp),
            )
        }
        LinesTotalHint(draft)

        if (editable || draft.paymentMethod.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            LabelValueRow("Paid by") {
                ReceiptField(
                    value = draft.paymentMethod,
                    onValueChange = { onChange(draft.copy(paymentMethod = it)) },
                    editable = editable,
                    placeholder = "Card, cash…",
                    style = ReceiptType.body,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Extra fields (table number, server, receipt number…)
        if (draft.extras.isNotEmpty() || editable) {
            DashedDivider()
            draft.extras.forEach { extra ->
                ExtraRow(
                    extra = extra,
                    editable = editable,
                    onChange = { updated ->
                        onChange(draft.copy(extras = draft.extras.map { if (it.key == extra.key) updated else it }))
                    },
                    onDelete = { onChange(draft.copy(extras = draft.extras.filterNot { it.key == extra.key })) },
                )
            }
            if (editable) {
                AddRowButton("+ add field") { onChange(draft.copy(extras = draft.extras + DraftExtra())) }
            }
        }
    }
}

@Composable
private fun LineRow(
    line: DraftLine,
    editable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (DraftLine) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalPaperColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (editable) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ReceiptField(
                value = line.description,
                onValueChange = { onChange(line.copy(description = it)) },
                editable = expanded,
                placeholder = "Item",
                style = ReceiptType.body,
                singleLine = false,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            ReceiptField(
                value = line.amount,
                onValueChange = { onChange(line.copy(amount = it)) },
                editable = expanded,
                placeholder = "0.00",
                style = ReceiptType.body,
                textAlign = TextAlign.End,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.width(96.dp),
            )
        }
        val qty = line.quantity.toDecimalOrNull()
        val showQty = (qty != null && qty.compareTo(java.math.BigDecimal.ONE) != 0) || line.unitPrice.isNotBlank()
        if (expanded) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text("qty ", style = ReceiptType.small, color = colors.inkMuted)
                ReceiptField(
                    value = line.quantity,
                    onValueChange = { onChange(line.copy(quantity = it)) },
                    editable = true,
                    placeholder = "1",
                    style = ReceiptType.small,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.width(48.dp),
                )
                Text("  @ ", style = ReceiptType.small, color = colors.inkMuted)
                ReceiptField(
                    value = line.unitPrice,
                    onValueChange = { onChange(line.copy(unitPrice = it)) },
                    editable = true,
                    placeholder = "unit price",
                    style = ReceiptType.small,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.width(96.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove line", tint = colors.inkMuted)
                }
            }
        } else if (showQty) {
            Text(
                text = "  ${line.quantity.ifBlank { "1" }} x ${line.unitPrice.ifBlank { "—" }}",
                style = ReceiptType.small,
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun AmountRow(label: String, value: String, editable: Boolean, onValueChange: (String) -> Unit) {
    if (!editable && value.isBlank()) return
    LabelValueRow(label) {
        ReceiptField(
            value = value,
            onValueChange = onValueChange,
            editable = editable,
            placeholder = "—",
            style = ReceiptType.body,
            textAlign = TextAlign.End,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LabelValueRow(label: String, value: @Composable () -> Unit) {
    val colors = LocalPaperColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ReceiptType.body, color = colors.inkMuted, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f)) { value() }
    }
}

@Composable
private fun ExtraRow(extra: DraftExtra, editable: Boolean, onChange: (DraftExtra) -> Unit, onDelete: () -> Unit) {
    val colors = LocalPaperColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReceiptField(
            value = extra.label,
            onValueChange = { onChange(extra.copy(label = it)) },
            editable = editable,
            placeholder = "Label",
            style = ReceiptType.small.copy(color = colors.inkMuted),
            modifier = Modifier.weight(0.4f),
        )
        ReceiptField(
            value = extra.value,
            onValueChange = { onChange(extra.copy(value = it)) },
            editable = editable,
            placeholder = "Value",
            style = ReceiptType.small,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f),
        )
        if (editable) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove field", tint = colors.inkMuted)
            }
        }
    }
}

@Composable
private fun LinesTotalHint(draft: ReceiptDraft) {
    val sum = draft.linesTotal() ?: return
    val total = draft.total.toDecimalOrNull()
    if (total != null && total.compareTo(sum) == 0) return
    Text(
        text = "Lines add up to ${sum.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()}",
        style = ReceiptType.small,
        color = LocalPaperColors.current.inkMuted,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

@Composable
private fun AddRowButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = ReceiptType.small,
        color = LocalPaperColors.current.accent,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    val color = LocalPaperColors.current.rule
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(1.dp),
    ) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
    }
}

/** A borderless text field in receipt type. Read-only mode renders plain text and hides empty values. */
@Composable
fun ReceiptField(
    value: String,
    onValueChange: (String) -> Unit,
    editable: Boolean,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    val colors = LocalPaperColors.current
    val textStyle = style.copy(
        color = if (style.color.isUnspecifiedColor()) colors.ink else style.color,
        textAlign = textAlign,
    )
    if (!editable) {
        if (value.isNotBlank()) Text(value, style = textStyle, modifier = modifier)
        return
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        singleLine = singleLine,
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = textStyle.copy(color = colors.inkMuted.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                inner()
            }
        },
    )
}

private fun androidx.compose.ui.graphics.Color.isUnspecifiedColor() =
    this == androidx.compose.ui.graphics.Color.Unspecified
