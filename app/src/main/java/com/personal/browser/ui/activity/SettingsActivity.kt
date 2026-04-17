package com.personal.browser.ui.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.personal.browser.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREF_AD_BLOCKING    = "pref_ad_blocking"
        const val PREF_BUILT_FILTERS  = "pref_built_in_filters"
        const val PREF_EXPAND_CONTENT = "pref_expand_content"
        const val PREF_SCRIPTS_ENABLED    = "pref_scripts_enabled"
        const val PREF_SCRIPT_IMMERSIVE   = "pref_script_immersive"
        const val PREF_HOMEPAGE_URL       = "pref_homepage_url"
        const val PREF_CLEAR_ON_EXIT      = "pref_clear_on_exit"
        const val DEFAULT_HOMEPAGE        = "https://www.google.com"
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

    private fun setupMode(mode: String) {
        when (mode) {
            "AD_BLOCKING" -> {
                binding.tvSettingsTitle.text = "Ad blocking"
                binding.generalContainer.visibility    = View.GONE
                binding.adBlockingContainer.visibility = View.VISIBLE
                binding.scriptsContainer.visibility    = View.GONE
                binding.btnSettingsAdd.visibility      = View.GONE
                setupAdBlockingSwitches()
            }
            "SCRIPTS" -> {
                binding.tvSettingsTitle.text = "Scripts"
                binding.generalContainer.visibility    = View.GONE
                binding.adBlockingContainer.visibility = View.GONE
                binding.scriptsContainer.visibility    = View.VISIBLE
                binding.btnSettingsAdd.visibility      = View.VISIBLE
                setupScriptsSwitches()
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

    // ── General Settings ──────────────────────────────────────────────────────

    private fun setupGeneralSettings() {
        // Homepage URL
        val savedUrl = prefs.getString(PREF_HOMEPAGE_URL, DEFAULT_HOMEPAGE) ?: DEFAULT_HOMEPAGE
        binding.etHomepageUrl.setText(savedUrl)

        binding.etHomepageUrl.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveHomepageUrl(textView.text.toString().trim())
                true
            } else false
        }

        binding.etHomepageUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveHomepageUrl(binding.etHomepageUrl.text.toString().trim())
            }
        }

        // Clear on exit toggle
        binding.switchClearOnExit.isChecked = prefs.getBoolean(PREF_CLEAR_ON_EXIT, false)
        binding.switchClearOnExit.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_CLEAR_ON_EXIT, checked).apply()
        }

        // Clear data now
        binding.btnClearDataNow.setOnClickListener {
            setResult(RESULT_OK, android.content.Intent().putExtra("ACTION", "CLEAR_DATA"))
            finish()
        }

        // Navigate to Ad Blocking
        binding.btnGoAdBlocking.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java)
                .putExtra("SETTINGS_MODE", "AD_BLOCKING"))
        }

        // Navigate to User Scripts
        binding.btnGoUserScripts.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java)
                .putExtra("SETTINGS_MODE", "SCRIPTS"))
        }
    }

    private fun saveHomepageUrl(url: String) {
        val finalUrl = when {
            url.isEmpty()         -> DEFAULT_HOMEPAGE
            url.startsWith("http://") || url.startsWith("https://") -> url
            else                  -> "https://$url"
        }
        prefs.edit().putString(PREF_HOMEPAGE_URL, finalUrl).apply()
        binding.etHomepageUrl.setText(finalUrl)
    }

    // ── Ad Blocking ───────────────────────────────────────────────────────────

    private fun setupAdBlockingSwitches() {
        binding.switchAdBlocking.isChecked = prefs.getBoolean(PREF_AD_BLOCKING, true)
        binding.switchFilters.isChecked    = prefs.getBoolean(PREF_BUILT_FILTERS, true)
        binding.switchExpand.isChecked     = prefs.getBoolean(PREF_EXPAND_CONTENT, true)

        binding.switchAdBlocking.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_AD_BLOCKING, checked).apply()
        }
        binding.switchFilters.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_BUILT_FILTERS, checked).apply()
        }
        binding.switchExpand.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_EXPAND_CONTENT, checked).apply()
        }
    }

    // ── User Scripts ──────────────────────────────────────────────────────────

    private fun setupScriptsSwitches() {
        binding.switchEnableScripts.isChecked   = prefs.getBoolean(PREF_SCRIPTS_ENABLED, true)
        binding.switchScriptImmersive.isChecked = prefs.getBoolean(PREF_SCRIPT_IMMERSIVE, true)

        binding.switchEnableScripts.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_SCRIPTS_ENABLED, checked).apply()
        }
        binding.switchScriptImmersive.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_SCRIPT_IMMERSIVE, checked).apply()
        }
    }
}
