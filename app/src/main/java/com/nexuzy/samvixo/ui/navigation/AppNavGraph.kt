package com.nexuzy.samvixo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.nexuzy.samvixo.ui.*

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {

    val startDest = if (FirebaseAuth.getInstance().currentUser != null) "chats" else "auth"

    NavHost(navController = navController, startDestination = startDest) {

        // ── Auth ──────────────────────────────────────────────────────────────
        composable("auth") {
            AuthScreen(
                onAuthComplete = {
                    navController.navigate("chats") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.OnboardingName.route) {
            OnboardingNameScreen(navController = navController)
        }

        // ── Main ───────────────────────────────────────────────────────────────
        composable("chats") {
            ChatsScreen(
                onOpenChat    = { chatId, type ->
                    when (type) {
                        "secret"  -> navController.navigate("secret_chat/$chatId/Chat")
                        "group"   -> navController.navigate("group_chat/$chatId/Chat")
                        "channel" -> navController.navigate("channel_detail/$chatId")
                        else      -> navController.navigate("chat_detail/$chatId/Chat")
                    }
                },
                onNewGroup     = { navController.navigate("group_creation") },
                onNewBroadcast = { navController.navigate("broadcast_creation") },
                onNewChannel   = { navController.navigate("create_channel") },
                onSearchUser   = { navController.navigate("user_search") }
            )
        }

        // ── Status ────────────────────────────────────────────────────────────
        composable("status") {
            StatusScreen(
                onCreateStatus = { navController.navigate("status_create") },
                onViewStatus   = { uid -> navController.navigate("user_profile/$uid") }
            )
        }

        composable("status_create") {
            StatusCreateScreen(navController = navController)
        }

        // ── Channels ──────────────────────────────────────────────────────────
        composable("channels") {
            ChannelsScreen(navController = navController)
        }

        composable(
            route     = "channel_detail/{channelId}",
            arguments = listOf(navArgument("channelId") { type = NavType.StringType })
        ) { back ->
            val channelId = back.arguments?.getString("channelId") ?: return@composable
            ChannelDetailScreen(channelId = channelId, navController = navController)
        }

        composable("create_channel") {
            CreateChannelScreen(navController = navController)
        }

        composable("channel_creation") {
            ChannelCreationScreen(
                onCreated = { channelId ->
                    navController.navigate("channel_detail/$channelId") {
                        popUpTo("channel_creation") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Chat Detail ───────────────────────────────────────────────────────
        composable(
            route = "chat_detail/{chatId}/{peerName}",
            arguments = listOf(
                navArgument("chatId")   { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType; defaultValue = "Chat" }
            )
        ) { back ->
            ChatDetailScreen(
                chatId        = back.arguments?.getString("chatId") ?: return@composable,
                peerName      = back.arguments?.getString("peerName") ?: "Chat",
                navController = navController
            )
        }

        composable(
            route     = "chat_detail/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { back ->
            ChatDetailScreen(
                chatId        = back.arguments?.getString("chatId") ?: return@composable,
                peerName      = "",
                navController = navController
            )
        }

        // ── Secret Chat ───────────────────────────────────────────────────────
        composable(
            route = "secret_chat/{chatId}/{peerName}",
            arguments = listOf(
                navArgument("chatId")   { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType; defaultValue = "Chat" }
            )
        ) { back ->
            SecretChatScreen(
                chatId        = back.arguments?.getString("chatId") ?: return@composable,
                peerName      = back.arguments?.getString("peerName") ?: "Chat",
                navController = navController
            )
        }

        // ── Group Chat ────────────────────────────────────────────────────────
        composable(
            route = "group_chat/{chatId}/{peerName}",
            arguments = listOf(
                navArgument("chatId")   { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType; defaultValue = "Group" }
            )
        ) { back ->
            ChatDetailScreen(
                chatId        = back.arguments?.getString("chatId") ?: return@composable,
                peerName      = back.arguments?.getString("peerName") ?: "Group",
                navController = navController
            )
        }

        composable("group_creation") {
            GroupCreationScreen(navController = navController)
        }

        composable("broadcast_creation") {
            BroadcastCreationScreen(
                onCreated = { id -> navController.navigate("chat_detail/$id/Broadcast") },
                onBack    = { navController.popBackStack() }
            )
        }

        // ── User Search & Profiles ────────────────────────────────────────────
        composable("user_search") {
            UserSearchScreen(navController = navController)
        }

        composable(
            route     = "user_profile/{uid}",
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { back ->
            UserProfileViewScreen(
                navController = navController,
                uid           = back.arguments?.getString("uid") ?: return@composable
            )
        }

        // ── Inbox ─────────────────────────────────────────────────────────────
        composable(Screen.Inbox.route) {
            InboxScreen(navController = navController)
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable("settings") {
            SettingsScreen(navController = navController)
        }

        // Account → AccountScreen (defined in SettingsSubScreens.kt)
        composable(Screen.Account.route) {
            AccountScreen(navController = navController)
        }

        // Avatar screen
        composable(Screen.Avatar.route) {
            AvatarScreen(navController = navController)
        }

        // Edit Profile & Profile → ProfileScreen (the file that exists)
        composable(Screen.EditProfile.route) {
            ProfileScreen(navController = navController)
        }
        composable(Screen.Profile.route) {
            ProfileScreen(navController = navController)
        }

        // Privacy / Profile Settings → ProfileSettingsScreen (standalone file)
        composable(Screen.ProfileSettings.route) {
            ProfileSettingsScreen(navController = navController)
        }

        composable(Screen.NotifSettings.route) {
            NotifSettingsScreen(navController = navController)
        }

        composable(Screen.StorageData.route) {
            StorageDataScreen(navController = navController)
        }

        composable(Screen.AppLanguage.route) {
            AppLanguageScreen(navController = navController)
        }

        // AppLocker → defined in SettingsSubScreens.kt
        composable(Screen.AppLocker.route) {
            AppLockerScreen(navController = navController)
        }

        composable(Screen.EmergencyMesh.route) {
            EmergencyMeshScreen(navController = navController)
        }

        composable(Screen.InviteFriend.route) {
            InviteFriendScreen(navController = navController)
        }

        // ── Linked Devices ──────────────────────────────────────────────────
        composable(Screen.LinkedDevices.route) {
            LinkedDevicesScreen(onBack = { navController.popBackStack() })
        }

        // ── Wallpaper Picker ──────────────────────────────────────────────────
        composable(Screen.WallpaperPicker.route) {
            WallpaperPickerScreen(navController = navController)
        }

        // ── Backup ────────────────────────────────────────────────────────────
        composable(Screen.Backup.route) {
            BackupScreen(navController = navController)
        }

        // ── AI Screens ────────────────────────────────────────────────────────
        composable(Screen.AI.route) {
            AIScreen(navController = navController)
        }

        composable(Screen.AINotes.route) {
            AINotesScreen(navController = navController)
        }

        composable(Screen.AIVault.route) {
            AIVaultScreen(navController = navController)
        }

        // ── Weather ───────────────────────────────────────────────────────────
        composable(Screen.Weather.route) {
            WeatherScreen(navController = navController)
        }

        // ── Help / Info Screens (from SamvixoInfoScreens.kt) ───────────────────
        composable(Screen.ContactUs.route) {
            ContactUsScreen(navController = navController)
        }

        composable(Screen.AboutUs.route) {
            AboutUsScreen(navController = navController)
        }

        composable(Screen.TermsConditions.route) {
            TermsConditionsScreen(navController = navController)
        }

        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(navController = navController)
        }
    }
}
