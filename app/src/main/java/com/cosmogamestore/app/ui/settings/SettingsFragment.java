package com.cosmogamestore.app.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.cosmogamestore.app.R;
import com.cosmogamestore.app.installer.PackageInstallerHelper;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;

/**
 * SettingsFragment written in Java.
 * Handles app preferences, themes, unknown source permissions, and starting page selection.
 */
public class SettingsFragment extends Fragment {

    public static final String PREFS_NAME = "cosmo_store_prefs";
    public static final String KEY_DARK_MODE = "key_dark_mode";
    public static final String KEY_WIFI_ONLY = "key_wifi_only";
    public static final String KEY_AUTO_DOWNLOAD_UPDATES = "key_auto_download_updates";
    public static final String KEY_ALLOW_BACKGROUND_ACTIVE = "key_allow_background_active";
    public static final String KEY_AUTO_INSTALL = "key_auto_install";
    public static final String KEY_START_PAGE = "key_start_page";

    public static final int START_PAGE_BROWSE = 0;
    public static final int START_PAGE_LIBRARY = 1;
    public static final int START_PAGE_UPDATES = 2;

    private SharedPreferences prefs;

    // Switches
    private MaterialSwitch switchDarkMode;
    private MaterialSwitch switchWifiOnly;
    private MaterialSwitch switchAutoDownloadUpdates;
    private MaterialSwitch switchBackgroundActive;
    private MaterialSwitch switchAutoInstall;

    // Status views & buttons
    private TextView tvThemeModeDesc;
    private View btnSettingStartPage;
    private TextView tvStartPageVal;
    private TextView tvPermissionBadge;
    private TextView tvPermissionDesc;
    private Button btnGrantInstallPermission;
    private TextView tvDownloadDir;
    private Button btnClearCache;

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        initViews(view);
        loadPreferences();
        setupListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionStatusUI();
    }

    private void initViews(View view) {
        switchDarkMode = view.findViewById(R.id.switch_dark_mode);
        switchWifiOnly = view.findViewById(R.id.switch_wifi_only);
        switchAutoDownloadUpdates = view.findViewById(R.id.switch_auto_download_updates);
        switchBackgroundActive = view.findViewById(R.id.switch_background_active);
        switchAutoInstall = view.findViewById(R.id.switch_auto_install);

        tvThemeModeDesc = view.findViewById(R.id.tv_theme_mode_desc);
        btnSettingStartPage = view.findViewById(R.id.btn_setting_start_page);
        tvStartPageVal = view.findViewById(R.id.tv_start_page_val);
        tvPermissionBadge = view.findViewById(R.id.tv_permission_badge);
        tvPermissionDesc = view.findViewById(R.id.tv_permission_desc);
        btnGrantInstallPermission = view.findViewById(R.id.btn_grant_install_permission);
        tvDownloadDir = view.findViewById(R.id.tv_download_dir);
        btnClearCache = view.findViewById(R.id.btn_clear_cache);

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir != null && tvDownloadDir != null) {
            tvDownloadDir.setText(downloadsDir.getAbsolutePath());
        }
    }

    private void loadPreferences() {
        boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, true);
        switchDarkMode.setChecked(isDarkMode);
        tvThemeModeDesc.setText(isDarkMode ? "Enabled • Deep space dark interface" : "Disabled • Light interface");

        switchWifiOnly.setChecked(prefs.getBoolean(KEY_WIFI_ONLY, false));
        switchAutoDownloadUpdates.setChecked(prefs.getBoolean(KEY_AUTO_DOWNLOAD_UPDATES, false));
        switchBackgroundActive.setChecked(prefs.getBoolean(KEY_ALLOW_BACKGROUND_ACTIVE, true));
        switchAutoInstall.setChecked(prefs.getBoolean(KEY_AUTO_INSTALL, true));

        int startPage = prefs.getInt(KEY_START_PAGE, START_PAGE_BROWSE);
        updateStartPageLabel(startPage);

        updatePermissionStatusUI();
    }

    private void setupListeners() {
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            tvThemeModeDesc.setText(isChecked ? "Enabled • Deep space dark interface" : "Disabled • Light interface");
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
        });

        switchWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_WIFI_ONLY, isChecked).apply();
            Toast.makeText(requireContext(), isChecked ? "Downloads restricted to Wi-Fi" : "Downloads allowed on any network", Toast.LENGTH_SHORT).show();
        });

        switchAutoDownloadUpdates.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_UPDATES, isChecked).apply();
        });

        switchBackgroundActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_ALLOW_BACKGROUND_ACTIVE, isChecked).apply();
        });

        switchAutoInstall.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_AUTO_INSTALL, isChecked).apply();
        });

        if (btnSettingStartPage != null) {
            btnSettingStartPage.setOnClickListener(v -> showStartPageDialog());
        }

        btnGrantInstallPermission.setOnClickListener(v -> {
            PackageInstallerHelper.promptUnknownSourcesPermission(requireActivity());
        });

        btnClearCache.setOnClickListener(v -> clearAppCache());
    }

    private void showStartPageDialog() {
        final String[] options = new String[]{"Browse (Game Store)", "My Library", "Updates"};
        int currentSelection = prefs.getInt(KEY_START_PAGE, START_PAGE_BROWSE);

        new AlertDialog.Builder(requireContext())
                .setTitle("Select Default Starting Page")
                .setSingleChoiceItems(options, currentSelection, (dialog, which) -> {
                    prefs.edit().putInt(KEY_START_PAGE, which).apply();
                    updateStartPageLabel(which);
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "Default page set to " + options[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateStartPageLabel(int page) {
        if (tvStartPageVal == null) return;
        switch (page) {
            case START_PAGE_LIBRARY:
                tvStartPageVal.setText("My Library");
                break;
            case START_PAGE_UPDATES:
                tvStartPageVal.setText("Updates");
                break;
            case START_PAGE_BROWSE:
            default:
                tvStartPageVal.setText("Browse (Game Store)");
                break;
        }
    }

    private void updatePermissionStatusUI() {
        Context context = getContext();
        if (context == null) return;

        boolean canInstall = PackageInstallerHelper.canRequestPackageInstalls(context);
        if (canInstall) {
            tvPermissionBadge.setText("GRANTED");
            tvPermissionBadge.setTextColor(ContextCompat.getColor(context, R.color.secondary));
            tvPermissionBadge.setBackgroundResource(R.drawable.bg_badge);
            tvPermissionDesc.setText("Cosmo is authorized to install APK and XAPK packages.");
            btnGrantInstallPermission.setVisibility(View.GONE);
        } else {
            tvPermissionBadge.setText("REQUIRED");
            tvPermissionBadge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            tvPermissionBadge.setBackgroundResource(0);
            tvPermissionDesc.setText("To install games directly from the app, please grant the 'Install Unknown Apps' system permission.");
            btnGrantInstallPermission.setVisibility(View.VISIBLE);
        }
    }

    private void clearAppCache() {
        Context context = getContext();
        if (context == null) return;

        try {
            WebView tempWebView = new WebView(context);
            tempWebView.clearCache(true);
            CookieManager.getInstance().removeAllCookies(null);

            File cacheDir = context.getCacheDir();
            deleteDir(cacheDir);

            Toast.makeText(context, "Temporary cache cleared successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Error clearing cache: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        }
        return false;
    }
}
