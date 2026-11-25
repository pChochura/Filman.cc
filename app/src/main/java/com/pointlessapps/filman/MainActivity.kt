package com.pointlessapps.filman

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.tv.material3.Surface
import com.pointlessapps.filman.data.api.StreamApiLinkSource
import com.pointlessapps.filman.ui.theme.TiwiTheme
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModel()
    private val streamApiLinkSource: StreamApiLinkSource by inject()

    private val _lastInteractionTime = MutableStateFlow(System.currentTimeMillis())
    val lastInteractionTime = _lastInteractionTime.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        streamApiLinkSource.wakeUp()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel.initBackStack(Route.Home)
        viewModel.handleIntent(intent)

        setContent {
            val appLanguage by viewModel.appLanguage.collectAsState()
            val effectiveLanguage = remember(appLanguage) {
                viewModel.getEffectiveLanguage(appLanguage)
            }
            val effectiveLocale = remember(effectiveLanguage) {
                Locale.forLanguageTag(if (effectiveLanguage == "pl") "pl-PL" else "en-US")
            }

            LaunchedEffect(effectiveLocale) {
                Locale.setDefault(effectiveLocale)
            }

            LaunchedEffect(Unit) {
                viewModel.remotePlayEvent.collect { title ->
                    val msg = getString(com.pointlessapps.filman.core.ui.R.string.remote_playback_toast, title)
                    android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }

            val context = LocalContext.current
            val currentConfig = LocalConfiguration.current
            val localizedConfiguration = remember(effectiveLocale, currentConfig) {
                Configuration(currentConfig).apply {
                    setLocale(effectiveLocale)
                    setLayoutDirection(effectiveLocale)
                }
            }
            val localizedContext = remember(effectiveLocale, context) {
                val config = Configuration(context.resources.configuration).apply {
                    setLocale(effectiveLocale)
                    setLayoutDirection(effectiveLocale)
                }
                context.createConfigurationContext(config)
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration,
                LocalContext provides localizedContext,
            ) {
                TiwiTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        TiwiApp(viewModel = viewModel, activity = this@MainActivity)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIntent(intent)
    }

    fun resetInteractionTime() {
        _lastInteractionTime.value = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        resetInteractionTime()
        viewModel.onResume()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        _lastInteractionTime.value = System.currentTimeMillis()
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        _lastInteractionTime.value = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        _lastInteractionTime.value = System.currentTimeMillis()
        return super.dispatchGenericMotionEvent(ev)
    }
}
