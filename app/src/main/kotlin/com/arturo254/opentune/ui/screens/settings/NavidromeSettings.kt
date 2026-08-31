/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.arturo254.opentune.LocalPlayerAwareWindowInsets
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.NavidromePasswordKey
import com.arturo254.opentune.constants.NavidromeServerUrlKey
import com.arturo254.opentune.constants.NavidromeUsernameKey
import com.arturo254.opentune.constants.NavidromeMaxBitRateKey
import com.arturo254.opentune.navidrome.Navidrome
import com.arturo254.opentune.ui.component.IconButton
import com.arturo254.opentune.ui.component.ListPreference
import com.arturo254.opentune.ui.component.PreferenceEntry
import com.arturo254.opentune.ui.component.PreferenceGroupTitle
import com.arturo254.opentune.ui.component.TextFieldDialog
import com.arturo254.opentune.ui.utils.backToMain
import com.arturo254.opentune.utils.reportException
import com.arturo254.opentune.utils.rememberPreference
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavidromeSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val coroutineScope = rememberCoroutineScope()

    var serverUrl by rememberPreference(NavidromeServerUrlKey, "")
    var username by rememberPreference(NavidromeUsernameKey, "")
    var password by rememberPreference(NavidromePasswordKey, "")

    val (maxBitRate, onMaxBitRateChange) = rememberPreference(NavidromeMaxBitRateKey, "0")

    val isConfigured = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    var showServerUrlEditor by rememberSaveable { mutableStateOf(false) }
    var showUsernameEditor by rememberSaveable { mutableStateOf(false) }
    var showPasswordEditor by rememberSaveable { mutableStateOf(false) }

    var isTestingConnection by rememberSaveable { mutableStateOf(false) }
    var connectionOk by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var connectionDetail by rememberSaveable { mutableStateOf<String?>(null) }
    var showNotConfigured by rememberSaveable { mutableStateOf(false) }

    fun resetConnectionState() {
        connectionOk = null
        connectionDetail = null
        showNotConfigured = false
    }

    fun testConnection() {
        if (!isConfigured) {
            showNotConfigured = true
            return
        }
        isTestingConnection = true
        connectionOk = null
        connectionDetail = null
        showNotConfigured = false

        coroutineScope.launch(Dispatchers.IO) {
            Navidrome.ping(serverUrl, username, password)
                .onSuccess { info ->
                    withContext(Dispatchers.Main) {
                        isTestingConnection = false
                        connectionOk = true
                        connectionDetail = info.displayName
                        Timber.d("Navidrome connection successful: ${info.displayName}")
                    }
                }
                .onFailure { exception ->
                    withContext(Dispatchers.Main) {
                        isTestingConnection = false
                        connectionOk = false
                        connectionDetail = exception.message
                        Timber.e(exception, "Navidrome connection test failed")
                        reportException(exception)
                    }
                }
        }
    }

    if (showServerUrlEditor) {
        TextFieldDialog(
            initialTextFieldValue = TextFieldValue(serverUrl),
            onDone = { value ->
                serverUrl = Navidrome.normalizeServerUrl(value)
                resetConnectionState()
                showServerUrlEditor = false
            },
            onDismiss = { showServerUrlEditor = false },
            singleLine = true,
            maxLines = 1,
            isInputValid = { it.isNotBlank() },
            title = { Text(stringResource(R.string.navidrome_server_url)) },
            placeholder = { Text(stringResource(R.string.navidrome_server_url_hint)) },
        )
    }

    if (showUsernameEditor) {
        TextFieldDialog(
            initialTextFieldValue = TextFieldValue(username),
            onDone = { value ->
                username = value.trim()
                resetConnectionState()
                showUsernameEditor = false
            },
            onDismiss = { showUsernameEditor = false },
            singleLine = true,
            maxLines = 1,
            isInputValid = { it.isNotBlank() },
            title = { Text(stringResource(R.string.username)) },
        )
    }

    if (showPasswordEditor) {
        var tempPassword by rememberSaveable { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPasswordEditor = false },
            title = { Text(stringResource(R.string.password)) },
            text = {
                OutlinedTextField(
                    value = tempPassword,
                    onValueChange = { tempPassword = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        password = tempPassword
                        resetConnectionState()
                        showPasswordEditor = false
                    },
                    enabled = tempPassword.isNotEmpty()
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordEditor = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.navidrome_server),
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.navidrome_server_url)) },
            description = serverUrl.ifBlank {
                stringResource(R.string.navidrome_server_url_hint)
            },
            icon = { Icon(painterResource(R.drawable.link), null) },
            onClick = { showServerUrlEditor = true },
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.username)) },
            description = username.ifBlank { null },
            icon = { Icon(painterResource(R.drawable.token), null) },
            onClick = { showUsernameEditor = true },
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.password)) },
            description = if (password.isBlank()) null else "••••••••",
            icon = { Icon(painterResource(R.drawable.security), null) },
            onClick = { showPasswordEditor = true },
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.connection),
        )

        ListPreference(
            title = { Text(stringResource(R.string.navidrome_data_saver)) },
            icon = { Icon(painterResource(R.drawable.wifi), null) },
            selectedValue = maxBitRate,
            values = listOf("0", "320", "192", "128", "96", "64"),
            valueText = { value ->
                if (value == "0") stringResource(R.string.navidrome_bitrate_unlimited)
                else "$value kbps"
            },
            onValueSelected = onMaxBitRateChange,
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.test_connection)) },
            icon = { Icon(painterResource(R.drawable.sync), null) },
            onClick = { testConnection() },
            isEnabled = !isTestingConnection,
            trailingContent = {
                if (isTestingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            },
            subtitle = {
                val statusText = when {
                    showNotConfigured -> stringResource(R.string.navidrome_not_configured)
                    isTestingConnection -> stringResource(R.string.testing_connection)
                    connectionOk == true -> stringResource(R.string.connection_successful) +
                        (connectionDetail?.let { " · $it" } ?: "")
                    connectionOk == false -> stringResource(R.string.connection_failed) +
                        (connectionDetail?.let { "\n$it" } ?: "")
                    else -> null
                }

                if (statusText != null) {
                    Text(
                        text = statusText,
                        color = when {
                            connectionOk == true -> MaterialTheme.colorScheme.primary
                            connectionOk == false || showNotConfigured -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            },
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.navidrome_server)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}
