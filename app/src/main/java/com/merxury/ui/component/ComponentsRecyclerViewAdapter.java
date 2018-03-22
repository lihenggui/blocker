package com.merxury.ui.component;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.merxury.blocker.R;
import com.merxury.core.ApplicationComponents;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by Mercury on 2018/1/25.
 */

public class ComponentsRecyclerViewAdapter extends RecyclerView.Adapter<ComponentsRecyclerViewAdapter.ViewHolder> {

    private PackageManager mPm;
    private List<ComponentInfo> mComponents;

    public ComponentsRecyclerViewAdapter() {
        mComponents = new ArrayList<>();
    }

    public ComponentsRecyclerViewAdapter(List<ComponentInfo> components) {
        mComponents = components;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.component_item, parent, false);
        mPm = context.getPackageManager();
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (position > mComponents.size()) {
            return;
        }
        final ComponentInfo info = mComponents.get(position);
        String[] splitResult = info.name.split("\\.");
        String splitName = splitResult.length == 0 ? "" : splitResult[splitResult.length - 1];
        holder.mComponentName.setText(splitName);
        holder.mComponentDescription.setText(info.name);
        holder.mSwitch.setChecked(ApplicationComponents.checkComponentIsEnabled(mPm, new ComponentName(info.packageName, info.name)));
    }

    @Override
    public int getItemCount() {
        return mComponents.size();
    }

    public void addData(List<ComponentInfo> components) {
        mComponents = components;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.component_name)
        TextView mComponentName;
        @BindView(R.id.component_description)
        TextView mComponentDescription;
        @BindView(R.id.component_switch)
        Switch mSwitch;

        ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }
}
