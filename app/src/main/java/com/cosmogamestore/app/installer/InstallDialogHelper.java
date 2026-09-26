package com.cosmogamestore.app.installer;

import android.app.Activity;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.cosmogamestore.app.R;

/**
 * Builds and shows an interactive Material 3 dialog for confirming and installing
 * parsed APK and XAPK packages.
 */
public class InstallDialogHelper {

    public static void showInstallDialog(Activity activity, PackageModel model, Runnable onInstalledCallback) {
        if (activity == null || activity.isFinishing() || model == null) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_install_package, null);

        ImageView ivIcon = dialogView.findViewById(R.id.iv_dialog_app_icon);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_app_title);
        TextView tvPackageName = dialogView.findViewById(R.id.tv_dialog_package_name);
        TextView tvTypeBadge = dialogView.findViewById(R.id.tv_dialog_type_badge);
        TextView tvVersionSize = dialogView.findViewById(R.id.tv_dialog_version_size);
        TextView tvInstallStatus = dialogView.findViewById(R.id.tv_dialog_install_status);

        LinearLayout layoutProgress = dialogView.findViewById(R.id.layout_dialog_progress);
        TextView tvProgressMsg = dialogView.findViewById(R.id.tv_dialog_progress_msg);
        TextView tvProgressPct = dialogView.findViewById(R.id.tv_dialog_progress_pct);
        ProgressBar pbInstall = dialogView.findViewById(R.id.pb_dialog_install);

        Button btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        Button btnInstall = dialogView.findViewById(R.id.btn_dialog_install);

        // Populate metadata
        if (model.getIcon() != null) {
            ivIcon.setImageDrawable(model.getIcon());
        }
        tvTitle.setText(model.getTitle());
        tvPackageName.setText(model.getPackageName());
        tvTypeBadge.setText(model.getTypeDescription());

        String sizeStr = Formatter.formatFileSize(activity, model.getSizeBytes());
        tvVersionSize.setText("v" + model.getVersionName() + " • " + sizeStr);

        if (model.isInstalled()) {
            tvInstallStatus.setVisibility(View.VISIBLE);
            tvInstallStatus.setText("Currently installed: v" + model.getInstalledVersionName());
            btnInstall.setText("Update / Install");
        } else {
            tvInstallStatus.setVisibility(View.GONE);
            btnInstall.setText("Install Package");
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnInstall.setOnClickListener(v -> {
            if (!PackageInstallerHelper.canRequestPackageInstalls(activity)) {
                dialog.dismiss();
                PackageInstallerHelper.promptUnknownSourcesPermission(activity);
                return;
            }

            btnInstall.setEnabled(false);
            btnCancel.setEnabled(false);
            layoutProgress.setVisibility(View.VISIBLE);
            dialog.setCancelable(false);

            PackageInstallerHelper.installPackage(activity, model, new PackageInstallerHelper.InstallCallback() {
                @Override
                public void onProgress(String message, int percentage) {
                    tvProgressMsg.setText(message);
                    tvProgressPct.setText(percentage + "%");
                    pbInstall.setProgress(percentage);
                }

                @Override
                public void onSuccess() {
                    dialog.dismiss();
                    if (onInstalledCallback != null) {
                        onInstalledCallback.run();
                    }
                }

                @Override
                public void onError(String error) {
                    btnInstall.setEnabled(true);
                    btnCancel.setEnabled(true);
                    dialog.setCancelable(true);
                    layoutProgress.setVisibility(View.GONE);
                    Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }
}
