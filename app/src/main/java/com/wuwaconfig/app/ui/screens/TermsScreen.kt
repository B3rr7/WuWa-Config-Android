package com.wuwaconfig.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                // ---------------------------------------------------------------- header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TermsHeaderBadge()
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    "Disclaimer & Terms of Use",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "Please read carefully before proceeding",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(20.dp))

                // -------------------------------------------------- "not affiliated" strip
                // The one legal must-be-read line gets its own flat card instead of
                // being buried in the middle of a wall of text.
                GlassCard(accentColor = NeonRed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "!",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                ),
                            color = NeonRed,
                            modifier =
                                Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NeonRed.copy(alpha = 0.12f))
                                    .wrapContentSize(Alignment.Center),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "NOT AFFILIATED WITH",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "KURO GAMES OR WUTHERING WAVES",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = NeonRed,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "This application is a fan-made tool for modifying game configuration files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ------------------------------------------------------------------ body
                GlassCard(accentColor = NeonCyan) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(
                            "By installing and using this application, you acknowledge and agree to the following:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // ------------------------------------------------ 1 · the tool
                        TermsSection(
                            number = "01",
                            icon = Icons.Default.SettingsEthernet,
                            title = "What this app is",
                            accent = NeonCyan,
                        ) {
                            TermsBullet(
                                "Edits and modifies game configuration files (.ini) for Wuthering Waves.",
                            )
                            TermsBullet(
                                "No game assets, code, or copyrighted material from Wuthering Waves is distributed with this app.",
                            )
                        }

                        HorizontalDivider(color = NeonAmber.copy(alpha = 0.12f), thickness = 1.dp)

                        // ------------------------------------------- 2 · the acceptance
                        TermsSection(
                            number = "02",
                            icon = Icons.Default.Lock,
                            title = "What you accept",
                            accent = NeonAmber,
                        ) {
                            TermsBullet(
                                "Modifying game files may be subject to the game's Terms of Service. You assume full responsibility.",
                            )
                            TermsBullet(
                                "The creator of this application is NOT responsible for any account actions, bans, penalties, or issues " +
                                    "that may arise from using modified configuration files.",
                            )
                            TermsBullet(
                                "This application is provided \"AS IS\" without any warranty, express or implied.",
                            )
                            TermsBullet(
                                "You use this software at your own risk. No guarantees are made about its safety or compatibility.",
                            )
                        }

                        HorizontalDivider(color = NeonAmber.copy(alpha = 0.12f), thickness = 1.dp)

                        // ------------------------------------------------ 3 · privacy
                        TermsSection(
                            number = "03",
                            icon = Icons.Default.VerifiedUser,
                            title = "Privacy",
                            accent = NeonGreen,
                        ) {
                            TermsBullet(
                                "No analytics, advertising, or crash-reporting. Nothing is collected, profiled, or sold. " +
                                    "There is no account and no login.",
                            )
                            TermsBullet(
                                "Everything this app saves — your profile, gacha history, deploy history, backups and logs — " +
                                    "stays in this app's PRIVATE storage, readable by no other app. Android device-to-device " +
                                    "transfer and cloud backup of app data are disabled.",
                            )
                            TermsBullet(
                                "Network access happens only when you ask for it: a gacha-history lookup sends the ID from a " +
                                    "gacha URL you paste in to the game's own official server, and the update check contacts GitHub. " +
                                    "Nothing else is transmitted.",
                            )
                            // Do NOT re-add "screenshots are blocked" here. `SECURE_SCREENSHOTS`
                            // is false for every variant (see build.gradle.kts), so FLAG_SECURE
                            // is never set and screenshots, screencap and the recents thumbnail
                            // all work. This sentence was false once already.
                            TermsBullet(
                                "If you use the Save/Export action on the Logs screen, a copy of the log is written to your " +
                                    "Downloads folder. The clipboard is marked sensitive so keyboards do not keep a copy.",
                            )
                            TermsBullet(
                                "This app requests elevated storage and Shizuku/Root access only to reach the game's own config " +
                                    "directory. Uninstalling removes all of its local data.",
                            )
                        }

                        HorizontalDivider(color = NeonAmber.copy(alpha = 0.12f), thickness = 1.dp)

                        Text(
                            "By tapping \"Agree & Continue\", you confirm that you have read, understood, and accepted these terms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                GlassButton(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    accentColor = NeonGreen,
                    contentColor = Color.White,
                ) {
                    Text("Agree & Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = { (context as? Activity)?.finishAffinity() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(
                        "Decline & Exit",
                        color = NeonRed.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TermsHeaderBadge() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeonAmber.copy(alpha = 0.12f))
                    .wrapContentSize(Alignment.Center),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = NeonAmber,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** A numbered flat section: kicker chip, icon square, title, then the rules. */
@Composable
private fun TermsSection(
    number: String,
    icon: ImageVector,
    title: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .wrapContentSize(Alignment.Center),
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                number,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = accent.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** Flat rule line: square accent dot instead of the old text bullet. */
@Composable
private fun TermsBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 7.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.8f)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}

@Preview(name = "Terms Light", showBackground = true)
@Composable
private fun TermsScreenLightPreview() {
    WuWaConfigTheme(themeMode = "light") {
        GradientBackground {
            Text("Terms Preview")
        }
    }
}

@Preview(name = "Terms Dark", showBackground = true)
@Composable
private fun TermsScreenDarkPreview() {
    WuWaConfigTheme(themeMode = "dark") {
        GradientBackground {
            Text("Terms Preview")
        }
    }
}
