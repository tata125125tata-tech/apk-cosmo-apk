package com.cosmogamestore.app.ui.updates;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.cosmogamestore.app.R;
import com.cosmogamestore.app.installer.ApkParser;
import com.cosmogamestore.app.installer.InstallDialogHelper;
import com.cosmogamestore.app.installer.PackageAdapter;
import com.cosmogamestore.app.installer.PackageModel;
import com.cosmogamestore.app.installer.XapkParser;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native Material Design Updates & Notifications Fragment written in Java.
 * Checks for updates to installed apps and available APK/XAPK package updates.
 */
public class UpdatesFragment extends Fragment {

    private TextView tvLastChecked;
    private Button btnCheckUpdates;
    private ProgressBar pbLoading;
    private TextView tvSectionTitle;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvUpdates;
    private View emptyView;

    private PackageAdapter adapter;
    private final List<PackageModel> appList = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static UpdatesFragment newInstance() {
        return new UpdatesFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_updates, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        setupListeners();
        scanAndCheckUpdates(false);
    }

    private void initViews(View root) {
        tvLastChecked = root.findViewById(R.id.tv_updates_last_checked);
        btnCheckUpdates = root.findViewById(R.id.btn_check_updates);
        pbLoading = root.findViewById(R.id.pb_updates_loading);
        tvSectionTitle = root.findViewById(R.id.tv_updates_section_title);
        swipeRefresh = root.findViewById(R.id.swipe_refresh_updates);
        rvUpdates = root.findViewById(R.id.rv_updates_list);
        emptyView = root.findViewById(R.id.updates_empty_view);

        swipeRefresh.setColorSchemeResources(R.color.secondary, R.color.primary);
    }

    private void setupRecyclerView() {
        adapter = new PackageAdapter(requireContext(), new PackageAdapter.OnPackageActionListener() {
            @Override
            public void onPrimaryAction(PackageModel model) {
                if (model.getType() == PackageModel.Type.INSTALLED_APP) {
                    PackageManager pm = requireContext().getPackageManager();
                    Intent launchIntent = pm.getLaunchIntentForPackage(model.getPackageName());
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    } else {
                        Toast.makeText(requireContext(), "Opening " + model.getTitle(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    InstallDialogHelper.showInstallDialog(requireActivity(), model, () -> scanAndCheckUpdates(false));
                }
            }

            @Override
            public void onSecondaryAction(PackageModel model) {
                if (model.getType() == PackageModel.Type.INSTALLED_APP) {
                    Toast.makeText(requireContext(), model.getTitle() + " is up to date.", Toast.LENGTH_SHORT).show();
                } else {
                    InstallDialogHelper.showInstallDialog(requireActivity(), model, () -> scanAndCheckUpdates(false));
                }
            }

            @Override
            public void onItemClick(PackageModel model) {
                if (model.getType() == PackageModel.Type.INSTALLED_APP) {
                    PackageManager pm = requireContext().getPackageManager();
                    Intent launchIntent = pm.getLaunchIntentForPackage(model.getPackageName());
                    if (launchIntent != null) startActivity(launchIntent);
                } else {
                    InstallDialogHelper.showInstallDialog(requireActivity(), model, () -> scanAndCheckUpdates(false));
                }
            }
        });

        rvUpdates.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvUpdates.setAdapter(adapter);
    }

    private void setupListeners() {
        btnCheckUpdates.setOnClickListener(v -> scanAndCheckUpdates(true));
        swipeRefresh.setOnRefreshListener(() -> scanAndCheckUpdates(true));
    }

    private void scanAndCheckUpdates(boolean isManual) {
        Context context = getContext();
        if (context == null) return;

        pbLoading.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            List<PackageModel> combined = new ArrayList<>();

            // 1. Scan downloaded package files (.apk / .xapk) in downloads
            Map<String, PackageModel> pendingPackageUpdates = new HashMap<>();
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            scanDirForUpdates(context, downloadsDir, pendingPackageUpdates);
            File appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            scanDirForUpdates(context, appDownloads, pendingPackageUpdates);

            // 2. Scan installed apps
            PackageManager pm = context.getPackageManager();
            List<PackageInfo> installed = pm.getInstalledPackages(0);

            int updatesCount = 0;
            for (PackageInfo info : installed) {
                boolean isSystem = (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean hasLaunch = pm.getLaunchIntentForPackage(info.packageName) != null;

                if (!isSystem || hasLaunch) {
                    PackageModel installedModel = ApkParser.parseInstalledApp(context, info);
                    if (installedModel != null) {
                        // Check if we have an installer file with newer version
                        PackageModel packageMatch = pendingPackageUpdates.get(installedModel.getPackageName());
                        if (packageMatch != null && packageMatch.getVersionCode() > installedModel.getVersionCode()) {
                            packageMatch.setInstalled(true);
                            packageMatch.setInstalledVersionName(installedModel.getVersionName());
                            combined.add(0, packageMatch);
                            updatesCount++;
                        } else {
                            combined.add(installedModel);
                        }
                    }
                }
            }

            final int finalUpdatesCount = updatesCount;
            final String timeString = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());

            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                pbLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                tvLastChecked.setText("Last checked: " + timeString);

                appList.clear();
                appList.addAll(combined);
                adapter.setItems(appList);

                if (finalUpdatesCount > 0) {
                    tvSectionTitle.setText("Updates Available (" + finalUpdatesCount + ")");
                } else {
                    tvSectionTitle.setText("All Installed Apps (" + appList.size() + ") • Up to date");
                }

                if (appList.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    rvUpdates.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    rvUpdates.setVisibility(View.VISIBLE);
                }

                if (isManual) {
                    if (finalUpdatesCount > 0) {
                        Toast.makeText(context, finalUpdatesCount + " package update(s) ready to install!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "All installed apps are up to date.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
    }

    private void scanDirForUpdates(Context context, File dir, Map<String, PackageModel> output) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".apk") || lower.endsWith(".xapk");
        });

        if (files != null) {
            for (File file : files) {
                PackageModel model = null;
                if (file.getName().toLowerCase().endsWith(".xapk")) {
                    model = XapkParser.parse(context, file);
                } else if (file.getName().toLowerCase().endsWith(".apk")) {
                    model = ApkParser.parseApk(context, file);
                }

                if (model != null && !model.getPackageName().isEmpty()) {
                    PackageModel existing = output.get(model.getPackageName());
                    if (existing == null || model.getVersionCode() > existing.getVersionCode()) {
                        output.put(model.getPackageName(), model);
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
