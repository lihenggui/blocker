package com.merxury.adapter;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.merxury.blocker.R;

import java.util.List;

/**
 * Created by lihen on 2018/1/13.
 */

public class AppListRecyclerViewAdapter extends RecyclerView.Adapter<AppListRecyclerViewAdapter.ViewHolder> {

    private List<PackageInfo> mPackageInfoList;
    private PackageManager mPm;

    public AppListRecyclerViewAdapter(Context context, List<PackageInfo> packageList) {
        mPackageInfoList = packageList;
        mPm = context.getPackageManager();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.app_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        PackageInfo packageInfo = mPackageInfoList.get(position);
        holder.mTextView.setText(packageInfo.applicationInfo.loadLabel(mPm));
        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO start intent
            }
        });
        RequestOptions options = new RequestOptions()
                .fitCenter()
                .placeholder(R.drawable.ic_android_green_24dp)
                .error(R.drawable.ic_error_red_24dp);
        Glide.with(holder.mImageView.getContext())
                .load(packageInfo.applicationInfo.loadIcon(mPm))
                .apply(options)
                .into(holder.mImageView);
    }

    @Override
    public int getItemCount() {
        return mPackageInfoList.size();
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
