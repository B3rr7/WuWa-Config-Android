package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IniEditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val editingFileName by viewModel.editingFileName.collectAsState()
    val iniContent by viewModel.iniEditorContent.collectAsState()
    val isLoading by viewModel.iniEditorLoading.collectAsState()
    val errorMessage by viewModel.iniEditorError.collectAsState()
    val successMessage by viewModel.iniEditorSuccess.collectAsState()

    var editorText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.syncConfigHashes()
    }

    LaunchedEffect(iniContent) {
        iniContent?.let { editorText = it }
    }

    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearIniEditorSuccess()
        }
    }

    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (editingFileName != null) {
                                editingFileName!!
                            } else {
                                "INI Editor"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (editingFileName != null) {
                                viewModel.returnToFileList()
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NeonPurple)
                        }
                    },
                    actions = {
                        if (editingFileName != null) {
                            IconButton(
                                onClick = { viewModel.saveIniFile(editorText) },
                                enabled = !isLoading,
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    "Save",
                                    tint = if (!isLoading) NeonAmber else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                            }
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        ),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeonCyan)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (editingFileName != null) "Saving $editingFileName..." else "Loading...",
                            color = NeonCyan,
                        )
                    }
                }
            } else if (editingFileName != null && iniContent != null) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .imePadding()
                            .verticalScroll(rememberScrollState()),
                ) {
                    TextField(
                        value = editorText,
                        onValueChange = { editorText = it },
                        modifier = Modifier.fillMaxSize().padding(8.dp).heightIn(min = 1000.dp),
                        textStyle =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFFE0E0E0),
                            ),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1A1A2E),
                                unfocusedContainerColor = Color(0xFF1A1A2E),
                                focusedIndicatorColor = NeonAmber,
                                unfocusedIndicatorColor = Color(0xFF3A3A5C),
                                cursorColor = NeonAmber,
                            ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Select an INI file to edit:",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonAmber,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    itemsIndexed(GamePaths.MONITORED_FILES) { _, fileName ->
                        IniFileCard(
                            fileName = fileName,
                            onClick = { viewModel.readIniFile(fileName) },
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Changes are pushed directly to device and hashes are refreshed — the game cannot detect tampering.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            errorMessage?.let { msg ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = Color(0xFF4A1E8A).copy(alpha = 0.95f),
                            ),
                    ) {
                        Text(
                            msg,
                            modifier = Modifier.padding(16.dp),
                            color = NeonRed,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            successMessage?.let { msg ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = Color(0xFF004D40).copy(alpha = 0.95f),
                            ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                "Success",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(msg, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IniFileCard(
    fileName: String,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2E).copy(alpha = 0.8f),
            ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmber.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Description,
                null,
                tint = NeonAmber.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                fileName,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Edit ›",
                style = MaterialTheme.typography.bodySmall,
                color = NeonAmber.copy(alpha = 0.7f),
            )
        }
    }
}
