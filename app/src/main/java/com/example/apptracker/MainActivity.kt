package com.example.apptracker



import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.apptracker.ui.SearchViewModel
import com.example.apptracker.ui.screens.SearchScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 🔥 status bar transparent (ASTA scoate bara neagră)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            MaterialTheme {
                Surface {
                    val vm: SearchViewModel = hiltViewModel()
                    SearchScreen(vm)
                }
            }
        }
    }
}
