package tn.careerpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import tn.careerpilot.ui.CareerPilotRoot
import tn.careerpilot.ui.theme.CareerPilotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CareerPilotTheme {
                CareerPilotRoot()
            }
        }
    }
}
