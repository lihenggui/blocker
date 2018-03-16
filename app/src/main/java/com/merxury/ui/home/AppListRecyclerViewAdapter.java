package com.merxury.ui.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.merxury.blocker.R;
import com.merxury.constant.Constant;
import com.merxury.entity.Application;
import com.merxury.ui.ComponentActivity;

import java.util.List;

/**
 * Created by Mercury on 2018/1/13.
 */

public class AppListRecyclerViewAdapter extends RecyclerView.Adapter<AppListRecyclerViewAdapter.ViewHolder> {

    private List<Application> mPackageInfoList;
    private PackageManager mPm;

    public AppListRecyclerViewAdapter(Context context) {
        mPm = context.getPackageManager();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.app_list_item, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("CheckResult")
    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        final Application application = mPackageInfoList.get(position);
        holder.mTextView.setText(application.getLabel());
        holder.mView.setOnClickListener(v -> {
            //TODO start intent
            Context context = v.getContext();
            Intent intent = new Intent(context, ComponentActivity.class);
            intent.putExtra(Constant.APPLICATION, application);
            context.startActivity(intent);
        });
        RequestOptions options = new RequestOptions()
                .fitCenter()
                .placeholder(android.R.drawable.sym_def_app_icon)
                .error(R.drawable.ic_error_red_24dp);
        Glide.with(holder.mImageView.getContext())
                .load(application.getApplicationIcon(mPm))
                .apply(options)
                .into(holder.mImageView);
    }

    @Override
    public int getItemCount() {
        return mPackageInfoList == null ? 0 : mPackageInfoList.size();
    }

    public void addData(List<Application> list) {
        this.mPackageInfoList = list;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final View mView;
        private final ImageView mImageView;
        private final TextView mTextView;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mImageView = view.findViewById(R.id.app_icon);
            mTextView = view.findViewById(R.id.app_name);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mTextView.getText();
        }
    }
}
