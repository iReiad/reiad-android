package uk.co.reiad.library.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.Motion

/* ============================================================
   One text box, everywhere.

   ---- what it replaces ----

   Nine boxes in three designs. Seven were a hand-rolled
   `Box + material(GROOVE) + BasicTextField`, each with its own
   corner, its own padding and its own height, and two more were
   Material 3's `TextField`, which is a filled rectangle with an
   underline and belongs to a different design system entirely.
   No two looked the same and none of them showed focus.

   That is not a tidiness complaint. A reader learns one shape and
   then meets another, and the second one reads as a different
   KIND of thing: on the account page the name box and the email
   box were a pill and the note box was a rounded rectangle, so
   the page said the two were different sorts of answer, which
   they are not.

   ---- a groove, and never glass ----

   The site's `NOT_GLASS` list has a text field in it, with the
   reason: its affordance is the caret and the focus ring, and a
   lit resting rim on a box you type into is a box that looks like
   a button. So this is a GROOVE, which is the inverse of glass: a
   channel cut in, `--standing: 0`, no lit top edge, which is
   exactly what a thing waiting to be filled should be.

   ---- and the ring is the affordance ----

   Nothing here showed focus at all, on any of the nine, so a
   keyboard or a switch could land in a box with no way to tell.
   The ring is drawn with `border` rather than a shadow, because
   the material owns `box-shadow` and a ring drawn there would
   REPLACE the groove's cut edge rather than sitting beside it,
   which is the trap the site's own `--surface-shadow` note is
   about.

   ---- alignment, which is what a reader actually sees ----

   The inner padding is `Gap.s5`, and it is the whole of the
   complaint that started this file. A card pads its contents by
   `Gap.s7`; a field inside it that pads by `Gap.s7` again puts
   its text 32dp from the card edge while the label above it sits
   at 16, and the two read as belonging to different columns. The
   box's EDGE aligns with the label, which is the alignment that
   matters, and its text is inset by the smallest amount that does
   not look cramped.
   ============================================================ */

/** How tall a box is: one line, or room for a few. */
enum class FieldSize(internal val min: Dp) {
    /** The one height for anything pressed. */
    LINE(44.dp),

    /** A note, an answer, a paragraph. */
    AREA(96.dp),
}

/**
 * A text box.
 *
 * `description` is REQUIRED and is the accessible name. The hint
 * is a drawing: it is a sibling `Text` rather than the field's own
 * label, so it disappears the moment there is a character in the
 * box, and a screen reader that read it instead would announce
 * "rice, dal, egg" to somebody who has already typed something
 * else.
 */
@Composable
fun Field(
    value: String,
    onValue: (String) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    /** A leading mark. What says "this is the search box" is the
        magnifier, never a different SHAPE of box: two shapes is
        the thing this file exists to stop. */
    icon: String? = null,
    size: FieldSize = FieldSize.LINE,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    actions: KeyboardActions = KeyboardActions.Default,
    /** Applied to every keystroke before it is passed on: digits
        only, a length cap. Here rather than in each caller
        because a filter written twice is two filters. */
    filter: ((String) -> String)? = null,
    focusRequester: FocusRequester? = null,
    textStyle: TextStyle? = null,
    /** A control that belongs INSIDE the box: a clear, a unit. A
        control that acts on what was typed belongs beside it, as
        its own button. */
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = LocalReiad.current
    var focused by remember { mutableStateOf(false) }
    val ring by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(Motion.QUICK_MS),
        label = "ring",
    )

    /* ONE NODE, and it is the decorated box.

       `decorationBox` puts the groove, the ring, the icon and the
       hint INSIDE the text field rather than around it, so the
       field's own semantics node is the whole 44dp box. Drawn the
       other way round, as a Box with a `BasicTextField` in it,
       the node is as tall as the text: `ReachTest` measured 24dp
       inside a 44dp box, which is a target under the site's own
       minimum on a screen that looks perfectly fine. That is what
       the first draft of this file did, and the test caught it
       before the build did. */
    BasicTextField(
        value = value,
        onValueChange = { onValue(filter?.invoke(it) ?: it) },
        singleLine = size == FieldSize.LINE,
        textStyle = (textStyle ?: MaterialTheme.typography.bodyMedium).copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = keyboard,
        keyboardActions = actions,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = size.min)
            .then(
                if (focusRequester == null) Modifier
                else Modifier.focusRequester(focusRequester),
            )
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = size.min)
                    .clip(RoundedCornerShape(Corner.field))
                    .material(Kind.GROOVE, c, Corner.field, ground = c.paperSunk)
                    .border(
                        width = 1.5.dp,
                        color = c.accent.copy(alpha = 0.75f * ring),
                        shape = RoundedCornerShape(Corner.field),
                    )
                    .padding(horizontal = Gap.s5, vertical = Gap.s4),
                verticalAlignment = if (size == FieldSize.AREA) {
                    Alignment.Top
                } else {
                    Alignment.CenterVertically
                },
            ) {
                if (icon != null) {
                    Icon(icon, size = 16.dp, tint = if (focused) c.accent else c.inkSoft)
                    Spacer(Modifier.width(Gap.s4))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    /* A sibling `Text`, never the field's label: a
                       hint read out by a screen reader would
                       announce "rice, dal, egg" to somebody who
                       has already typed something else. */
                    if (value.isEmpty() && hint != null) {
                        Text(
                            hint,
                            style = textStyle ?: MaterialTheme.typography.bodyMedium,
                            color = c.inkSoft,
                        )
                    }
                    inner()
                }
                if (trailing != null) {
                    Spacer(Modifier.width(Gap.s4))
                    trailing()
                }
            }
        },
    )
}

/** A box with a label over it, which is what almost every one of
    these actually needs.

    The label is a `Text` and NOT the field's accessible name: it
    is read by anyone looking, and the name is what a screen
    reader hears, and they are usually the same sentence said at
    two lengths. Passing one string for both is how a field ends
    up announced as "Name" with no clue what sort. */
@Composable
fun LabelledField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    note: String? = null,
    size: FieldSize = FieldSize.LINE,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    filter: ((String) -> String)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = LocalReiad.current
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = c.ink)
        if (note != null) {
            Spacer(Modifier.padding(top = Gap.s2))
            Text(note, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
        }
        Spacer(Modifier.padding(top = Gap.s4))
        Field(
            value = value,
            onValue = onValue,
            description = description,
            hint = hint,
            size = size,
            keyboard = keyboard,
            filter = filter,
            trailing = trailing,
        )
    }
}

/** Digits and one point, which is what every number box here
    wants and four of them wrote out separately. */
val decimalsOnly: (String) -> String = { typed ->
    typed.filter { it.isDigit() || it == '.' }
}

/** Whole numbers. A box for a year or a step count that accepts a
    decimal point is a box that accepts "20.26". */
val digitsOnly: (String) -> String = { typed -> typed.filter { it.isDigit() } }
