package com.cosmogamestore.app.installer;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cosmogamestore.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter for APK/XAPK packages and installed applications.
 */
public class PackageAdapter extends RecyclerView.Adapter<PackageAdapter.PackageViewHolder> {

    public interface OnPackageActionListener {
        void onPrimaryAction(PackageModel model);
        void onSecondaryAction(PackageModel model);
        void onItemClick(PackageModel model);
    }

    private final Context context;
    private final List<PackageModel> items = new ArrayList<>();
    private final OnPackageActionListener listener;

    public PackageAdapter(Context context, OnPackageActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<PackageModel> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PackageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_package_card, parent, false);
        return new PackageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PackageViewHolder holder, int position) {
        PackageModel model = items.get(position);
        holder.bind(model);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class PackageViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivIcon;
        private final TextView tvTitle;
        private final TextView tvPackageId;
        private final TextView tvTypeBadge;
        private final TextView tvMeta;
        private final TextView tvStatusHint;
        private final Button btnSecondary;
        private final Button btnPrimary;

        public PackageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_package_icon);
            tvTitle = itemView.findViewById(R.id.tv_package_title);
            tvPackageId = itemView.findViewById(R.id.tv_package_id);
            tvTypeBadge = itemView.findViewById(R.id.tv_package_type_badge);
            tvMeta = itemView.findViewById(R.id.tv_package_meta);
            tvStatusHint = itemView.findViewById(R.id.tv_package_status_hint);
            btnSecondary = itemView.findViewById(R.id.btn_package_secondary);
            btnPrimary = itemView.findViewById(R.id.btn_package_primary);
        }

        public void bind(PackageModel model) {
            if (model.getIcon() != null) {
                ivIcon.setImageDrawable(model.getIcon());
            } else {
                ivIcon.setImageResource(R.drawable.ic_games);
            }

            tvTitle.setText(model.getTitle());
            tvPackageId.setText(model.getPackageName());
            tvTypeBadge.setText(model.getTypeDescription());

            String sizeStr = Formatter.formatFileSize(context, model.getSizeBytes());
            tvMeta.setText("v" + model.getVersionName() + " • " + sizeStr);

            if (model.getType() == PackageModel.Type.INSTALLED_APP) {
                tvStatusHint.setText("Installed on device");
                btnPrimary.setText("Open");
                btnSecondary.setVisibility(View.VISIBLE);
                btnSecondary.setText("Uninstall");
            } else {
                if (model.isInstalled()) {
                    tvStatusHint.setText("Installed (v" + model.getInstalledVersionName() + ")");
                    btnPrimary.setText("Update");
                } else {
                    tvStatusHint.setText("Ready to install");
                    btnPrimary.setText("Install");
                }
                btnSecondary.setVisibility(View.VISIBLE);
                btnSecondary.setText("Delete");
            }

            btnPrimary.setOnClickListener(v -> {
                if (listener != null) listener.onPrimaryAction(model);
            });

            btnSecondary.setOnClickListener(v -> {
                if (listener != null) listener.onSecondaryAction(model);
            });

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(model);
            });
        }
    }
}
