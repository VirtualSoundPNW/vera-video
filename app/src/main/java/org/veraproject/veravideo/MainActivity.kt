package org.veraproject.veravideo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import org.veraproject.veravideo.ui.VeraVideoAppUi
import org.veraproject.veravideo.ui.theme.VeraVideoTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VeraVideoTheme {
                VeraVideoAppUi()
            }
        }
    }
}
