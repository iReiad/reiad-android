package uk.co.reiad.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.Reader

/* ============================================================
   The account, and what being signed in actually means.

   Four sentences, and the screen says all four out loud rather
   than leaving a reader to work out what a Sign in button will
   do to the ticks already on their phone:

   | | |
   | --- | --- |
   | signed out | nothing. No request, no listener, no storage touched. Progress is this phone's and everything still works. |
   | signing in | the account's rows are written on to this phone, and any synced key the account does not have is REMOVED. What was here first is not merged and not uploaded. |
   | signed in | the phone is a mirror. A tick here goes up; a tick on the laptop comes down. |
   | signing out | the mirror comes off, so the next person at this phone does not inherit somebody's reading. |

   The second is the one that surprises people, and it is the one
   that has to be said before the button is pressed rather than
   after. A phone is not a copy of an account: it may have been
   handed to somebody for five minutes, and nothing here can tell.
   ============================================================ */

@Composable
fun AccountScreen(
    reader: Reader?,
    /** What went wrong last time, if anything. Silently doing
        nothing is the one response to a failed sign-in that
        leaves somebody pressing the same button again. */
    problem: String?,
    linkSent: Boolean,
    bottomPadding: Dp,
    onGoogle: () -> Unit,
    onLink: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val c = LocalReiad.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = Gap.s11, bottom = bottomPadding),
    ) {
        item("head") {
            Text("আপনার", style = BanglaHeading.copy(
                fontSize = MaterialTheme.typography.displaySmall.fontSize,
            ), color = c.ink)
            Text("Your account", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
            Spacer(Modifier.height(Gap.s9))
        }

        if (reader != null) {
            item("who") {
                Pane {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (reader.picture.isNotBlank()) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(Corner.pill)),
                            ) {
                                Photo(reader.picture, reader.name)
                            }
                            Spacer(Modifier.width(Gap.s6))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                reader.name.ifBlank { reader.email },
                                style = MaterialTheme.typography.titleMedium,
                                color = c.ink,
                            )
                            if (reader.name.isNotBlank() && reader.email.isNotBlank()) {
                                Text(
                                    reader.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.inkSoft,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Gap.s7))
                InfoCard(
                    title = "This phone is a mirror",
                    dek = "A tick here goes up; a tick on your laptop comes down. " +
                        "What you type into a practice book stays on this phone and " +
                        "is never sent anywhere.",
                )
                Spacer(Modifier.height(Gap.s7))
                Control(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onSignOut),
                    ground = c.panel,
                ) {
                    Text(
                        "Sign out",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.accent,
                    )
                }
                Spacer(Modifier.height(Gap.s4))
                Text(
                    "Signing out takes the mirror off this phone, so the next person " +
                        "using it does not inherit your reading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                )
            }
            return@LazyColumn
        }

        item("in") {
            problem?.let {
                InfoCard(title = "That sign-in did not go through", dek = it)
                Spacer(Modifier.height(Gap.s7))
            }

            /* Said BEFORE the button, not after. This is the one
               thing about signing in that surprises people, and a
               reader with forty lessons ticked on this phone
               deserves to know what happens to them. */
            InfoCard(
                title = "What signing in does",
                dek = "Your account's reading is written on to this phone, and anything " +
                    "ticked here that the account does not have is removed. Nothing on " +
                    "this phone is uploaded. If you have been reading here without an " +
                    "account, tick what matters on the site first.",
            )
            Spacer(Modifier.height(Gap.s8))

            Control(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onGoogle),
                ground = c.accent,
            ) {
                Text(
                    "Continue with Google",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.paper,
                )
            }
            Spacer(Modifier.height(Gap.s8))

            Text("Or by email", style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
            Spacer(Modifier.height(Gap.s4))
            EmailBox(linkSent, onLink)

            Spacer(Modifier.height(Gap.s8))
            Text(
                "Signed out, nothing here talks to a server about you. Your reading is " +
                    "this phone's and every page still works.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
        }
    }
}

@Composable
private fun EmailBox(sent: Boolean, onLink: (String) -> Unit) {
    val c = LocalReiad.current
    var email by remember { mutableStateOf("") }

    if (sent) {
        Plate(Modifier.fillMaxWidth()) {
            Text(
                "Check your email. The link opens straight back here.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink,
            )
        }
        return
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Gap.s5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(Gap.tap)
                /* A GROOVE, for the reason the site's `NOT_GLASS`
                   note gives: a text field's affordance is the
                   caret and the focus ring, and a lit resting rim
                   on a box you type into is a box that looks like
                   a button. */
                .clip(RoundedCornerShape(Corner.pill))
                .material(Kind.GROOVE, c, Corner.pill, ground = c.paperSunk)
                .padding(horizontal = Gap.s7),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (email.isEmpty()) {
                Text(
                    "you@example.com",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkSoft,
                )
            }
            BasicTextField(
                value = email,
                onValueChange = { email = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Send,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Control(
            modifier = Modifier.clickable(
                role = Role.Button,
                enabled = '@' in email,
            ) { onLink(email.trim()) },
            ground = if ('@' in email) c.accent else c.paperSunk,
        ) {
            Text(
                "Send",
                style = MaterialTheme.typography.labelLarge,
                color = if ('@' in email) c.paper else c.inkSoft,
            )
        }
    }
}
