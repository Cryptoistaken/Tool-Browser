package com.personal.browser.ui.activity

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.personal.browser.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mode = intent.getStringExtra("SETTINGS_MODE") ?: "GENERAL"

        setupMode(mode)

        binding.btnBackSettings.setOnClickListener {
            finish()
        }
    }

    private fun setupMode(mode: String) {
        when (mode) {
            "AD_BLOCKING" -> {
                binding.tvSettingsTitle.text = "Ad blocking"
                binding.adBlockingContainer.visibility = View.VISIBLE
                binding.scriptsContainer.visibility = View.GONE
                binding.btnSettingsAdd.visibility = View.GONE
            }
            "SCRIPTS" -> {
                binding.tvSettingsTitle.text = "Scripts"
                binding.adBlockingContainer.visibility = View.GONE
                binding.scriptsContainer.visibility = View.VISIBLE
                binding.btnSettingsAdd.visibility = View.VISIBLE
            }
            else -> {
                binding.tvSettingsTitle.text = "Settings"
                binding.adBlockingContainer.visibility = View.GONE
                binding.scriptsContainer.visibility = View.GONE
                binding.btnSettingsAdd.visibility = View.GONE
            }
        }
    }
}
