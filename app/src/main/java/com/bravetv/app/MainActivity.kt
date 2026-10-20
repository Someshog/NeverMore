package com.bravetv.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BraveEngine.initializeBraveShields(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = Color(0xFF10131A))) {
                    HomeScreen { label, url ->
                        startActivity(Intent(this@MainActivity, TvWebViewActivity::class.java).apply {
                            putExtra(TvWebViewActivity.EXTRA_TITLE, label)
                            putExtra(TvWebViewActivity.EXTRA_URL, url)
                        })
                    }
                }
            }
        }
    }
}

private data class Service(val name: String, val url: String, val color: Color)

@Composable
private fun HomeScreen(onOpen: (String, String) -> Unit) {
    val services = listOf(
        Service("YouTube TV", "https://www.youtube.com/tv", Color(0xFF20232B)),
        // Service("JioHotstar", "https://www.hotstar.com/in", Color(0xFF151947)),
        // Service("Twitch", "https://www.twitch.tv", Color(0xFF6441A5))
    )
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF10131A)).padding(horizontal = 72.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NeverMore", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Your streaming apps, ready for the big screen", fontSize = 19.sp, color = Color(0xFFADB5C5))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            services.forEach { service ->
                Card(onClick = { onOpen(service.name, service.url) }, modifier = Modifier.width(210.dp).height(132.dp)) {
                    Column(
                        Modifier.fillMaxSize().background(service.color).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(painter = painterResource(R.drawable.youtube_logo), contentDescription = "YouTube", modifier = Modifier.width(90.dp).height(64.dp))
                    }
                }
            }
        }
        Text("Use your remote to choose a service", fontSize = 16.sp, color = Color(0xFF8791A3))
    }
}


