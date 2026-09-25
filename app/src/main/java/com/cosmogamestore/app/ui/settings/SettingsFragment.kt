package com.cosmogamestore.app.ui.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.cosmogamestore.app.R
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * SettingsFragment:
 * Handles user preferences:
 * 1. Dark / Light Mode Switch with AppCompatDelegate persistence
 * 2. Download over Wi-Fi only toggle
 * 3. Auto-download updates toggle
 * 4. Background active execution toggle
 * 5. Unknown app install permissions and storage cleaning
 */
class SettingsFragment : Fragment() {

    companion object {
        const val PREFS_NAME = "cosmo_store_prefs"
        const val KEY_DARK_MODE = "key_dark_mode"
        const val KEY_WIFI_ONLY = "key_wifi_only"
        const val KEY_AUTO_DOWNLOAD_UPDATES = "key_auto_download_updates"
        const val KEY_ALLOW_BACKGROUND_ACTIVE = "key_allow_background_active"
        const val KEY_AUTO_INSTALL = "key_auto_install"

        fun newInstance() = SettingsFragment()
    }

    private lateinit var prefs: SharedPreferences

    // Switches
    private lateinit var switchDarkMode: MaterialSwitch
    private lateinit var switchWifiOnly: MaterialSwitch
    private lateinit var switchAutoDownloadUpdates: MaterialSwitch
    private lateinit var switchBackgroundActive: MaterialSwitch
    private lateinit var switchAutoInstall: MaterialSwitch

    // Status Views & Buttons
    private lateinit var tvThemeModeDesc: TextView
    private lateinit var tvPermissionBadge: TextView
    private lateinit var tvPermissionDesc: TextView
    private lateinit var btnGrantInstallPermission: Button
    private lateinit var tvDownloadDir: TextView
    private lateinit var btnClearCache: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        initViews(view)
        loadPreferences()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatusUI()
    }

    private fun initViews(view: View) {
        switchDarkMode = view.findViewById(R.id.switch_dark_mode)
        switchWifiOnly = view.findViewById(R.id.switch_wifi_only)
        switchAutoDownloadUpdates = view.findViewById(R.id.switch_auto_download_updates)
        switchBackgroundActive = view.findViewById(R.id.switch_background_active)
        switchAutoInstall = view.findViewById(R.id.switch_auto_install)

        tvThemeModeDesc = view.findViewById(R.id.tv_theme_mode_desc)
        tvPermissionBadge = view.findViewById(R.id.tv_permission_badge)
        tvPermissionDesc = view.findViewById(R.id.tv_permission_desc)
        btnGrantInstallPermission = view.findViewById(R.id.btn_grant_install_permission)
        tvDownloadDir = view.findViewById(R.id.tv_download_dir)
        btnClearCache = view.findViewById(R.id.btn_clear_cache)

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        tvDownloadDir.text = downloadsDir?.absolutePath ?: "/storage/emulated/0/Download"
    }

    private fun loadPreferences() {
        // 1. Dark Mode: Defaults to true (dark theme)
        val isDarkMode = prefs.getBoolean(KEY_DARK_MODE, true)
        switchDarkMode.isChecked = isDarkMode
        tvThemeModeDesc.text = if (isDarkMode) "Dark theme enabled" else "Light theme enabled"

        // 2. Wi-Fi Only: Defaults to false
        switchWifiOnly.isChecked = prefs.getBoolean(KEY_WIFI_ONLY, false)

        // 3. Auto Download Updates: Defaults to true
        switchAutoDownloadUpdates.isChecked = prefs.getBoolean(KEY_AUTO_DOWNLOAD_UPDATES, true)

        // 4. Background Active: Defaults to true
        switchBackgroundActive.isChecked = prefs.getBoolean(KEY_ALLOW_BACKGROUND_ACTIVE, true)

        // 5. Auto Install: Defaults to true
        switchAutoInstall.isChecked = prefs.getBoolean(KEY_AUTO_INSTALL, true)
    }

    private fun setupListeners() {
        // Dark / Light Mode Switch
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            tvThemeModeDesc.text = if (isChecked) "Dark theme enabled" else "Light theme enabled"

            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        // Network: Wi-Fi Only Switch
        switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_WIFI_ONLY, isChecked).apply()
            val msg = if (isChecked) "Downloads restricted to Wi-Fi only" else "Downloads allowed over any network"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // Auto Download Updates Switch
        switchAutoDownloadUpdates.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_UPDATES, isChecked).apply()
            val msg = if (isChecked) "Auto-download updates active" else "Auto-download updates turned off"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // Display / Background Active Switch
        switchBackgroundActive.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_ALLOW_BACKGROUND_ACTIVE, isChecked).apply()
            val msg = if (isChecked) "Background downloads kept active" else "Background activity limited"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // Auto-install after download
        switchAutoInstall.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_INSTALL, isChecked).apply()
        }

        // Manage Install Permission Button
        btnGrantInstallPermission.setOnClickListener {
            openInstallPermissionSettings()
        }

        // Clear Browser Cache and Cookies
        btnClearCache.setOnClickListener {
            try {
                WebView(requireContext()).clearCache(true)
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                Toast.makeText(requireContext(), "Cache and session cookies cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Cache cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isInstallPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun updatePermissionStatusUI() {
        if (!isAdded) return

        if (isInstallPermissionGranted()) {
            tvPermissionBadge.text = "ALLOWED"
            tvPermissionBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green))
            btnGrantInstallPermission.text = "Install Permission Active"
            btnGrantInstallPermission.isEnabled = false
        } else {
            tvPermissionBadge.text = "ACTION REQUIRED"
            tvPermissionBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_rose))
            btnGrantInstallPermission.text = "Grant Unknown Apps Permission"
            btnGrantInstallPermission.isEnabled = true
        }
    }

    private fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
        }
    }
}
