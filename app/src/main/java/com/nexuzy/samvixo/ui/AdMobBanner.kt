package com.nexuzy.samvixo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.nexuzy.samvixo.util.Config

/**
 * AdMobBanner — Composable AdMob banner ad (§19 PID)
 *
 * ALLOWED sections:
 *   - Channels feed (ChannelsScreen)
 *   - AI Chat section (AIScreen)
 *   - Status / Stories viewer (StatusScreen)
 *   - AI section / summaries
 *   - Discover section
 *
 * NEVER shown in:
 *   - Personal (1:1) chats (ChatDetailScreen)
 *   - Group chats
 *   - Secret chats (SecretChatScreen)
 *   - Voice / Video calls (CallActivity)
 *   - Status creation screen (StatusCreateScreen)
 *   - Profile screens (ProfileScreen)
 *   - Settings (SettingsScreen)
 *
 * Usage:
 *   AdMobBanner()  // add at bottom of AIScreen, ChannelsScreen, StatusScreen
 */
@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = Config.ADMOB_BANNER_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
