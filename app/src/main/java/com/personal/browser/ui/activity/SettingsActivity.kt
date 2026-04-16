package com.personal.browser.ui.activity

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.personal.browser.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREF_AD_BLOCKING   = "pref_ad_blocking"
        const val PREF_BUILT_FILTERS = "pref_built_in_filters"
        const val PREF_EXPAND_CONTENT = "pref_expand_content"
        const val PREF_SCRIPTS_ENABLED = "pref_scripts_enabled"
        const val PREF_SCRIPT_IMMERSIVE = "pref_script_immersive"
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
                binding.adBlockingContainer.visibility = View.VISIBLE
                binding.scriptsContainer.visibility = View.GONE
                binding.btnSettingsAdd.visibility = View.GONE
                setupAdBlockingSwitches()
            }
            "SCRIPTS" -> {
                binding.tvSettingsTitle.text = "Scripts"
                binding.adBlockingContainer.visibility = View.GONE
                binding.scriptsContainer.visibility = View.VISIBLE
                binding.btnSettingsAdd.visibility = View.VISIBLE
                setupScriptsSwitches()
            }
            else -> {
                binding.tvSettingsTitle.text = "Settings"
                binding.adBlockingContainer.visibility = View.GONE
                binding.scriptsContainer.visibility = View.GONE
                binding.btnSettingsAdd.visibility = View.GONE
            }
        }
    }

    private fun setupAdBlockingSwitches() {
        // Restore saved state
        binding.switchAdBlocking.isChecked = prefs.getBoolean(PREF_AD_BLOCKING, true)
        binding.switchFilters.isChecked    = prefs.getBoolean(PREF_BUILT_FILTERS, true)
        binding.switchExpand.isChecked     = prefs.getBoolean(PREF_EXPAND_CONTENT, true)

        // Save on change
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

    private fun setupScriptsSwitches() {
        binding.switchEnableScripts.isChecked    = prefs.getBoolean(PREF_SCRIPTS_ENABLED, true)
        binding.switchScriptImmersive.isChecked  = prefs.getBoolean(PREF_SCRIPT_IMMERSIVE, true)

        binding.switchEnableScripts.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_SCRIPTS_ENABLED, checked).apply()
        }
        binding.switchScriptImmersive.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_SCRIPT_IMMERSIVE, checked).apply()
        }
    }
}
