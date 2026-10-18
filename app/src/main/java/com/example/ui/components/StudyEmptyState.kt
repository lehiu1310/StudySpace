package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StudySpaceTokens

@Composable
fun StudyEmptyState(
    title: String,
    body: String,
    icon: ImageVector = Icons.Default.AutoAwesome,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    compact: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = StudySpaceTokens.controlShape
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (compact) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(if (compact) 44.dp else 56.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(if (compact) 11.dp else 14.dp)
                )
            }
            Text(
                text = title,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(2.dp))
                if (compact && secondaryActionLabel != null && onSecondaryAction != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onAction,
                            modifier = Modifier.weight(1f).height(StudySpaceTokens.buttonHeight),
                            shape = StudySpaceTokens.buttonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text(actionLabel, textAlign = TextAlign.Center) }
                        OutlinedButton(
                            onClick = onSecondaryAction,
                            modifier = Modifier.weight(1f).height(StudySpaceTokens.buttonHeight),
                            shape = StudySpaceTokens.buttonShape
                        ) { Text(secondaryActionLabel, textAlign = TextAlign.Center) }
                    }
                } else {
                    Button(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth().height(StudySpaceTokens.buttonHeight),
                        shape = StudySpaceTokens.buttonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text(actionLabel) }
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        OutlinedButton(
                            onClick = onSecondaryAction,
                            modifier = Modifier.fillMaxWidth().height(StudySpaceTokens.buttonHeight),
                            shape = StudySpaceTokens.buttonShape
                        ) { Text(secondaryActionLabel) }
                    }
                }
            }
        }
    }
}
