package com.minidex.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.minidex.app.input.adb.AdbConnectionStatus
import com.minidex.app.ui.theme.LocalMiniDexColors

@Composable
fun AdbPairingDialog(
    connectionStatus: AdbConnectionStatus,
    statusMessage: String,
    discoveredPairingPort: Int?,
    discoveredConnectPort: Int?,
    isShizukuAvailable: Boolean,
    onOpenSettings: () -> Unit,
    onPairWithCode: (port: Int, code: String) -> Unit,
    onConnectDirect: (port: Int) -> Unit,
    onRequestShizuku: () -> Unit,
    onSendTestEvent: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalMiniDexColors.current
    val shape = RoundedCornerShape(16.dp)

    var portText by remember { mutableStateOf(discoveredPairingPort?.toString() ?: "") }
    var codeText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val isBusy = connectionStatus == AdbConnectionStatus.PAIRING ||
        connectionStatus == AdbConnectionStatus.CONNECTING

    // Update port if mDNS detects one dynamically
    LaunchedEffect(discoveredPairingPort) {
        if (discoveredPairingPort != null && (portText.isBlank() || portText == "5555")) {
            portText = discoveredPairingPort.toString()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(shape)
                .border(1.5.dp, colors.accent.copy(alpha = 0.6f), shape),
            color = colors.surfaceElevated,
            shape = shape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionStatus) {
                                        AdbConnectionStatus.CONNECTED -> Color(0xFF00E676)
                                        AdbConnectionStatus.CONNECTING, AdbConnectionStatus.PAIRING -> Color(0xFFFFD600)
                                        AdbConnectionStatus.SEARCHING_MDNS -> Color(0xFF00E5FF)
                                        AdbConnectionStatus.ERROR -> Color(0xFFFF5252)
                                        else -> Color.Gray
                                    }
                                )
                        )
                        Text(
                            text = "WIRELESS ADB PAIRING",
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "✕",
                        color = colors.textSecondary,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }

                // Status banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusMessage,
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        if (connectionStatus == AdbConnectionStatus.PAIRING || connectionStatus == AdbConnectionStatus.CONNECTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = colors.accent,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                // If already connected
                if (connectionStatus == AdbConnectionStatus.CONNECTED) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00E676).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF00E676).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "⚡ ADB ACTIVE & READY",
                                color = Color(0xFF00E676),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = onSendTestEvent,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676).copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Send Test Click to DeX", color = Color(0xFF00E676), fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Step 1: Open Settings
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "1. Open Wireless Debugging ↗",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Step 2 & 3: Port & 6-Digit PIN
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "2. Tap 'Pair device with pairing code'",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it },
                            enabled = !isBusy,
                            label = {
                                Text(
                                    text = if (discoveredPairingPort != null) "Auto Port: $discoveredPairingPort" else "Pairing Port",
                                    fontSize = 10.sp
                                )
                            },
                            placeholder = { Text("37482", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = codeText,
                            onValueChange = { if (it.length <= 6) codeText = it },
                            enabled = !isBusy,
                            label = { Text("6-Digit Code", fontSize = 10.sp) },
                            placeholder = { Text("123456", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val p = portText.toIntOrNull() ?: discoveredPairingPort
                                if (p != null && codeText.isNotBlank()) onPairWithCode(p, codeText)
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.accent,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            modifier = Modifier
                                .weight(1.2f)
                                .focusRequester(focusRequester)
                        )
                    }

                    // Auto-Discovered Badge
                    if (discoveredPairingPort != null) {
                        Text(
                            text = "✓ Discovered Pairing Port #$discoveredPairingPort via mDNS",
                            color = Color(0xFF00E676),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            val p = portText.toIntOrNull() ?: discoveredPairingPort
                            if (p != null && codeText.isNotBlank()) {
                                onPairWithCode(p, codeText)
                            }
                        },
                        enabled = !isBusy && codeText.length == 6 &&
                            (portText.isNotBlank() || discoveredPairingPort != null),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            disabledContainerColor = colors.surfaceElevated
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Pair & Connect", fontSize = 12.sp, color = if (codeText.length == 6) Color.Black else colors.textSecondary)
                    }
                }

                // Shizuku Alternative Option
                if (isShizukuAvailable) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { onRequestShizuku() }
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Shizuku Service Running", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Tap to authorize and connect", color = colors.textSecondary, fontSize = 10.sp)
                            }
                            Text("Connect ›", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Direct connect to port 5555 / custom port
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !isBusy && discoveredConnectPort != null,
                        onClick = {
                            discoveredConnectPort?.let(onConnectDirect)
                        }
                    ) {
                        Text(
                            text = discoveredConnectPort?.let { "Connect (#$it)" }
                                ?: "Waiting for connect port…",
                            color = if (discoveredConnectPort != null) colors.accent else colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close", color = colors.accent, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
