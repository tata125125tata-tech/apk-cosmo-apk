package com.cosmogamestore.app.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

/**
 * BroadcastReceiver to handle PackageInstaller Session status updates
 * for both multi-package XAPK split installations and single APKs.
 */
public class InstallStatusReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL_STATUS = "com.cosmogamestore.app.ACTION_INSTALL_STATUS";
    public static final String EXTRA_PACKAGE_NAME = "com.cosmogamestore.app.EXTRA_PACKAGE_NAME";
    private static final String TAG = "InstallStatusReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        Log.d(TAG, "Install status received: " + status + ", package: " + packageName + ", message: " + message);

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // User needs to confirm installation through the system dialog
                Intent confirmIntent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
                } else {
                    confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                }

                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                }
                break;

            case PackageInstaller.STATUS_SUCCESS:
                String successMsg = "App installed successfully!";
                if (packageName != null && !packageName.isEmpty()) {
                    successMsg = packageName + " installed successfully!";
                }
                Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show();
                break;

            case PackageInstaller.STATUS_FAILURE:
            case PackageInstaller.STATUS_FAILURE_ABORTED:
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
            case PackageInstaller.STATUS_FAILURE_INVALID:
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                String errorDetail = (message != null && !message.isEmpty()) ? message : "Installation was cancelled or failed.";
                Log.e(TAG, "Installation failed with status " + status + ": " + errorDetail);
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    Toast.makeText(context, "Installation error: " + errorDetail, Toast.LENGTH_LONG).show();
                }
                break;

            default:
                break;
        }
    }
}
