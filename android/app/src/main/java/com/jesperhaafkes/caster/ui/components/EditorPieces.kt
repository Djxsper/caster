package com.jesperhaafkes.caster.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jesperhaafkes.caster.ui.theme.CasterFontFamily
import com.jesperhaafkes.caster.ui.theme.LocalTheme

/**
 * The switcher at the top of both editors: what is currently loaded, how many
 * things are on it, and a menu of the other saved sets.
 */
/**
 * Every field in these editors holds a person's name or a wheel entry. The
 * platform default lowercases the first letter and autocorrects, which quietly
 * turns names into dictionary words. Swift turns both off at
 * `RosterEditor.swift:186-188`; this is the same call.
 */
private val NameKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.Words,
    autoCorrectEnabled = false,
    imeAction = ImeAction.Done,
)

@Composable
fun PickerHeader(
    glyph: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val theme = LocalTheme.current
    var isMenuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(theme.surfaceRaised)
                .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                .tappable { isMenuOpen = true }
                .padding(vertical = 12.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = glyph, style = TextStyle(fontFamily = CasterFontFamily, fontSize = 17.sp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = CasterFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.textPrimary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = TextStyle(fontFamily = CasterFontFamily, fontSize = 12.sp, color = theme.textSecondary),
                )
            }

            Chevrons()
        }

        DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
            menu { isMenuOpen = false }
        }
    }
}

@Composable
private fun Chevrons() {
    val theme = LocalTheme.current
    Canvas(Modifier.size(14.dp)) {
        val stroke = 1.6.dp.toPx()
        val midX = size.width / 2f
        drawLine(
            theme.textSecondary,
            Offset(midX - size.width * 0.28f, size.height * 0.36f),
            Offset(midX, size.height * 0.16f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            theme.textSecondary,
            Offset(midX, size.height * 0.16f),
            Offset(midX + size.width * 0.28f, size.height * 0.36f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            theme.textSecondary,
            Offset(midX - size.width * 0.28f, size.height * 0.64f),
            Offset(midX, size.height * 0.84f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            theme.textSecondary,
            Offset(midX, size.height * 0.84f),
            Offset(midX + size.width * 0.28f, size.height * 0.64f),
            stroke,
            StrokeCap.Round,
        )
    }
}

/** A menu row, so both editors' menus read the same way. */
@Composable
fun EditorMenuItem(
    label: String,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val theme = LocalTheme.current
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = CasterFontFamily,
                    fontSize = 15.sp,
                    color = if (isDestructive) theme.danger else theme.textPrimary,
                ),
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun EditorMenuDivider() {
    HorizontalDivider(color = LocalTheme.current.border)
}

/**
 * The field-plus-plus-button row both editors keep above their list. Focus is
 * held after a commit so a dozen names can be typed straight through without
 * reaching back for the field between each one.
 */
@Composable
fun AddRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canCommit: Boolean = value.isNotBlank(),
    focusRequester: FocusRequester = remember { FocusRequester() },
    onCommit: () -> Unit,
) {
    val theme = LocalTheme.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FieldBox(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(fontFamily = CasterFontFamily, fontSize = 16.sp, color = theme.textPrimary),
                cursorBrush = SolidColor(theme.accent),
                keyboardOptions = NameKeyboard,
                keyboardActions = KeyboardActions(onDone = { onCommit() }),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(fontFamily = CasterFontFamily, fontSize = 16.sp, color = theme.textSecondary),
                        )
                    }
                    inner()
                },
            )
        }

        Box(
            modifier = Modifier
                .size(46.dp)
                .alpha(if (canCommit) 1f else 0.45f)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.accent)
                .tappable(enabled = canCommit, onClick = onCommit),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(18.dp)) {
                val stroke = 2.6.dp.toPx()
                drawLine(
                    Color.White,
                    Offset(size.width / 2f, 0f),
                    Offset(size.width / 2f, size.height),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    Color.White,
                    Offset(0f, size.height / 2f),
                    Offset(size.width, size.height / 2f),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun FieldBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val theme = LocalTheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(theme.surfaceRaised)
            .border(1.dp, theme.border, RoundedCornerShape(10.dp))
            .padding(vertical = 12.dp, horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
        content = { content() },
    )
}

/**
 * One editable row: the seat or slice colour, the name itself, and the controls
 * iOS gets free from a `List` — reorder and delete.
 *
 * The field is bound straight through to the store, so an edit persists on
 * every keystroke rather than needing a separate save step.
 */
@Composable
fun EditorRow(
    swatch: Color,
    value: String,
    onValueChange: (String) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTheme.current
    val focusManager = LocalFocusManager.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(swatch)
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(fontFamily = CasterFontFamily, fontSize = 16.sp, color = theme.textPrimary),
            cursorBrush = SolidColor(theme.accent),
            keyboardOptions = NameKeyboard,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )

        Spacer(Modifier.width(2.dp))
        ArrowButton(pointsUp = true, enabled = onMoveUp != null) { onMoveUp?.invoke() }
        ArrowButton(pointsUp = false, enabled = onMoveDown != null) { onMoveDown?.invoke() }
        DeleteButton(onDelete)
    }
}

