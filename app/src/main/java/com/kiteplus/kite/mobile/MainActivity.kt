package com.kiteplus.kite.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kiteplus.kite.mobile.ui.KiteRoot
import com.kiteplus.kite.mobile.ui.KiteTheme

/**
 * One activity, one Compose tree: the three screens are values inside it, not separate activities.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as KiteApp).container
        setContent {
            KiteTheme {
                KiteRoot(container)
            }
        }
    }
}
