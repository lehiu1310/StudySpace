package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.StudyViewModel

@Composable
fun BiometricLockScreen(study: StudyViewModel, onLogout: () -> Unit) {
    val activity = LocalContext.current as? MainActivity
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock, null, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.product_unlock_title), style = MaterialTheme.typography.headlineSmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { activity?.authenticateWithBiometrics(
            onSuccess = { study.unlockAppWithBiometrics() }, onError = { error = it }) }) {
            Text(stringResource(R.string.product_unlock))
        }
        TextButton(onClick = onLogout) { Text(stringResource(R.string.product_signout)) }
    }
}
