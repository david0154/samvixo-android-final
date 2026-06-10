package com.nexuzy.samvixo.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

data class WallpaperPreset(val id: String, val label: String, val colors: List<Color>)

val WALLPAPER_PRESETS = listOf(
    WallpaperPreset("default",  "Default",   listOf(Color(0xFFF5F5F5), Color(0xFFE0E0E0))),
    WallpaperPreset("ocean",    "Ocean",     listOf(Color(0xFF1565C0), Color(0xFF42A5F5))),
    WallpaperPreset("sunset",   "Sunset",    listOf(Color(0xFFFF6F00), Color(0xFFEF5350))),
    WallpaperPreset("forest",   "Forest",    listOf(Color(0xFF1B5E20), Color(0xFF66BB6A))),
    WallpaperPreset("lavender", "Lavender",  listOf(Color(0xFF7B1FA2), Color(0xFFCE93D8))),
    WallpaperPreset("midnight", "Midnight",  listOf(Color(0xFF0D0D0D), Color(0xFF212121))),
    WallpaperPreset("rose",     "Rose",      listOf(Color(0xFFE91E63), Color(0xFFF48FB1))),
    WallpaperPreset("sky",      "Sky",       listOf(Color(0xFF0277BD), Color(0xFFB3E5FC))),
    WallpaperPreset("gold",     "Gold",      listOf(Color(0xFFF9A825), Color(0xFFFFF9C4))),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPickerScreen(navController: NavController) {
    val context  = LocalContext.current
    val prefs    = remember { context.getSharedPreferences("samvixo_prefs", Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf(prefs.getString("wallpaper", "default") ?: "default") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            prefs.edit().putString("wallpaper", "custom").putString("wallpaper_uri", it.toString()).apply()
            selected = "custom"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Wallpaper") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text("Choose a wallpaper for all your chats.",
                modifier = Modifier.padding(16.dp), color = Color.Gray)

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(WALLPAPER_PRESETS) { preset ->
                    Box(
                        modifier = Modifier.aspectRatio(0.65f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.verticalGradient(preset.colors))
                            .then(
                                if (preset.id == selected)
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                else Modifier
                            )
                            .clickable {
                                selected = preset.id
                                prefs.edit().putString("wallpaper", preset.id).apply()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (preset.id == selected) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Text(preset.label, color = Color.White, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Custom image from gallery
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Image, null)
                Spacer(Modifier.width(8.dp))
                Text("Choose from Gallery")
            }
        }
    }
}
