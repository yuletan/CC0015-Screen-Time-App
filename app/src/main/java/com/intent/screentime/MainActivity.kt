package com.intent.screentime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intent.screentime.core.permission.UsageAccess
import com.intent.screentime.ui.nav.IntentNavHost
import com.intent.screentime.ui.onboarding.OnboardingScreen
import com.intent.screentime.ui.theme.IntentTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * A route handed in by a notification, waiting to be forwarded to the nav graph.
     *
     * Held in a flow rather than read once: a digest tapped while the app is already open
     * arrives through [onNewIntent], and the value has to survive recomposition to reach the
     * host. It is cleared once consumed so a later recomposition does not navigate again.
     */
    private val deepLinkRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        deepLinkRoute.value = intent?.getStringExtra(EXTRA_ROUTE)

        val container = (application as IntentApp).container

        setContent {
            val dynamicColor by container.preferences.dynamicColor
                .collectAsStateWithLifecycle(initialValue = false)

            IntentTheme(dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val scope = rememberCoroutineScope()

                    // Usage access lives in Settings, so re-check whenever we come back to
                    // the foreground rather than only on first composition.
                    var usageGranted by remember { mutableStateOf(UsageAccess.isGranted(context)) }
                    var notificationsGranted by remember { mutableStateOf(hasNotifications(context)) }

                    val onboardingComplete by container.preferences.onboardingComplete
                        .collectAsStateWithLifecycle(initialValue = false)

                    val route by deepLinkRoute.collectAsStateWithLifecycle()

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                usageGranted = UsageAccess.isGranted(context)
                                notificationsGranted = hasNotifications(context)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    val notificationLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { notificationsGranted = it },
                    )

                    if (usageGranted && onboardingComplete) {
                        IntentNavHost(
                            container = container,
                            dynamicColor = dynamicColor,
                            onDynamicColorChange = { enabled ->
                                scope.launch { container.preferences.setDynamicColor(enabled) }
                            },
                            deepLinkRoute = route,
                            onDeepLinkConsumed = { deepLinkRoute.value = null },
                        )
                    } else {
                        OnboardingScreen(
                            usageGranted = usageGranted,
                            notificationsGranted = notificationsGranted,
                            onOpenUsageSettings = { UsageAccess.openSettings(context) },
                            onRequestNotifications = {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            onContinue = {
                                scope.launch {
                                    container.preferences.setOnboardingComplete(true)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    /** A digest tapped while the app is open. `singleTop` keeps this from spawning a second. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { deepLinkRoute.value = it }
    }

    companion object {
        /**
         * The extra a notification's [android.app.PendingIntent] carries: a nav route for the
         * host to open. An explicit extra rather than a URI scheme, so no intent filter has
         * to be declared and no other app can deep-link in.
         */
        const val EXTRA_ROUTE = "route"
    }
}

private fun hasNotifications(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
