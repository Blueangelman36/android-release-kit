package releasekit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { Home() }
            }
        }
    }
}

/** Replace this screen with your app. Keep VersionCard somewhere the user can find it. */
@Composable
private fun Home() {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text("Your app goes here.", style = MaterialTheme.typography.bodyLarge)
        VersionCard()
    }
}

@Composable
fun VersionCard() {
    var status by remember { mutableStateOf<UpdateStatus?>(null) }
    LaunchedEffect(Unit) { status = UpdateCheck.check() }
    val uri = LocalUriHandler.current
    val small = MaterialTheme.typography.bodySmall

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", style = small)
        when (val s = status) {
            null -> Text("Checking for a newer version…", style = small)
            UpdateStatus.UpToDate -> Text("This is the newest version.", style = small)
            is UpdateStatus.Unavailable -> Text(s.reason, style = small)
            is UpdateStatus.Available -> Button(onClick = { uri.openUri(s.release.apkUrl) }) {
                Text("Download ${s.release.versionName}")
            }
        }
    }
}
