package com.intent.screentime.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    usageGranted: Boolean,
    notificationsGranted: Boolean,
    onOpenUsageSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text = "CC0015 Intent",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Not just how long you were on your phone — what you were actually doing with it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        FeatureRow(
            icon = Icons.Filled.Star,
            title = "Production vs consumption",
            body = "Every app is sorted into producing, consuming or neutral, so you can see the split rather than just a total.",
        )
        Spacer(Modifier.height(20.dp))
        FeatureRow(
            icon = Icons.Filled.CheckCircle,
            title = "Targets and streaks",
            body = "Set caps you must stay under and goals you want to hit. Keep both going and the streak builds.",
        )
        Spacer(Modifier.height(20.dp))
        FeatureRow(
            icon = Icons.Filled.Lock,
            title = "Entirely offline",
            body = "Nothing ever leaves your phone. There is no account, no sync and no network access at all.",
        )

        Spacer(Modifier.height(36.dp))

        PermissionCard(
            title = "Usage access",
            body = if (usageGranted) {
                "Granted. Intent can now read your app usage."
            } else {
                "Required. Android keeps usage data behind a special permission that only you can switch on."
            },
            granted = usageGranted,
            actionLabel = if (usageGranted) "Manage" else "Open settings",
            onAction = onOpenUsageSettings,
        )

        Spacer(Modifier.height(12.dp))

        PermissionCard(
            title = "Notifications",
            body = if (notificationsGranted) {
                "Enabled. You'll get the daily digest of your most-used app."
            } else {
                "Optional, but this is how the daily digest of your top app reaches you."
            },
            granted = notificationsGranted,
            actionLabel = if (notificationsGranted) "Enabled" else "Enable",
            onAction = onRequestNotifications,
            actionEnabled = !notificationsGranted,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            enabled = usageGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (usageGranted) "Start tracking" else "Waiting for usage access")
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Your data stays on this device. Intent has no internet permission.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (granted) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onAction, enabled = actionEnabled) {
                Icon(
                    imageVector = if (title == "Notifications") {
                        Icons.Filled.Notifications
                    } else {
                        Icons.Filled.Lock
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(actionLabel)
            }
        }
    }
}
