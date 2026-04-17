package com.personal.browser.ui.activity

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.personal.browser.R
import com.personal.browser.databinding.ActivitySettingsBinding
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    // File picker for local-file script import
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) importScriptFromFile(uri)
    }

    companion object {
        const val PREF_AD_BLOCKING      = "pref_ad_blocking"
        const val PREF_BUILT_FILTERS    = "pref_built_in_filters"
        const val PREF_EXPAND_CONTENT   = "pref_expand_content"
        const val PREF_SCRIPTS_ENABLED  = "pref_scripts_enabled"
        const val PREF_HOMEPAGE_URL     = "pref_homepage_url"
        const val PREF_CLEAR_ON_EXIT    = "pref_clear_on_exit"
        const val PREF_DARK_MODE        = "pref_dark_mode"
        const val PREF_SCRIPTS_JSON     = "pref_scripts_json"
        const val DEFAULT_HOMEPAGE      = "https://www.google.com"

        /** Preset homepage URLs shown in the spinner (plus "Custom…" at the end). */
        val HOMEPAGE_PRESETS = listOf(
            "https://www.google.com",
            "https://www.instagram.com/accounts/login",
            "https://m.facebook.com/"
        )
        private const val CUSTOM_LABEL = "Custom…"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("browser_prefs", MODE_PRIVATE)

        val mode = intent.getStringExtra("SETTINGS_MODE") ?: "GENERAL"
        setupMode(mode)

        binding.btnBackSettings.setOnClickListener { finish() }
    }

    // ── Route to the correct panel ────────────────────────────────────────────

    private fun setupMode(mode: String) {
        when (mode) {
            "AD_BLOCKING" -> {
                binding.tvSettingsTitle.text = "Ad Blocking"
                binding.generalContainer.visibility    = View.GONE
                binding.adBlockingContainer.visibility = View.VISIBLE
                binding.scriptsContainer.visibility    = View.GONE
                binding.btnSettingsAdd.visibility      = View.GONE
                setupAdBlockingSwitches()
            }
            "SCRIPTS" -> {
                binding.tvSettingsTitle.text = "User Scripts"
                binding.generalContainer.visibility    = View.GONE
                binding.adBlockingContainer.visibility = View.GONE
                binding.scriptsContainer.visibility    = View.VISIBLE
                binding.btnSettingsAdd.visibility      = View.VISIBLE
                setupScriptsPanel()
            }
            else -> { // "GENERAL"
                binding.tvSettingsTitle.text = "Settings"
                binding.generalContainer.visibility    = View.VISIBLE
                binding.adBlockingContainer.visibility = View.GONE
                binding.scriptsContainer.visibility    = View.GONE
                binding.btnSettingsAdd.visibility      = View.GONE
                setupGeneralSettings()
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // GENERAL SETTINGS
    // ────────────────────────────────────────────────────────────────────────

    private fun setupGeneralSettings() {

        // ── Homepage spinner ──────────────────────────────────────────────
        val currentUrl = prefs.getString(PREF_HOMEPAGE_URL, DEFAULT_HOMEPAGE) ?: DEFAULT_HOMEPAGE

        val spinnerItems = HOMEPAGE_PRESETS.toMutableList().also { it.add(CUSTOM_LABEL) }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerItems)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerHomepage.adapter = spinnerAdapter

        // Pre-select matching preset or "Custom…"
        val presetIndex = HOMEPAGE_PRESETS.indexOf(currentUrl)
        if (presetIndex >= 0) {
            binding.spinnerHomepage.setSelection(presetIndex)
            binding.tilCustomHomepage.visibility = View.GONE
        } else {
            binding.spinnerHomepage.setSelection(spinnerItems.size - 1)
            binding.tilCustomHomepage.visibility = View.VISIBLE
            binding.etHomepageUrl.setText(currentUrl)
        }

        binding.spinnerHomepage.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>, view: View?, pos: Int, id: Long
                ) {
                    if (pos == HOMEPAGE_PRESETS.size) {
                        // "Custom…"
                        binding.tilCustomHomepage.visibility = View.VISIBLE
                    } else {
                        binding.tilCustomHomepage.visibility = View.GONE
                        prefs.edit().putString(PREF_HOMEPAGE_URL, HOMEPAGE_PRESETS[pos]).apply()
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }

        // Custom URL field
        binding.etHomepageUrl.setOnEditorActionListener { tv, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveCustomHomepage(tv.text.toString().trim())
                true
            } else false
        }
        binding.etHomepageUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveCustomHomepage(binding.etHomepageUrl.text.toString().trim())
        }

        // ── Dark mode switch ──────────────────────────────────────────────
        val isDark = prefs.getBoolean(PREF_DARK_MODE, false)
        binding.switchDarkMode.isChecked = isDark
        binding.switchDarkMode.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_DARK_MODE, checked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // ── Clear on exit switch ──────────────────────────────────────────
        binding.switchClearOnExit.isChecked = prefs.getBoolean(PREF_CLEAR_ON_EXIT, false)
        binding.switchClearOnExit.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_CLEAR_ON_EXIT, checked).apply()
        }

        // ── Clear data now ────────────────────────────────────────────────
        binding.btnClearDataNow.setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra("ACTION", "CLEAR_DATA"))
            finish()
        }

        // ── Navigate to sub-screens ───────────────────────────────────────
        binding.btnGoAdBlocking.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java)
                .putExtra("SETTINGS_MODE", "AD_BLOCKING"))
        }
        binding.btnGoUserScripts.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java)
                .putExtra("SETTINGS_MODE", "SCRIPTS"))
        }
    }

    private fun saveCustomHomepage(url: String) {
        val finalUrl = when {
            url.isEmpty() -> DEFAULT_HOMEPAGE
            url.startsWith("http://") || url.startsWith("https://") -> url
            else -> "https://$url"
        }
        prefs.edit().putString(PREF_HOMEPAGE_URL, finalUrl).apply()
        binding.etHomepageUrl.setText(finalUrl)
    }

    // ────────────────────────────────────────────────────────────────────────
    // AD BLOCKING
    // ────────────────────────────────────────────────────────────────────────

    private fun setupAdBlockingSwitches() {
        binding.switchAdBlocking.isChecked = prefs.getBoolean(PREF_AD_BLOCKING, true)
        binding.switchFilters.isChecked    = prefs.getBoolean(PREF_BUILT_FILTERS, true)
        binding.switchExpand.isChecked     = prefs.getBoolean(PREF_EXPAND_CONTENT, true)

        binding.switchAdBlocking.setOnCheckedChangeListener { _, c ->
            prefs.edit().putBoolean(PREF_AD_BLOCKING, c).apply()
        }
        binding.switchFilters.setOnCheckedChangeListener { _, c ->
            prefs.edit().putBoolean(PREF_BUILT_FILTERS, c).apply()
        }
        binding.switchExpand.setOnCheckedChangeListener { _, c ->
            prefs.edit().putBoolean(PREF_EXPAND_CONTENT, c).apply()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // USER SCRIPTS
    // ────────────────────────────────────────────────────────────────────────

    private fun setupScriptsPanel() {
        // Master enable switch
        binding.switchEnableScripts.isChecked = prefs.getBoolean(PREF_SCRIPTS_ENABLED, true)
        binding.switchEnableScripts.setOnCheckedChangeListener { _, c ->
            prefs.edit().putBoolean(PREF_SCRIPTS_ENABLED, c).apply()
        }

        // Render current scripts list
        refreshScriptsList()

        // + button → show picker dialog
        binding.btnSettingsAdd.setOnClickListener {
            showAddScriptDialog()
        }
    }

    /** Read scripts from prefs and rebuild the dynamic list UI. */
    private fun refreshScriptsList() {
        val container = binding.scriptsListContainer
        container.removeAllViews()
        val scripts = loadScripts()

        if (scripts.isEmpty()) {
            binding.tvNoScripts.visibility = View.VISIBLE
            return
        }
        binding.tvNoScripts.visibility = View.GONE

        scripts.forEachIndexed { index, script ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_script, container, false)
            row.findViewById<TextView>(R.id.tvScriptName).text = script.name
            row.findViewById<TextView>(R.id.tvScriptSource).text = script.source

            val sw = row.findViewById<SwitchMaterial>(R.id.switchScript)
            sw.isChecked = script.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                script.enabled = checked
                saveScripts(scripts)
            }

            row.findViewById<View>(R.id.btnDeleteScript).setOnClickListener {
                scripts.removeAt(index)
                saveScripts(scripts)
                refreshScriptsList()
                Toast.makeText(this, R.string.script_deleted, Toast.LENGTH_SHORT).show()
            }
            container.addView(row)
        }
    }

    /** Show a dialog: choose source type (URL / File / Text). */
    private fun showAddScriptDialog() {
        val options = arrayOf(
            getString(R.string.script_from_url),
            getString(R.string.script_from_file),
            getString(R.string.script_from_text)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.add_script))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showUrlInputDialog()
                    1 -> filePickerLauncher.launch("text/*")
                    2 -> showTextInputDialog()
                }
            }
            .show()
    }

    /** Dialog: enter a URL to a remote .user.js file. */
    private fun showUrlInputDialog() {
        val input = EditText(this).apply {
            hint = "https://example.com/script.user.js"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.script_from_url))
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    addScript(UserScript(name = url.substringAfterLast("/"), source = url, code = "/* from URL */"))
                } else {
                    Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Dialog: type or paste raw JS. */
    private fun showTextInputDialog() {
        val nameInput = EditText(this).apply { hint = "Script name" }
        val codeInput = EditText(this).apply {
            hint = "// JavaScript code here…"
            minLines = 5
            maxLines = 12
            gravity = android.view.Gravity.TOP
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(nameInput)
            addView(codeInput)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.script_from_text))
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim().ifEmpty { "Custom Script" }
                val code = codeInput.text.toString().trim()
                if (code.isNotEmpty()) {
                    addScript(UserScript(name = name, source = "text input", code = code))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Read file content from URI and add as script. */
    private fun importScriptFromFile(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            val fileName = uri.lastPathSegment ?: "script.js"
            addScript(UserScript(name = fileName, source = "local file", code = content))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addScript(script: UserScript) {
        val scripts = loadScripts()
        scripts.add(script)
        saveScripts(scripts)
        refreshScriptsList()
        Toast.makeText(this, R.string.script_added, Toast.LENGTH_SHORT).show()
    }

    // ── Script persistence ────────────────────────────────────────────────────

    data class UserScript(
        var name: String,
        var source: String,
        var code: String,
        var enabled: Boolean = true
    )

    private fun loadScripts(): MutableList<UserScript> {
        val json = prefs.getString(PREF_SCRIPTS_JSON, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                UserScript(
                    name    = obj.optString("name", "Script"),
                    source  = obj.optString("source", ""),
                    code    = obj.optString("code", ""),
                    enabled = obj.optBoolean("enabled", true)
                )
            }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    private fun saveScripts(scripts: List<UserScript>) {
        val arr = JSONArray()
        scripts.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("source", s.source)
                put("code", s.code)
                put("enabled", s.enabled)
            })
        }
        prefs.edit().putString(PREF_SCRIPTS_JSON, arr.toString()).apply()
    }
}
