package com.nexuzy.samvixo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// ─────────────────────────────────────────────────────────────────────────────
// AboutUsScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutUsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Samvixo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text("Samvixo", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Version 1.0.0", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(24.dp))

            InfoSection(
                title = "What is Samvixo?",
                body  = "Samvixo is a next-generation secure messaging app built for India. " +
                    "It combines end-to-end encrypted messaging, AI assistance, status updates, " +
                    "channels, and offline mesh networking \u2014 all in one app."
            )
            InfoSection(
                title = "End-to-End Encryption",
                body  = "Every message, call, and file is encrypted on your device before it leaves. " +
                    "Not even Samvixo\'s servers can read your conversations."
            )
            InfoSection(
                title = "AI-Powered Features",
                body  = "Devil AI assists with message suggestions, smart search, and productivity tools " +
                    "\u2014 powered by state-of-the-art language models."
            )
            InfoSection(
                title = "Built by Nexuzy Lab",
                body  = "Samvixo is developed and maintained by Nexuzy Lab, an Indian technology company " +
                    "focused on privacy-first communication software."
            )

            Spacer(Modifier.height(24.dp))
            Text("\u00a9 2025 Nexuzy Lab. All rights reserved.", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun InfoSection(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 14.sp, color = Color.Gray, lineHeight = 22.sp)
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(thickness = 0.5.dp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TermsConditionsScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsConditionsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Conditions") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Terms & Conditions", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Last updated: January 2025", fontSize = 12.sp, color = Color.Gray)
            HorizontalDivider()

            TermsSection("1. Acceptance of Terms",
                "By downloading, installing, or using Samvixo, you agree to be bound by these Terms and Conditions. " +
                "If you do not agree, please uninstall the application.")
            TermsSection("2. Eligibility",
                "You must be at least 13 years of age to use Samvixo. By using this app you represent that you meet this requirement.")
            TermsSection("3. Account Responsibility",
                "You are responsible for maintaining the confidentiality of your account. " +
                "You agree to notify us immediately of any unauthorized use of your account.")
            TermsSection("4. Prohibited Conduct",
                "You agree not to use Samvixo to send spam, illegal content, or to harass other users. " +
                "Violation may result in immediate account termination.")
            TermsSection("5. Intellectual Property",
                "All content, trademarks, and technology in Samvixo are the property of Nexuzy Lab. " +
                "You may not copy, modify, or distribute our proprietary content without permission.")
            TermsSection("6. Limitation of Liability",
                "Nexuzy Lab is not liable for any indirect, incidental, or consequential damages arising from your use of Samvixo.")
            TermsSection("7. Changes to Terms",
                "We reserve the right to update these Terms at any time. Continued use after changes constitutes acceptance of the new Terms.")
            TermsSection("8. Contact",
                "For questions about these Terms, contact us via the \"Contact Us\" section in the app.")
        }
    }
}

@Composable
private fun TermsSection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 13.sp, color = Color.Gray, lineHeight = 20.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PrivacyPolicyScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Last updated: January 2025", fontSize = 12.sp, color = Color.Gray)
            HorizontalDivider()

            TermsSection("1. Information We Collect",
                "We collect the minimum information required to provide our service: your phone number for authentication, " +
                "and your display name and profile photo (optional). Message content is end-to-end encrypted and " +
                "cannot be read by Samvixo.")
            TermsSection("2. How We Use Your Information",
                "Your phone number is used solely for account verification. We do not sell, share, or monetise " +
                "your personal data.")
            TermsSection("3. Data Storage",
                "Your messages are stored encrypted in Firebase. Backup copies are optionally stored in your " +
                "personal Google Drive — we have no access to them.")
            TermsSection("4. Third-Party Services",
                "We use Firebase (Google) for authentication and data storage, subject to Google\'s privacy policy. " +
                "We also use Google ML Kit for on-device QR scanning — this runs entirely on-device.")
            TermsSection("5. Your Rights",
                "You may delete your account at any time from Settings \u2192 Account \u2192 Delete Account. " +
                "This permanently removes all your data from our servers.")
            TermsSection("6. Children\'s Privacy",
                "Samvixo is not intended for children under 13. We do not knowingly collect data from children.")
            TermsSection("7. Contact",
                "For privacy concerns, contact us via the \"Contact Us\" section in the app.")
        }
    }
}
