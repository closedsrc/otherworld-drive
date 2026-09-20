package com.dfc.mobile.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dfc.mobile.DfcApi
import com.dfc.mobile.Prefs
import com.dfc.mobile.Server
import com.dfc.mobile.backup.BackupWorker
import com.dfc.mobile.ui.components.PrimaryButton
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.dfc.mobile.ui.theme.currentType

/** How long Connect waits before reporting an unreachable drive. */
private const val SETUP_TIMEOUT_MS = 15_000L

/**
 * Connect this phone to a drive.
 *
 * The primary path registers this install as a device: the drive password is
 * exchanged once for a token that authorizes only this phone, and the server
 * keeps just its hash. The token can be revoked from the drive's device list
 * without touching any other phone — there is no shared secret in the app.
 * The token field below exists for the headless case (a token minted with
 * create-token on the server); it is the advanced path, not the default.
 */
@Composable
fun SetupScreen(
    onConnected: () -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val existing = remember { Prefs.get(context) }
    var serverUrl by rememberSaveable { mutableStateOf(existing.serverUrl.ifEmpty { Server.DEFAULT_BASE_URL }) }
    var password by rememberSaveable { mutableStateOf("") }
    var tokenMode by rememberSaveable { mutableStateOf(false) }
    var token by rememberSaveable { mutableStateOf("") }
    var wifiOnly by rememberSaveable { mutableStateOf(existing.wifiOnly) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = if (tokenMode) {
        token.isNotBlank() && !busy
    } else {
        password.isNotBlank() && serverUrl.isNotBlank() && !busy
    }

    fun finishWithToken(verified: Boolean, message: String?) {
        busy = false
        if (verified) {
            existing.serverUrl = serverUrl.trim()
            existing.wifiOnly = wifiOnly
            DfcApi.get(context).invalidateCache()
            BackupWorker.schedule(context, wifiOnly)
            BackupWorker.runNow(context)
            onConnected()
        } else {
            android.util.Log.d("SetupScreen", "finishWithToken FALSE msg=$message")
            error = message ?: "Could not reach the drive"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        if (onCancel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.dfc.mobile.ui.BarIcon(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onCancel,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "Connect this phone",
            style = currentType.display,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "Sign in once with your drive password. This phone gets its own " +
                "access key — photos and videos then back up on their own, and the " +
                "key can be revoked any time without touching your other devices.",
            style = currentType.bodyMuted,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.md))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            singleLine = true,
            label = { Text("Drive address") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = RoundedCornerShape(Radii.control),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.md))

        if (tokenMode) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                singleLine = true,
                label = { Text("Device token") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(Radii.control),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                label = { Text("Drive password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Password,
                ),
                shape = RoundedCornerShape(Radii.control),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TextButton(onClick = { tokenMode = !tokenMode; error = null }) {
            Text(
                text = if (tokenMode) "Sign in with the password instead"
                else "Paste a device token instead",
                style = currentType.meta,
            )
        }

        Spacer(Modifier.height(Spacing.xs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Wi-Fi only",
                    style = currentType.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Keep uploads off mobile data",
                    style = currentType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
        }

        Spacer(Modifier.height(Spacing.lg))

        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                text = if (busy) "Connecting" else "Connect",
                onClick = {
                    if (!canSubmit) return@PrimaryButton
                    busy = true
                    error = null
                    scope.launch {
                        if (tokenMode) {
                            // Verify a hand-minted token before it is saved, so a
                            // typo cannot leave the app half-configured.
                            val candidate = Prefs.probe(token.trim())
                            val (ok, msg) = withContext(Dispatchers.IO) {
                                runCatching { DfcApi(candidate).verify() }
                            }.getOrElse { false to (it.message ?: "connection failed") }
                            if (ok) existing.token = token.trim()
                            finishWithToken(ok, msg)
                        } else {
                            val deviceName = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android phone"
                            // Bounded. An unreachable address used to leave the
                            // button reading "Connect" — no spinner, no error —
                            // for a full minute while the socket timed out,
                            // which reads as a frozen app. Fifteen seconds is
                            // long enough for a handshake on a slow link and
                            // short enough that a typo is obviously a typo.
                            val result = withTimeoutOrNull(SETUP_TIMEOUT_MS) {
                                withContext(Dispatchers.IO) {
                                    DfcApi.registerDevice(serverUrl.trim(), password, deviceName)
                                }
                            }
                            if (result == null) {
                                finishWithToken(
                                    false,
                                    "Could not reach that drive. Check the address and try again.",
                                )
                                return@launch
                            }
                            val (newToken, _, regError) = result
                            if (newToken == null) {
                                finishWithToken(false, regError)
                            } else {
                                // Verify before persisting: registration succeeded
                                // server-side, but the address must also work for
                                // real traffic from this phone.
                                existing.token = newToken
                                existing.serverUrl = serverUrl.trim()
                                val (ok, msg) = withContext(Dispatchers.IO) {
                                    runCatching { DfcApi.get(context).verify() }
                                }.getOrElse { false to (it.message ?: "connection failed") }
                                if (!ok) existing.token = ""
                                finishWithToken(ok, msg)
                            }
                        }
                    }
                },
                enabled = canSubmit,
            )
            if (busy) {
                Spacer(Modifier.size(Spacing.md))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // The error sits directly under the button rather than at the end of the
        // scrollable column: at the bottom it was below the fold on a phone with
        // the keyboard up, so a failed connection looked like nothing happened.
        error?.let { message ->
            Spacer(Modifier.height(Spacing.md))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.control))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(Spacing.md),
            ) {
                Text(
                    text = message,
                    style = currentType.bodyMuted,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
