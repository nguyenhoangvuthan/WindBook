package dev.kent.windbook

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import dev.kent.windbook.input.KeyEventDispatcher
import dev.kent.windbook.input.LocalKeyEventDispatcher
import dev.kent.windbook.navigation.WindBookNavHost
import dev.kent.windbook.ui.theme.WindBookTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var keyEventDispatcher: KeyEventDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WindBookTheme {
                CompositionLocalProvider(LocalKeyEventDispatcher provides keyEventDispatcher) {
                    WindBookNavHost()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        keyEventDispatcher.dispatch(event) || super.dispatchKeyEvent(event)
}
