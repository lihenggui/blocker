package com.merxury.ui;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.merxury.blocker.R;
import com.merxury.function.ApplicationComponents;
import com.stericson.RootTools.RootTools;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.textView1) TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        List<PackageInfo> info = ApplicationComponents.getApplicationList(this);
        List<PackageInfo> thirdPartyApplicationList = ApplicationComponents.getThirdPartyApplicationList(this);
        List<PackageInfo> systemApplicationList = ApplicationComponents.getSystemApplicationList(this);


        for(PackageInfo info1: info) {
            String packageName = info1.packageName;
            ActivityInfo[] activities = ApplicationComponents.getActivitiyList(this, packageName);
            ActivityInfo[] receiver = ApplicationComponents.getReceiverList(this, packageName);
            ProviderInfo[] providers = ApplicationComponents.getProviderList(this, packageName);
            ServiceInfo[] services = ApplicationComponents.getServiceList(this, packageName);
        }
    }

}
