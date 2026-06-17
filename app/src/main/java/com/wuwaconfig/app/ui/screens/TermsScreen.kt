package com.wuwaconfig.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*

@Composable
fun TermsScreen(onAccept: () -> Unit) {
    val context = LocalContext.current

    GradientBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(Modifier.height(24.dp))

                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = NeonAmber,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Disclaimer & Terms of Use",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Please read carefully before proceeding",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                GlassCard(accentColor = NeonAmber) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonAmber.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "This application is a FAN-MADE tool for modifying game configuration files.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )

                        Text(
                            "NOT AFFILIATED WITH KURO GAMES OR WUTHERING WAVES",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonRed,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(
                            color = NeonAmber.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )

                        Text(
                            "By installing and using this application, you acknowledge and agree to the following:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BulletPoint(
                                "This tool edits and modifies game configuration files (.ini) for Wuthering Waves."
                            )
                            BulletPoint(
                                "Modifying game files may be subject to the game's Terms of Service. You assume full responsibility."
                            )
                            BulletPoint(
                                "The creator of this application is NOT responsible for any account actions, bans, penalties, or issues that may arise from using modified configuration files."
                            )
                            BulletPoint(
                                "This application is provided \"AS IS\" without any warranty, express or implied."
                            )
                            BulletPoint(
                                "You use this software at your own risk. No guarantees are made about its safety or compatibility."
                            )
                            BulletPoint(
                                "No game assets, code, or copyrighted material from Wuthering Waves is distributed with this app."
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        HorizontalDivider(
                            color = NeonAmber.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )

                        Text(
                            "By tapping \"Agree & Continue\", you confirm that you have read, understood, and accepted these terms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻⸻",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonAmber.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                GlassButton(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    accentColor = NeonGreen,
                    contentColor = Color.White
                ) {
                    Text("Agree & Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = { (context as? Activity)?.finishAffinity() },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        "Decline & Exit",
                        color = NeonRed.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "•",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonAmber,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
    }
}
