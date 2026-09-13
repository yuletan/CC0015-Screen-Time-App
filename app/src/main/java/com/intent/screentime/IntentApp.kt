package com.intent.screentime

import android.app.Application
import android.provider.Settings
import com.intent.screentime.core.di.AppContainer
import com.intent.screentime.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IntentApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Only used to read preferences once at startup; nothing long-running lives here. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannels()

        appScope.launch {
            // Safe to call on every start: the harvest and rollup use KEEP and never
            // duplicate, while the digest uses UPDATE so a changed time takes effect.
            WorkScheduler.scheduleAll(this@IntentApp, container.preferences)

            // The intent prompt survives a restart only if the user left it on *and*
            // still holds the overlay permission — losing either should leave the app
            // as quiet as it was before the feature existed.
            val promptEnabled = container.preferences.intentPromptEnabled.first()
            if (promptEnabled && Settings.canDrawOverlays(this@IntentApp)) {
                container.startIntentWatch()
            }
        }
    }
}
