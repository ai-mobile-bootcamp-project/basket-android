package com.basket.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.basket.R
import com.basket.ui.common.BasketIcons

/**
 * Screen 0 — Welcome. Shown once, on first launch, before Lists.
 * Persist `onboardingDone = true` in DataStore when either button is tapped and pop this
 * destination off the back stack (`popUpTo(Welcome) { inclusive = true }`), so Back from Lists
 * leaves the app. Reset sample data must NOT clear the flag.
 *
 * Icons: Material Symbols Rounded — checklist, payments, cloud_off (none are directional; nothing to mirror).
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,          // seeds sample lists, then opens Lists
    onStartEmpty: () -> Unit,          // opens Lists with no lists
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            // Scrollable body so 200% text never pushes the buttons off-screen.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.weight(1.1f))
                AppMark()
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.welcome_title),        // "Welcome to Basket"
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.welcome_tagline),      // "Grocery lists that add up as you shop."
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(36.dp))
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    WelcomePoint(BasketIcons.Checklist, R.string.welcome_point1_title, R.string.welcome_point1_body)
                    WelcomePoint(BasketIcons.Payments, R.string.welcome_point2_title, R.string.welcome_point2_body)
                    WelcomePoint(BasketIcons.CloudOff, R.string.welcome_point3_title, R.string.welcome_point3_body)
                }
                Spacer(Modifier.weight(1f))
            }
            Button(
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.welcome_get_started)) }        // "Get started"
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onStartEmpty,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.welcome_start_empty)) }        // "Start without sample lists"
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AppMark() {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = BasketIcons.BasketFilled,
            contentDescription = null,                                    // decorative; the title carries the name
            tint = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp),
        )
    }
}

@Composable
private fun WelcomePoint(icon: ImageVector, title: Int, body: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