@Composable
private fun ArrowButton(pointsUp: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalTheme.current
    Box(
        modifier = Modifier
            .size(30.dp)
            .alpha(if (enabled) 1f else 0.25f)
            .clip(CircleShape)
            .tappable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(13.dp)) {
            val stroke = 2f.dp.toPx()
            val tipY = if (pointsUp) size.height * 0.28f else size.height * 0.72f
            val baseY = if (pointsUp) size.height * 0.66f else size.height * 0.34f
            drawLine(
                theme.textSecondary,
                Offset(size.width * 0.15f, baseY),
                Offset(size.width / 2f, tipY),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                theme.textSecondary,
                Offset(size.width / 2f, tipY),
                Offset(size.width * 0.85f, baseY),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    val theme = LocalTheme.current
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = 2f.dp.toPx()
            drawLine(
                theme.danger,
                Offset(0f, 0f),
                Offset(size.width, size.height),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                theme.danger,
                Offset(size.width, 0f),
                Offset(0f, size.height),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

/** What a list looks like before anything is on it. */
@Composable
fun EditorEmptyState(glyph: String, message: String, modifier: Modifier = Modifier) {
    val theme = LocalTheme.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = glyph, style = TextStyle(fontFamily = CasterFontFamily, fontSize = 40.sp))
        Text(
            text = message,
            style = TextStyle(fontFamily = CasterFontFamily, fontSize = 15.sp, color = theme.textSecondary),
        )
    }
}

/** One dialog serving both "new set" and "rename set". */
@Composable
fun NamePromptDialog(
    title: String,
    message: String?,
    placeholder: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val theme = LocalTheme.current
    var draft by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.surfaceRaised,
        title = {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = CasterFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message != null) {
                    Text(
                        text = message,
                        style = TextStyle(fontFamily = CasterFontFamily, fontSize = 13.sp, color = theme.textSecondary),
                    )
                }
                FieldBox {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontFamily = CasterFontFamily, fontSize = 16.sp, color = theme.textPrimary),
                        cursorBrush = SolidColor(theme.accent),
                        keyboardOptions = NameKeyboard,
                        keyboardActions = KeyboardActions(onDone = { onSave(draft) }),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = TextStyle(
                                        fontFamily = CasterFontFamily,
                                        fontSize = 16.sp,
                                        color = theme.textSecondary,
                                    ),
                                )
                            }
                            inner()
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text("Save", style = TextStyle(fontFamily = CasterFontFamily, color = theme.accent, fontSize = 15.sp))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = TextStyle(fontFamily = CasterFontFamily, color = theme.textSecondary, fontSize = 15.sp))
            }
        },
    )
}

/** The confirmation both editors put in front of deleting a whole set. */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val theme = LocalTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.surfaceRaised,
        title = {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = CasterFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary,
                ),
            )
        },
        text = {
            Text(text = message, style = TextStyle(fontFamily = CasterFontFamily, fontSize = 13.sp, color = theme.textSecondary))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", style = TextStyle(fontFamily = CasterFontFamily, color = theme.danger, fontSize = 15.sp))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = TextStyle(fontFamily = CasterFontFamily, color = theme.textSecondary, fontSize = 15.sp))
            }
        },
    )
}

/** The overflow menu both editors hang their set-level actions off. */
@Composable
fun OptionsMenuButton(
    modifier: Modifier = Modifier,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val theme = LocalTheme.current
    var isOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .tappable { isOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(18.dp)) {
                val radius = 1.8.dp.toPx()
                val y = size.height / 2f
                for (fraction in listOf(0.18f, 0.5f, 0.82f)) {
                    drawCircle(theme.textSecondary, radius, Offset(size.width * fraction, y))
                }
            }
        }

        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            menu { isOpen = false }
        }
    }
}

/** A word rather than a glyph in the title bar, for "Done". */
@Composable
fun BarTextAction(label: String, onClick: () -> Unit) {
    val theme = LocalTheme.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .tappable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = CasterFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.accent,
            ),
        )
    }
}
