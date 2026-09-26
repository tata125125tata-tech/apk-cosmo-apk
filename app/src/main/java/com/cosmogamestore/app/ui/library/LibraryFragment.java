package com.cosmogamestore.app.ui.library;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.cosmogamestore.app.MainActivity;
import com.cosmogamestore.app.R;
import com.cosmogamestore.app.installer.ApkParser;
import com.cosmogamestore.app.installer.InstallDialogHelper;
import com.cosmogamestore.app.installer.PackageAdapter;
import com.cosmogamestore.app.installer.PackageModel;
import com.cosmogamestore.app.installer.XapkParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native Material Design Library Fragment written in Java.
 * Manages downloaded APK and XAPK packages and installed apps on the device.
 */
public class LibraryFragment extends Fragment {

    private static final int SUBTAB_PACKAGES = 0;
    private static final int SUBTAB_INSTALLED = 1;

    private int currentSubTab = SUBTAB_PACKAGES;

    private TextView tabDownloadedPackages;
    private TextView tabInstalledApps;
    private Button btnPickFile;
    private Button btnScanStorage;
    private ProgressBar pbLoading;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvItems;
    private View emptyView;
    private ImageView ivEmptyIcon;
    private TextView tvEmptyTitle;
    private TextView tvEmptyDesc;
    private Button btnEmptyAction;

    private PackageAdapter adapter;
    private final List<PackageModel> downloadedPackages = new ArrayList<>();
    private final List<PackageModel> installedApps = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String[]> documentPickerLauncher;

    public static LibraryFragment newInstance() {
        return new LibraryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Storage Access Framework document picker for .apk and .xapk files
        documentPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null && getContext() != null) {
                        handlePickedDocument(uri);
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        setupListeners();
        selectSubTab(SUBTAB_PACKAGES);
        loadCurrentTabContent(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCurrentTabContent(false);
    }

    private void initViews(View root) {
        tabDownloadedPackages = root.findViewById(R.id.tab_downloaded_packages);
        tabInstalledApps = root.findViewById(R.id.tab_installed_apps);
        btnPickFile = root.findViewById(R.id.btn_pick_file);
        btnScanStorage = root.findViewById(R.id.btn_scan_storage);
        pbLoading = root.findViewById(R.id.pb_library_loading);
        swipeRefresh = root.findViewById(R.id.swipe_refresh_library);
        rvItems = root.findViewById(R.id.rv_library_items);
        emptyView = root.findViewById(R.id.library_empty_view);
        ivEmptyIcon = root.findViewById(R.id.iv_empty_icon);
        tvEmptyTitle = root.findViewById(R.id.tv_empty_title);
        tvEmptyDesc = root.findViewById(R.id.tv_empty_desc);
        btnEmptyAction = root.findViewById(R.id.btn_empty_action);

        swipeRefresh.setColorSchemeResources(R.color.secondary, R.color.primary);
    }

    private void setupRecyclerView() {
        adapter = new PackageAdapter(requireContext(), new PackageAdapter.OnPackageActionListener() {
            @Override
            public void onPrimaryAction(PackageModel model) {
                handlePrimaryAction(model);
            }

            @Override
            public void onSecondaryAction(PackageModel model) {
                handleSecondaryAction(model);
            }

            @Override
            public void onItemClick(PackageModel model) {
                if (model.getType() == PackageModel.Type.INSTALLED_APP) {
                    handlePrimaryAction(model);
                } else {
                    InstallDialogHelper.showInstallDialog(requireActivity(), model, () -> loadCurrentTabContent(false));
                }
            }
        });

        rvItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvItems.setAdapter(adapter);
    }

    private void setupListeners() {
        tabDownloadedPackages.setOnClickListener(v -> selectSubTab(SUBTAB_PACKAGES));
        tabInstalledApps.setOnClickListener(v -> selectSubTab(SUBTAB_INSTALLED));

        swipeRefresh.setOnRefreshListener(() -> loadCurrentTabContent(true));

        btnScanStorage.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Scanning device storage for packages...", Toast.LENGTH_SHORT).show();
            loadCurrentTabContent(true);
        });

