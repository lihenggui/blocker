package com.merxury.entity;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Mercury on 2017/12/30.
 * An entity class that describe application infomation
 */

public class Application implements Parcelable{
    private String packageName;
    private String versionName;
    private int versionCode;
    private boolean enabled;
    private int targetSdkVersion;

    private Application(){}

    public Application(PackageInfo info) {
        this.packageName = info.packageName;
        this.versionName = info.versionName;
        this.versionCode = info.versionCode;
        ApplicationInfo appDetails = info.applicationInfo;
        if(appDetails != null) {
            this.targetSdkVersion = appDetails.targetSdkVersion;
            this.enabled = appDetails.enabled;
        }
    }

    public static final Parcelable.Creator<Application> CREATOR = new Creator<Application>() {
        @Override
        public Application createFromParcel(Parcel source) {
            Application application = new Application();
            application.setPackageName(source.readString());
            application.setVersionName(source.readString());
            application.setVersionCode(source.readInt());
            application.setEnabled(source.readByte() != 0);
            application.setTargetSdkVersion(source.readInt());
            return application;
        }

        @Override
        public Application[] newArray(int size) {
            return new Application[size];
        }
    };

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTargetSdkVersion() {
        return targetSdkVersion;
    }

    public void setTargetSdkVersion(int targetSdkVersion) {
        this.targetSdkVersion = targetSdkVersion;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeString(versionName);
        dest.writeInt(versionCode);
        dest.writeByte((byte) (enabled ? 1 : 0));
        dest.writeInt(targetSdkVersion);
    }
}
