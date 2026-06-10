package com.nexuzy.samvixo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * TrustScoreWidget — displays the 0-100 trust score badge on profiles (§13 PID)
 *
 * Trust score logic:
 *   +5  on successful verification
 *   -10 on each report
 *   0-5 reports: normal status
 *   6+ reports: 48h restriction + manual review queue
 *
 * Managed by Cloud Function: updateTrustScore
 * Stored in: users/{uid}.trustScore
 */
@Composable
fun TrustScoreWidget(
    score: Int,
    reportCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val (color, label) = when {
        score >= 80  -> Color(0xFF2E7D32) to "Trusted"
        score >= 50  -> Color(0xFFF57F17) to "Moderate"
        score >= 20  -> Color(0xFFE65100) to "Caution"
        else         -> Color(0xFFC62828) to "Restricted"
    }

    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier           = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = "Trust Score",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text  = score.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = color
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { score / 100f },
                modifier = Modifier.fillMaxWidth(),
                color    = color
            )
            if (reportCount >= 6) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "⚠️ Account under review ($reportCount reports)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