        btnPickFile.setOnClickListener(v -> {
            // Open SAF file picker for .apk and .xapk files
            String[] mimeTypes = new String[]{
                    "application/vnd.android.package-archive",
                    "application/octet-stream",
                    "application/zip",
                    "*/*"
            };
            documentPickerLauncher.launch(mimeTypes);
        });

        btnEmptyAction.setOnClickListener(v -> {
            if (currentSubTab == SUBTAB_PACKAGES) {
                Activity activity = getActivity();
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).switchToBrowseTab();
                }
            } else {
                loadCurrentTabContent(true);
            }
        });
    }

    private void selectSubTab(int tab) {
        currentSubTab = tab;
        Context context = getContext();
        if (context == null) return;

        if (tab == SUBTAB_PACKAGES) {
            tabDownloadedPackages.setBackgroundResource(R.drawable.bg_badge);
            tabDownloadedPackages.setTextColor(ContextCompat.getColor(context, R.color.secondary));

            tabInstalledApps.setBackgroundResource(0);
            tabInstalledApps.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));

            btnPickFile.setVisibility(View.VISIBLE);
            btnScanStorage.setVisibility(View.VISIBLE);
        } else {
            tabInstalledApps.setBackgroundResource(R.drawable.bg_badge);
            tabInstalledApps.setTextColor(ContextCompat.getColor(context, R.color.secondary));

            tabDownloadedPackages.setBackgroundResource(0);
            tabDownloadedPackages.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));

            btnPickFile.setVisibility(View.GONE);
            btnScanStorage.setVisibility(View.GONE);
        }

        loadCurrentTabContent(false);
    }

    private void loadCurrentTabContent(boolean isRefresh) {
        Context context = getContext();
        if (context == null) return;

        pbLoading.setVisibility(View.VISIBLE);

        if (currentSubTab == SUBTAB_PACKAGES) {
            executor.execute(() -> {
                List<PackageModel> scanned = scanLocalPackages(context);
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    downloadedPackages.clear();
                    downloadedPackages.addAll(scanned);
                    adapter.setItems(downloadedPackages);
                    pbLoading.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    updateEmptyState(downloadedPackages.isEmpty());
                });
            });
        } else {
            executor.execute(() -> {
                List<PackageModel> installed = scanInstalledApps(context);
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    installedApps.clear();
                    installedApps.addAll(installed);
                    adapter.setItems(installedApps);
                    pbLoading.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    updateEmptyState(installedApps.isEmpty());
                });
            });
        }
    }

    private List<PackageModel> scanLocalPackages(Context context) {
        List<PackageModel> results = new ArrayList<>();
        List<File> candidateFiles = new ArrayList<>();

        // 1. Check external public Downloads directory
        File publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        addFilesFromDir(publicDownloads, candidateFiles);

        // 2. Check app external files directory
        File appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        addFilesFromDir(appDownloads, candidateFiles);

        // 3. Check app cache directory
        File cacheDir = context.getCacheDir();
        addFilesFromDir(cacheDir, candidateFiles);

        for (File file : candidateFiles) {
            String name = file.getName().toLowerCase();
            PackageModel model = null;
            if (name.endsWith(".xapk")) {
                model = XapkParser.parse(context, file);
            } else if (name.endsWith(".apk")) {
                model = ApkParser.parseApk(context, file);
            }

            if (model != null) {
                results.add(model);
            }
        }

        return results;
    }

    private void addFilesFromDir(File dir, List<File> output) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".apk") || lower.endsWith(".xapk");
        });

        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.length() > 0) {
                    output.add(f);
                }
            }
        }
    }

    private List<PackageModel> scanInstalledApps(Context context) {
        List<PackageModel> results = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> allPackages = pm.getInstalledPackages(0);

        for (PackageInfo info : allPackages) {
            // Filter non-system apps (or apps with launch intent)
            boolean isSystem = (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean hasLaunch = pm.getLaunchIntentForPackage(info.packageName) != null;

            if (!isSystem || hasLaunch) {
                PackageModel model = ApkParser.parseInstalledApp(context, info);
                if (model != null) {
                    results.add(model);
                }
            }
        }

        return results;
    }

    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            rvItems.setVisibility(View.GONE);

            if (currentSubTab == SUBTAB_PACKAGES) {
                ivEmptyIcon.setImageResource(R.drawable.ic_games);
                tvEmptyTitle.setText("No packages found");
                tvEmptyDesc.setText("Download games from Browse or tap 'Pick File' to install an .apk or .xapk from storage.");
                btnEmptyAction.setText("Browse Games");
            } else {
                ivEmptyIcon.setImageResource(R.drawable.ic_games);
                tvEmptyTitle.setText("No installed games found");
                tvEmptyDesc.setText("Installed games and applications will appear here.");
                btnEmptyAction.setText("Refresh");
            }
        } else {
            emptyView.setVisibility(View.GONE);
            rvItems.setVisibility(View.VISIBLE);
        }
    }

    private void handlePrimaryAction(PackageModel model) {
        if (model.getType() == PackageModel.Type.INSTALLED_APP) {
            // Launch the application
            PackageManager pm = requireContext().getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(model.getPackageName());
            if (launchIntent != null) {
                startActivity(launchIntent);
            } else {
                Toast.makeText(requireContext(), "Unable to open application", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Install the APK or XAPK package
            InstallDialogHelper.showInstallDialog(requireActivity(), model, () -> loadCurrentTabContent(false));
        }
    }

    private void handleSecondaryAction(PackageModel model) {
        if (model.getType() == PackageModel.Type.INSTALLED_APP) {
            // Uninstall app
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + model.getPackageName()));
            startActivity(intent);
        } else {
            // Delete downloaded package file
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Package")
                    .setMessage("Are you sure you want to delete " + model.getTitle() + " (" + model.getFile().getName() + ")?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (model.getFile() != null && model.getFile().exists()) {
                            boolean deleted = model.getFile().delete();
                            if (deleted) {
                                Toast.makeText(requireContext(), "Package deleted", Toast.LENGTH_SHORT).show();
                                loadCurrentTabContent(false);
                            } else {
                                Toast.makeText(requireContext(), "Failed to delete file", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void handlePickedDocument(Uri uri) {
        Context context = getContext();
        if (context == null) return;

        pbLoading.setVisibility(View.VISIBLE);
        Toast.makeText(context, "Processing selected package file...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            File tempFile = null;
            try {
                String displayName = "picked_package_" + System.currentTimeMillis();
                try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            displayName = cursor.getString(nameIndex);
                        }
                    }
                }

                String ext = displayName.toLowerCase().endsWith(".xapk") ? ".xapk" : ".apk";
                tempFile = new File(context.getCacheDir(), "imported_" + System.currentTimeMillis() + ext);

                try (InputStream is = context.getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                }

                final PackageModel model;
                if (ext.equals(".xapk")) {
                    model = XapkParser.parse(context, tempFile);
                } else {
                    model = ApkParser.parseApk(context, tempFile);
                }

                final File finalTempFile = tempFile;
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    if (model != null) {
                        InstallDialogHelper.showInstallDialog(requireActivity(), model, () -> {
                            loadCurrentTabContent(false);
                        });
                    } else {
                        Toast.makeText(context, "Could not parse selected package.", Toast.LENGTH_LONG).show();
                        if (finalTempFile.exists()) finalTempFile.delete();
                    }
                });

            } catch (Exception e) {
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(context, "Failed to import file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
