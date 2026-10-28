package com.kyled.receiptscanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyled.receiptscanner.data.ReceiptDraft
import com.kyled.receiptscanner.data.formatDay
import com.kyled.receiptscanner.data.formatRand
import com.kyled.receiptscanner.data.toDecimalOrNull
import com.kyled.receiptscanner.data.toLocalDateOrNull
import com.kyled.receiptscanner.ui.theme.LocalPaperColors
import com.kyled.receiptscanner.ui.theme.ReceiptType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Values from earlier receipts, offered as one-tap chips under the prompted fields. */
data class Suggestions(
    val natureOfExpense: List<String> = emptyList(),
    val company: List<String> = emptyList(),
)

/**
 * The receipt drawn as a slip of paper (design B), holding one line of the expense claim form.
 * With [editable] every value becomes a field; the date opens a calendar.
 */
@Composable
fun PaperReceipt(
    draft: ReceiptDraft,
    editable: Boolean,
    onChange: (ReceiptDraft) -> Unit,
    modifier: Modifier = Modifier,
    suggestions: Suggestions = Suggestions(),
) {
    val colors = LocalPaperColors.current

    Column(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .background(colors.paper, RoundedCornerShape(4.dp))
            .border(0.5.dp, colors.rule, RoundedCornerShape(4.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            "EXPENSE CLAIM",
            style = ReceiptType.heading,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        DashedDivider()

        FieldLabel("Date")
        DateField(date = draft.date, editable = editable, onChange = { onChange(draft.copy(date = it)) })

        FieldLabel("Nature of expense")
        ReceiptField(
            value = draft.natureOfExpense,
            onValueChange = { onChange(draft.copy(natureOfExpense = it)) },
            editable = editable,
            placeholder = "What was it for? e.g. Supper",
            style = ReceiptType.body,
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        if (editable) {
            SuggestionRow(draft.natureOfExpense, suggestions.natureOfExpense) {
                onChange(draft.copy(natureOfExpense = it))
            }
        }

        FieldLabel("Company")
        ReceiptField(
            value = draft.company,
            onValueChange = { onChange(draft.copy(company = it)) },
            editable = editable,
            placeholder = "Which company? e.g. HellermannTyton",
            style = ReceiptType.body,
            capitalization = KeyboardCapitalization.Words,
            modifier = Modifier.fillMaxWidth(),
        )
        if (editable) {
            SuggestionRow(draft.company, suggestions.company) { onChange(draft.copy(company = it)) }
        }

        FieldLabel("Names")
        ReceiptField(
            value = draft.names,
            onValueChange = { onChange(draft.copy(names = it)) },
            editable = editable,
            placeholder = "Who was there? e.g. Pedro/Victor",
            style = ReceiptType.body,
            capitalization = KeyboardCapitalization.Words,
            modifier = Modifier.fillMaxWidth(),
        )

        DashedDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AMOUNT", style = ReceiptType.total, color = colors.ink)
            Spacer(Modifier.weight(1f))
            if (editable) {
                Text("R ", style = ReceiptType.total, color = colors.inkMuted)
                ReceiptField(
                    value = draft.amount,
                    onValueChange = { onChange(draft.copy(amount = it)) },
                    editable = true,
                    placeholder = "0.00",
                    style = ReceiptType.total,
                    textAlign = TextAlign.End,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.width(140.dp),
                )
            } else {
                val amount = draft.amount.toDecimalOrNull()
                Text(amount?.let(::formatRand) ?: draft.amount, style = ReceiptType.total, color = colors.ink)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        style = ReceiptType.small,
        color = LocalPaperColors.current.inkMuted,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: String, editable: Boolean, onChange: (String) -> Unit) {
    val colors = LocalPaperColors.current
    var picking by remember { mutableStateOf(false) }

    val shown = if (date.isBlank()) "Tap to pick the date" else formatDay(date)
    Text(
        text = shown,
        style = ReceiptType.body,
        color = if (date.isBlank()) colors.accent else colors.ink,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (editable) Modifier.clickable { picking = true } else Modifier)
            .padding(vertical = 2.dp),
    )

    if (picking) {
        // The picker works in UTC midnight millis.
        val initial = (date.toLocalDateOrNull() ?: LocalDate.now()).toEpochDay() * MILLIS_PER_DAY
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/** Earlier values that match what's typed so far. Hidden once the field holds one exactly. */
@Composable
private fun SuggestionRow(current: String, options: List<String>, onPick: (String) -> Unit) {
    val typed = current.trim()
    if (options.any { it.equals(typed, ignoreCase = true) }) return
    val matches = options.filter { typed.isEmpty() || it.contains(typed, ignoreCase = true) }.take(8)
    if (matches.isEmpty()) return

    val colors = LocalPaperColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp),
    ) {
        matches.forEach { option ->
            SuggestionChip(
                onClick = { onPick(option) },
                label = { Text(option, style = ReceiptType.small) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Color.Transparent,
                    labelColor = colors.accent,
                ),
                border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = colors.rule),
            )
        }
    }
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
        color = if (style.color == Color.Unspecified) colors.ink else style.color,
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
                        // Empty prompted fields read in the accent colour so they stand out as "fill me in".
                        style = textStyle.copy(color = colors.accent.copy(alpha = 0.75f)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                inner()
            }
        },
    )
}
