package com.dfc.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dfc.mobile.DfcApi
import com.dfc.mobile.Prefs
import com.dfc.mobile.backup.BackupWorker
import com.dfc.mobile.ui.components.PrimaryButton
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Connection setup as a Compose screen. Credentials are verified against the
 * server before they are saved, so a typo cannot leave the app in a configured
 * state that never works.
 */
@Composable
fun SetupScreen(
    onConnected: () -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val existing = remember { Prefs.get(context) }
    var url by remember { mutableStateOf(existing.serverUrl) }
    var token by remember { mutableStateOf(existing.token) }
    var wifiOnly by remember { mutableStateOf(existing.wifiOnly) }
    var showToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val urlLooksValid = url.trim().startsWith("http://") || url.trim().startsWith("https://")
    val canSubmit = urlLooksValid && token.isNotBlank() && !busy

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
    ) {
        Text(
            text = "Connect this phone",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "Point the app at your drive and paste a write token. Photos and " +
                "videos then back up on their own every 15 minutes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.xl))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            singleLine = true,
            label = { Text("Server address") },
            placeholder = { Text("https://drive.example.com") },
            isError = url.isNotBlank() && !urlLooksValid,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            shape = RoundedCornerShape(Radii.control),
            modifier = Modifier.fillMaxWidth(),
        )
        if (url.isNotBlank() && !urlLooksValid) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "The address has to start with http:// or https://",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(Spacing.md))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            singleLine = true,
            label = { Text("Write token") },
            visualTransformation = if (showToken) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = RoundedCornerShape(Radii.control),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(
                        text = if (showToken) "Hide" else "Show",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Wi-Fi only",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Keep uploads off mobile data",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
        }

        Spacer(Modifier.height(Spacing.lg))

        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                text = if (busy) "Checking" else "Connect",
                onClick = {
                    if (!canSubmit) return@PrimaryButton
                    busy = true
                    error = null
                    scope.launch {
                        val candidate = Prefs.probe(url.trim(), token.trim())
                        val result = withContext(Dispatchers.IO) {
                            runCatching { DfcApi(candidate).verify() }
                        }
                        busy = false
                        val ok = result.getOrNull()?.first == true
                        if (ok) {
                            // Only now are the credentials persisted: a failed
                            // probe must not leave the app half-configured.
                            existing.serverUrl = url.trim()
                            existing.token = token.trim()
                            existing.wifiOnly = wifiOnly
                            DfcApi.get(context).invalidateCache()
                            BackupWorker.schedule(context, wifiOnly)
                            BackupWorker.runNow(context)
                            onConnected()
                        } else {
                            error = result.getOrNull()?.second
                                ?: result.exceptionOrNull()?.message
                                ?: "Could not reach that server"
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
            if (onCancel != null) {
                Spacer(Modifier.size(Spacing.sm))
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }

        error?.let { message ->
            Spacer(Modifier.height(Spacing.md))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
