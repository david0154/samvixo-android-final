package com.nexuzy.samvixo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.nexuzy.samvixo.R
import com.nexuzy.samvixo.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

private enum class AppState { SPLASH, AUTH, HOME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController? = null) {
    val innerNav = rememberNavController()
    val navBackStackEntry by innerNav.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var appState by remember { mutableStateOf(AppState.SPLASH) }

    LaunchedEffect(Unit) {
        delay(1500)
        val user = FirebaseAuth.getInstance().currentUser
        appState = if (user == null) {
            AppState.AUTH
        } else {
            val snap = try {
                Firebase.firestore.collection("users").document(user.uid).get().await()
            } catch (_: Exception) { null }
            if (snap != null && snap.exists() && snap.getString("displayName") != null)
                AppState.HOME
            else
                AppState.AUTH
        }
    }

    when (appState) {
        AppState.SPLASH -> { SplashScreen(); return }
        AppState.AUTH   -> { AuthScreen(onAuthComplete = { appState = AppState.HOME }); return }
        AppState.HOME   -> { /* fall through */ }
    }

    val bottomTabs = listOf(
        Screen.Chats.route, Screen.Status.route, Screen.Channels.route,
        Screen.AI.route, Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomTabs) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(72.dp)
                ) {
                    NavigationBarItem(
                        icon     = { Icon(Icons.AutoMirrored.Filled.Chat, "Chats", modifier = Modifier.size(26.dp)) },
                        label    = { Text("Chats", fontSize = 12.sp) },
                        selected = currentRoute == Screen.Chats.route,
                        onClick  = { navigateToTab(innerNav, Screen.Chats.route) },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF7C3AED),
                            selectedTextColor = Color(0xFF7C3AED),
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon     = { Icon(Icons.Default.Adjust, "Status", modifier = Modifier.size(26.dp)) },
                        label    = { Text("Status", fontSize = 12.sp) },
                        selected = currentRoute == Screen.Status.route,
                        onClick  = { navigateToTab(innerNav, Screen.Status.route) },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF7C3AED),
                            selectedTextColor = Color(0xFF7C3AED),
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon     = { Icon(Icons.Default.Hub, "Channels", modifier = Modifier.size(26.dp)) },
                        label    = { Text("Explore", fontSize = 12.sp) },
                        selected = currentRoute == Screen.Channels.route,
                        onClick  = { navigateToTab(innerNav, Screen.Channels.route) },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF7C3AED),
                            selectedTextColor = Color(0xFF7C3AED),
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon     = { Icon(Icons.Default.AutoAwesome, "AI", modifier = Modifier.size(26.dp)) },
                        label    = { Text("Devil AI", fontSize = 12.sp) },
                        selected = currentRoute == Screen.AI.route,
                        onClick  = { navigateToTab(innerNav, Screen.AI.route) },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF7C3AED),
                            selectedTextColor = Color(0xFF7C3AED),
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon     = { Icon(Icons.Default.Settings, "Settings", modifier = Modifier.size(26.dp)) },
                        label    = { Text("Settings", fontSize = 12.sp) },
                        selected = currentRoute == Screen.Settings.route,
                        onClick  = { navigateToTab(innerNav, Screen.Settings.route) },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF7C3AED),
                            selectedTextColor = Color(0xFF7C3AED),
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = innerNav,
            startDestination = Screen.Chats.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Chats.route) {
                ChatsScreen(
                    onOpenChat     = { chatId, type ->
                        when (type) {
                            "secret" -> innerNav.navigate("secret_chat/$chatId/Chat")
                            "group"  -> innerNav.navigate("chat_detail/$chatId/Group")
                            "channel"-> innerNav.navigate("channel_detail/$chatId")
                            else     -> innerNav.navigate("chat_detail/$chatId/Chat")
                        }
                    },
                    onNewGroup     = { innerNav.navigate(Screen.GroupCreation.route) },
                    onNewBroadcast = { innerNav.navigate(Screen.BroadcastCreation.route) },
                    onNewChannel   = { innerNav.navigate(Screen.CreateChannel.route) },
                    onSearchUser   = { innerNav.navigate(Screen.UserSearch.route) }
                )
            }
            composable(Screen.Status.route) {
                StatusScreen(
                    onCreateStatus = { innerNav.navigate(Screen.StatusCreate.route) },
                    onViewStatus   = { uid -> innerNav.navigate("user_profile/$uid") }
                )
            }
            // FIX: pass innerNav to ChannelsScreen (required parameter)
            composable(Screen.Channels.route) { ChannelsScreen(navController = innerNav) }
            composable(Screen.AI.route)       { AIScreen() }
            composable(Screen.Settings.route) { SettingsScreen(navController = innerNav) }

            composable(
                route     = Screen.ChannelDetail.route,
                arguments = listOf(navArgument("channelId") { type = NavType.StringType })
            ) { back ->
                val channelId = back.arguments?.getString("channelId") ?: ""
                ChannelDetailScreen(channelId = channelId, navController = innerNav)
            }

            composable(
                route     = Screen.ChatDetail.route,
                arguments = listOf(
                    navArgument("chatId")   { type = NavType.StringType },
                    navArgument("peerName") { type = NavType.StringType }
                )
            ) { back ->
                ChatDetailScreen(
                    chatId        = back.arguments?.getString("chatId")   ?: "",
                    peerName      = back.arguments?.getString("peerName") ?: "",
                    navController = innerNav
                )
            }
            composable(
                route     = Screen.SecretChat.route,
                arguments = listOf(
                    navArgument("chatId")   { type = NavType.StringType },
                    navArgument("peerName") { type = NavType.StringType }
                )
            ) { back ->
                SecretChatScreen(
                    navController = innerNav,
                    chatId        = back.arguments?.getString("chatId")   ?: "",
                    peerName      = back.arguments?.getString("peerName") ?: ""
                )
            }

            composable(Screen.GroupCreation.route)     { GroupCreationScreen(innerNav) }
            composable(Screen.BroadcastCreation.route) {
                BroadcastCreationScreen(
                    onCreated = { innerNav.navigate("chat_detail/$it/Broadcast") { popUpTo(Screen.Chats.route) } },
                    onBack    = { innerNav.popBackStack() }
                )
            }
            composable(Screen.ChannelCreation.route) {
                ChannelCreationScreen(
                    onCreated = { innerNav.navigate("chat_detail/$it/Channel") { popUpTo(Screen.Chats.route) } },
                    onBack    = { innerNav.popBackStack() }
                )
            }
            composable(Screen.StatusCreate.route)    { StatusCreateScreen(innerNav) }
            composable(Screen.Profile.route)         { ProfileScreen(innerNav) }
            composable(Screen.EditProfile.route)     { EditProfileScreen(innerNav) }
            composable(Screen.ProfileSettings.route) { ProfileSettingsScreen(innerNav) }
            composable(
                route     = Screen.UserProfileView.route,
                arguments = listOf(navArgument("uid") { type = NavType.StringType })
            ) { back ->
                UserProfileViewScreen(
                    navController = innerNav,
                    uid           = back.arguments?.getString("uid") ?: ""
                )
            }
            composable(Screen.Account.route)         { AccountScreen(innerNav) }
            composable(Screen.Avatar.route)          { AvatarScreen(innerNav) }
            composable(Screen.NotifSettings.route)   { NotifSettingsScreen(innerNav) }
            composable(Screen.StorageData.route)     { StorageDataScreen(innerNav) }
            composable(Screen.AppLanguage.route)     { AppLanguageScreen(innerNav) }
            composable(Screen.EmergencyMesh.route)   { EmergencyMeshScreen(innerNav) }
            composable(Screen.InviteFriend.route)    { InviteFriendScreen(innerNav) }
            composable(Screen.WallpaperPicker.route) { WallpaperPickerScreen(innerNav) }
            composable(Screen.CreateChannel.route)   { CreateChannelScreen(innerNav) }
            composable(Screen.AboutUs.route)         { AboutUsScreen(innerNav) }
            composable(Screen.TermsConditions.route) { TermsConditionsScreen(innerNav) }
            composable(Screen.PrivacyPolicy.route)   { PrivacyPolicyScreen(innerNav) }
            composable(Screen.ContactUs.route)       { ContactUsScreen(innerNav) }
            composable(Screen.Weather.route)         { WeatherScreen(innerNav) }
            composable(Screen.Backup.route)          { BackupScreen(innerNav) }
            composable(Screen.Inbox.route)           { InboxScreen(innerNav) }
            composable(Screen.UserSearch.route)      { UserSearchScreen(innerNav) }
            composable(Screen.LinkedDevices.route)   { LinkedDevicesScreen(onBack = { innerNav.popBackStack() }) }
            // AI sub-screens
            composable(Screen.AINotes.route)         { AINotesScreen(innerNav) }
            composable(Screen.AIVault.route)         { AIVaultScreen(innerNav) }
            composable(Screen.AppLocker.route)       { AppLockerScreen(innerNav) }

            composable(
                route = "group_chat/{chatId}/{peerName}",
                arguments = listOf(
                    navArgument("chatId")   { type = NavType.StringType },
                    navArgument("peerName") { type = NavType.StringType; defaultValue = "Group" }
                )
            ) { back ->
                ChatDetailScreen(
                    chatId        = back.arguments?.getString("chatId")   ?: "",
                    peerName      = back.arguments?.getString("peerName") ?: "Group",
                    navController = innerNav
                )
            }
        }
    }
}

private fun navigateToTab(
    navController: androidx.navigation.NavController,
    route: String
) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState    = true
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF000511), Color(0xFF000000)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(
                painter            = painterResource(id = R.drawable.samvixo_logo),
                contentDescription = "Samvixo Logo",
                modifier           = Modifier.size(200.dp),
                contentScale       = ContentScale.Fit
            )
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.width(20.dp), thickness = 1.dp, color = Color(0xFF00C6FF))
                Text("  Secure AI Messaging  ", fontSize = 14.sp, color = Color(0xFF00C6FF), letterSpacing = 2.sp)
                HorizontalDivider(modifier = Modifier.width(20.dp), thickness = 1.dp, color = Color(0xFF00C6FF))
            }
        }
        Column(
            modifier            = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("from", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.Image(
                painter            = painterResource(id = R.drawable.nexuzy_lab),
                contentDescription = "Nexuzy Lab",
                modifier           = Modifier.height(40.dp),
                contentScale       = ContentScale.Fit
            )
        }
    }
}
