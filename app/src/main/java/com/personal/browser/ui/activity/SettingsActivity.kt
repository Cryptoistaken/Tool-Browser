package com.personal.browser.ui.activity

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) importScriptFromFile(uri) }

    companion object {
        const val PREF_AD_BLOCKING     = "pref_ad_blocking"
        const val PREF_BUILT_FILTERS   = "pref_built_in_filters"
        const val PREF_EXPAND_CONTENT  = "pref_expand_content"
        const val PREF_SCRIPTS_ENABLED = "pref_scripts_enabled"
        const val PREF_HOMEPAGE_URL    = "pref_homepage_url"
        const val PREF_CLEAR_ON_EXIT   = "pref_clear_on_exit"
        const val PREF_DARK_MODE       = "pref_dark_mode"
        const val PREF_SCRIPTS_JSON    = "pref_scripts_json"
        const val DEFAULT_HOMEPAGE     = "https://www.google.com"

        val HOMEPAGE_PRESETS = listOf(
            "https://www.google.com",
            "https://www.instagram.com/accounts/login",
            "https://m.facebook.com/"
        )
    }

    data class UserScript(
        var name: String,
        var source: String,
        var code: String,
        var enabled: Boolean = true
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("browser_prefs", MODE_PRIVATE)
        val mode = intent.getStringExtra("SETTINGS_MODE") ?: "GENERAL"
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (mode) {
                        "AD_BLOCKING" -> AdBlockingScreen()
                        "SCRIPTS"     -> ScriptsScreen()
                        else          -> GeneralSettingsScreen()
                    }
                }
            }
        }
    }

    // ── General Settings ─────────────────────────────────────────────────────

    @Composable
    private fun GeneralSettingsScreen() {
        val currentUrl = prefs.getString(PREF_HOMEPAGE_URL, DEFAULT_HOMEPAGE) ?: DEFAULT_HOMEPAGE
        val presetIndex = HOMEPAGE_PRESETS.indexOf(currentUrl)
        var selectedPreset by remember { mutableIntStateOf(if (presetIndex >= 0) presetIndex else HOMEPAGE_PRESETS.size) }
        var customUrl by remember { mutableStateOf(if (presetIndex < 0) currentUrl else "") }
        var darkMode by remember { mutableStateOf(prefs.getBoolean(PREF_DARK_MODE, false)) }
        var clearOnExit by remember { mutableStateOf(prefs.getBoolean(PREF_CLEAR_ON_EXIT, false)) }
        val focusManager = LocalFocusManager.current

        SettingsScaffold(title = "Settings") {
            SectionLabel("BROWSER")
            SectionCard {
                Text("Default Homepage", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "Select or type the URL loaded on new tab",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                val options = HOMEPAGE_PRESETS + "Custom…"
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = options[selectedPreset],
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        options.forEachIndexed { idx, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedPreset = idx
                                    expanded = false
                                    if (idx < HOMEPAGE_PRESETS.size) {
                                        prefs.edit().putString(PREF_HOMEPAGE_URL, HOMEPAGE_PRESETS[idx]).apply()
                                    }
                                }
                            )
                        }
                    }
                }
                if (selectedPreset == HOMEPAGE_PRESETS.size) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        placeholder = { Text("https://www.google.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            saveCustomHomepage(customUrl)
                            focusManager.clearFocus()
                        })
                    )
                }
            }

            SectionLabel("APPEARANCE")
            SectionCard {
                SwitchRow(
                    title = "Dark Mode",
                    subtitle = "Switch between light and dark theme",
                    checked = darkMode,
                    onCheckedChange = {
                        darkMode = it
                        prefs.edit().putBoolean(PREF_DARK_MODE, it).apply()
                        AppCompatDelegate.setDefaultNightMode(
                            if (it) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                        )
                    }
                )
            }

            SectionLabel("PRIVACY")
            SectionCard {
                SwitchRow(
                    title = "Clear data on exit",
                    subtitle = "Clears cache, cookies and history when app is closed",
                    checked = clearOnExit,
                    onCheckedChange = {
                        clearOnExit = it
                        prefs.edit().putBoolean(PREF_CLEAR_ON_EXIT, it).apply()
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                NavRow(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    title = "Clear browsing data now",
                    onClick = {
                        setResult(Activity.RESULT_OK, Intent().putExtra("ACTION", "CLEAR_DATA"))
                        finish()
                    }
                )
            }

            SectionLabel("CONTENT")
            SectionCard {
                NavRow(
                    title = "Ad Blocking",
                    subtitle = "Block ads and trackers",
                    onClick = {
                        startActivity(
                            Intent(this@SettingsActivity, SettingsActivity::class.java)
                                .putExtra("SETTINGS_MODE", "AD_BLOCKING")
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                NavRow(
                    title = "User Scripts",
                    subtitle = "Manage and run custom scripts on pages",
                    onClick = {
                        startActivity(
                            Intent(this@SettingsActivity, SettingsActivity::class.java)
                                .putExtra("SETTINGS_MODE", "SCRIPTS")
                        )
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    private fun saveCustomHomepage(url: String) {
        val final = when {
            url.isEmpty() -> DEFAULT_HOMEPAGE
            url.startsWith("http://") || url.startsWith("https://") -> url
            else -> "https://$url"
        }
        prefs.edit().putString(PREF_HOMEPAGE_URL, final).apply()
    }

    // ── Ad Blocking ──────────────────────────────────────────────────────────

    @Composable
    private fun AdBlockingScreen() {
        var adBlocking by remember { mutableStateOf(prefs.getBoolean(PREF_AD_BLOCKING, true)) }
        var builtFilters by remember { mutableStateOf(prefs.getBoolean(PREF_BUILT_FILTERS, true)) }
        var expandContent by remember { mutableStateOf(prefs.getBoolean(PREF_EXPAND_CONTENT, true)) }

        SettingsScaffold(title = "Ad Blocking") {
            SectionLabel("FILTERS")
            SectionCard {
                SwitchRow("Ad blocking", "Block ads and trackers while browsing", adBlocking) {
                    adBlocking = it; prefs.edit().putBoolean(PREF_AD_BLOCKING, it).apply()
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SwitchRow("Enable built-in filters", "Block common ads using built-in filter lists", builtFilters) {
                    builtFilters = it; prefs.edit().putBoolean(PREF_BUILT_FILTERS, it).apply()
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SwitchRow("Expand webpage content", "Expand collapsed content automatically on load", expandContent) {
                    expandContent = it; prefs.edit().putBoolean(PREF_EXPAND_CONTENT, it).apply()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ── User Scripts ─────────────────────────────────────────────────────────

    @Composable
    private fun ScriptsScreen() {
        var scriptsEnabled by remember { mutableStateOf(prefs.getBoolean(PREF_SCRIPTS_ENABLED, true)) }
        var scripts by remember { mutableStateOf(loadScripts()) }

        // Dialog state — all booleans/targets hoisted here so dialogs stay in Compose tree
        var showPickerDialog by remember { mutableStateOf(false) }
        var showAddTextDialog by remember { mutableStateOf(false) }
        var editTarget by remember { mutableStateOf<Pair<Int, UserScript>?>(null) }
        var deleteTarget by remember { mutableStateOf<Pair<Int, UserScript>?>(null) }

        SettingsScaffold(
            title = "User Scripts",
            actions = {
                IconButton(onClick = { showPickerDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add script")
                }
            }
        ) {
            SectionLabel("SETTINGS")
            SectionCard {
                SwitchRow("Enable user scripts", checked = scriptsEnabled) {
                    scriptsEnabled = it
                    prefs.edit().putBoolean(PREF_SCRIPTS_ENABLED, it).apply()
                }
            }

            SectionLabel("INSTALLED SCRIPTS")
            if (scripts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No user scripts added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                SectionCard {
                    scripts.forEachIndexed { index, script ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editTarget = index to script }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(script.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(
                                    script.source,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Switch(
                                checked = script.enabled,
                                onCheckedChange = { checked ->
                                    val updated = scripts.toMutableList()
                                    updated[index] = script.copy(enabled = checked)
                                    scripts = updated
                                    saveScripts(updated)
                                }
                            )
                            IconButton(onClick = { deleteTarget = index to script }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── Dialogs (always in Compose tree, shown via state) ─────────────────

        if (showPickerDialog) {
            AddScriptPickerDialog(
                onDismiss = { showPickerDialog = false },
                onAddText = { showPickerDialog = false; showAddTextDialog = true },
                onAddFromFile = { showPickerDialog = false; filePickerLauncher.launch("text/*") },
                onAddFromUrl = { showPickerDialog = false }
            )
        }

        if (showAddTextDialog) {
            ScriptTextDialog(
                existing = null,
                onDismiss = { showAddTextDialog = false },
                onSave = { s ->
                    val updated = loadScripts().also { it.add(s) }
                    saveScripts(updated)
                    scripts = updated
                    toast("Script added")
                    showAddTextDialog = false
                }
            )
        }

        editTarget?.let { (idx, script) ->
            ScriptEditDialog(
                script = script,
                onDismiss = { editTarget = null },
                onSave = { updated ->
                    val list = loadScripts().toMutableList()
                    if (idx < list.size) { list[idx] = updated; saveScripts(list); scripts = list; toast("Script updated") }
                    editTarget = null
                },
                onRefetch = if (script.source.startsWith("http")) ({
                    fetchScriptFromUrl(script.source, script.name) { fetched ->
                        val list = loadScripts().toMutableList()
                        if (idx < list.size) { list[idx] = fetched; saveScripts(list); scripts = list }
                    }
                    editTarget = null
                }) else null
            )
        }

        deleteTarget?.let { (idx, script) ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete Script") },
                text = { Text("Remove \"${script.name}\"?") },
                confirmButton = {
                    TextButton(onClick = {
                        val list = loadScripts().toMutableList()
                        if (idx < list.size) { list.removeAt(idx); saveScripts(list); scripts = list }
                        toast("Script deleted")
                        deleteTarget = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
            )
        }
    }

    // ── Script dialogs ────────────────────────────────────────────────────────

    @Composable
    private fun AddScriptPickerDialog(
        onDismiss: () -> Unit,
        onAddText: () -> Unit,
        onAddFromFile: () -> Unit,
        onAddFromUrl: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add Script") },
            text = {
                Column {
                    listOf("From URL", "From File", "Enter Script").forEachIndexed { idx, label ->
                        TextButton(
                            onClick = { when (idx) { 0 -> onAddFromUrl(); 1 -> onAddFromFile(); else -> onAddText() } },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    @Composable
    private fun ScriptTextDialog(
        existing: UserScript?,
        onDismiss: () -> Unit,
        onSave: (UserScript) -> Unit
    ) {
        var name by remember { mutableStateOf(existing?.name ?: "") }
        var code by remember { mutableStateOf(existing?.code ?: "") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (existing != null) "Edit Script" else "Enter Script") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = code, onValueChange = { code = it },
                        label = { Text("JavaScript code") },
                        modifier = Modifier.fillMaxWidth().height(160.dp), maxLines = 10)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (code.isNotBlank()) {
                        onSave(UserScript(name.trim().ifEmpty { "Custom Script" }, "text input", code.trim()))
                    }
                }) { Text(if (existing != null) "Save" else "Add") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    @Composable
    private fun ScriptEditDialog(
        script: UserScript,
        onDismiss: () -> Unit,
        onSave: (UserScript) -> Unit,
        onRefetch: (() -> Unit)?
    ) {
        var name by remember { mutableStateOf(script.name) }
        var code by remember { mutableStateOf(script.code) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit \"${script.name}\"") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = code, onValueChange = { code = it },
                        label = { Text("Code") },
                        modifier = Modifier.fillMaxWidth().height(160.dp), maxLines = 10)
                    if (onRefetch != null) {
                        TextButton(onClick = onRefetch, modifier = Modifier.fillMaxWidth()) {
                            Text("Re-fetch from URL")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSave(script.copy(name = name, code = code)) }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    // ── Script persistence ────────────────────────────────────────────────────

    private fun loadScripts(): MutableList<UserScript> {
        val json = prefs.getString(PREF_SCRIPTS_JSON, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                UserScript(o.optString("name", "Script"), o.optString("source", ""),
                    o.optString("code", ""), o.optBoolean("enabled", true))
            }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    private fun saveScripts(scripts: List<UserScript>) {
        val arr = JSONArray()
        scripts.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name); put("source", s.source)
                put("code", s.code); put("enabled", s.enabled)
            })
        }
        prefs.edit().putString(PREF_SCRIPTS_JSON, arr.toString()).apply()
    }

    private fun fetchScriptFromUrl(url: String, nameHint: String, onDone: (UserScript) -> Unit = {}) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000; readTimeout = 15_000
                        requestMethod = "GET"; setRequestProperty("User-Agent", "Mozilla/5.0")
                    }
                    try {
                        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                        conn.inputStream.bufferedReader().readText()
                    } finally { conn.disconnect() }
                }
            }
            result.onSuccess { code ->
                val meta = Regex("""//\s*@name\s+(.+)""").find(code)?.groupValues?.getOrNull(1)?.trim()
                val script = UserScript(nameHint.ifEmpty { meta ?: url.substringAfterLast("/") }, url, code)
                val list = loadScripts().also { it.add(script) }
                saveScripts(list); onDone(script); toast("Script added")
            }.onFailure { toast("Failed: ${it.message}") }
        }
    }

    private fun importScriptFromFile(uri: Uri) {
        try {
            val code = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            val fileName = uri.lastPathSegment ?: "script.js"
            val meta = Regex("""//\s*@name\s+(.+)""").find(code)?.groupValues?.getOrNull(1)?.trim()
            val script = UserScript(meta ?: fileName, "local file: $fileName", code)
            val list = loadScripts().also { it.add(script) }
            saveScripts(list); toast("Script added")
        } catch (e: Exception) { toast("Failed to read file: ${e.message}") }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ── Shared Compose primitives ─────────────────────────────────────────────

    @Composable
    private fun SettingsScaffold(
        title: String,
        actions: @Composable RowScope.() -> Unit = {},
        content: @Composable ColumnScope.() -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = actions
                )
            }
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item { Column { content() } }
            }
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
        )
    }

    @Composable
    private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), content = content)
        }
    }

    @Composable
    private fun SwitchRow(
        title: String,
        subtitle: String = "",
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    @Composable
    private fun NavRow(
        title: String,
        subtitle: String = "",
        icon: (@Composable () -> Unit)? = null,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) { Box(modifier = Modifier.padding(end = 12.dp)) { icon() } }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}
