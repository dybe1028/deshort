package com.dybe.deshort

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dybe.deshort.data.DeshortPreferences
import com.dybe.deshort.ui.theme.DeshortTheme

/**
 * MainActivity — launcher entry point.
 *
 * Guides the user through enabling and selecting the Deshort keyboard,
 * and provides settings for appearance, clipboard, and keyboard feel.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeshortTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SetupScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SetupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val prefs = remember { DeshortPreferences(context) }

    // IME status
    var isEnabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    var isSelected by remember { mutableStateOf(isKeyboardSelected(context)) }
    var testText by remember { mutableStateOf("") }

    // Input settings
    var autoCapEnabled by remember { mutableStateOf(prefs.isAutoCapitalizationEnabled) }
    var autoSwitchBackEnabled by remember { mutableStateOf(prefs.isAutoSwitchBackAbcEnabled) }

    // Appearance settings
    var themeMode by remember { mutableStateOf(prefs.themeMode) }
    var lightStartHour by remember { mutableIntStateOf(prefs.scheduledLightStartHour) }
    var darkStartHour by remember { mutableIntStateOf(prefs.scheduledDarkStartHour) }
    var toolbarVisible by remember { mutableStateOf(prefs.isToolbarVisible) }

    // Clipboard settings
    var clipboardPreview by remember { mutableStateOf(prefs.isClipboardPreviewEnabled) }

    // Keyboard feel
    var vibrationEnabled by remember { mutableStateOf(prefs.isKeyVibrationEnabled) }
    var soundEnabled by remember { mutableStateOf(prefs.isKeySoundEnabled) }

    // Refresh IME status when returning from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEnabled = isKeyboardEnabled(context)
                isSelected = isKeyboardSelected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Deshort Keyboard",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Bàn phím gõ tiếng Việt Telex thông minh",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Step 1: Enable ────────────────────────────────────────────────────
        SetupCard(
            completed = isEnabled,
            title = if (isEnabled) "✓ Bước 1: Đã bật bàn phím" else "Bước 1: Bật bàn phím Deshort",
            body = if (isEnabled) "Deshort đã được kích hoạt." else "Kích hoạt Deshort trong cài đặt bàn phím."
        ) {
            if (!isEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Bật trong Cài đặt") }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Step 2: Select ────────────────────────────────────────────────────
        SetupCard(
            completed = isSelected,
            title = if (isSelected) "✓ Bước 2: Đang sử dụng Deshort" else "Bước 2: Chọn Deshort làm bàn phím chính",
            body = if (isSelected) "Deshort đang là bàn phím hoạt động." else "Chuyển phương thức nhập liệu sang Deshort Keyboard."
        ) {
            if (!isSelected) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showInputMethodPicker()
                    },
                    enabled = isEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Chọn bàn phím Deshort") }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Appearance Settings ───────────────────────────────────────────────
        SettingsCard(title = "Giao diện") {
            // Theme mode selector
            SectionLabel("Chế độ màu")
            ThemeModeSelector(
                current = themeMode,
                onSelect = { mode ->
                    themeMode = mode
                    prefs.themeMode = mode
                }
            )

            // Scheduled hours (shown only when scheduled is selected)
            if (themeMode == DeshortPreferences.THEME_SCHEDULED) {
                Spacer(modifier = Modifier.height(8.dp))
                HourPicker(
                    label = "Giờ bắt đầu chế độ Sáng",
                    hour = lightStartHour,
                    onHourChange = { h ->
                        lightStartHour = h
                        prefs.scheduledLightStartHour = h
                    }
                )
                HourPicker(
                    label = "Giờ bắt đầu chế độ Tối",
                    hour = darkStartHour,
                    onHourChange = { h ->
                        darkStartHour = h
                        prefs.scheduledDarkStartHour = h
                    }
                )
            }

            Divider()

            // Toolbar visibility
            SettingsSwitch(
                label = "Hiện thanh công cụ",
                description = "Emoji, nhớ tạm, và điều khiển con trỏ",
                checked = toolbarVisible,
                onCheckedChange = {
                    toolbarVisible = it
                    prefs.isToolbarVisible = it
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Clipboard Settings ────────────────────────────────────────────────
        SettingsCard(title = "Nhớ tạm") {
            SettingsSwitch(
                label = "Hiện xem trước nhớ tạm",
                description = "Hiện nội dung sao chép gần nhất trên thanh công cụ",
                checked = clipboardPreview,
                onCheckedChange = {
                    clipboardPreview = it
                    prefs.isClipboardPreviewEnabled = it
                }
            )
            Divider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = {
                    prefs.clearClipboardHistory()
                }) {
                    Text("Xóa lịch sử nhớ tạm")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Keyboard Settings ─────────────────────────────────────────────────
        SettingsCard(title = "Bàn phím") {
            SettingsSwitch(
                label = "Tự động viết hoa đầu câu",
                description = "Tự động viết hoa sau dấu chấm, hỏi, than (., ?, !)",
                checked = autoCapEnabled,
                onCheckedChange = {
                    autoCapEnabled = it
                    prefs.isAutoCapitalizationEnabled = it
                }
            )
            Divider()
            SettingsSwitch(
                label = "Tự về bảng chữ cái",
                description = "Tự động quay về bảng gõ chữ khi bấm Cách sau ký tự đặc biệt",
                checked = autoSwitchBackEnabled,
                onCheckedChange = {
                    autoSwitchBackEnabled = it
                    prefs.isAutoSwitchBackAbcEnabled = it
                }
            )
            Divider()
            SettingsSwitch(
                label = "Rung khi nhấn phím",
                description = "Phản hồi xúc giác khi nhấn phím (nếu thiết bị hỗ trợ)",
                checked = vibrationEnabled,
                onCheckedChange = {
                    vibrationEnabled = it
                    prefs.isKeyVibrationEnabled = it
                }
            )
            Divider()
            SettingsSwitch(
                label = "Âm thanh khi nhấn phím",
                description = "Phát âm thanh click nhẹ khi nhấn phím",
                checked = soundEnabled,
                onCheckedChange = {
                    soundEnabled = it
                    prefs.isKeySoundEnabled = it
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Test area ─────────────────────────────────────────────────────────
        Text(
            text = "Khu vực gõ thử nghiệm:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = testText,
            onValueChange = { testText = it },
            placeholder = { Text("Chạm vào đây để gõ thử (vd: Việt Nam, quyết tâm, đường xá)...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        if (testText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { testText = "" },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Xóa văn bản") }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Reusable composables
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetupCard(
    completed: Boolean,
    title: String,
    body: String,
    content: @Composable () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (completed)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun ThemeModeSelector(current: String, onSelect: (String) -> Unit) {
    val modes = listOf(
        DeshortPreferences.THEME_LIGHT to "Sáng",
        DeshortPreferences.THEME_DARK to "Tối",
        DeshortPreferences.THEME_SYSTEM to "Hệ thống",
        DeshortPreferences.THEME_SCHEDULED to "Theo giờ"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { (mode, label) ->
            if (mode == current) {
                Button(onClick = { onSelect(mode) }) { Text(label, style = MaterialTheme.typography.labelMedium) }
            } else {
                OutlinedButton(onClick = { onSelect(mode) }) { Text(label, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun HourPicker(label: String, hour: Int, onHourChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%02d:00".format(hour),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(
                onClick = { if (hour > 0) onHourChange(hour - 1) },
                modifier = Modifier.padding(0.dp)
            ) { Text("−") }
            OutlinedButton(
                onClick = { if (hour < 23) onHourChange(hour + 1) },
                modifier = Modifier.padding(0.dp)
            ) { Text("+") }
        }
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

// ────────────────────────────────────────────────────────────────────────────
// IME Status helpers
// ────────────────────────────────────────────────────────────────────────────

private fun isKeyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    val enabledList = imm?.enabledInputMethodList ?: return false
    return enabledList.any { it.packageName == context.packageName }
}

private fun isKeyboardSelected(context: Context): Boolean {
    val currentIme = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD
    )
    return currentIme?.contains(context.packageName) == true
}