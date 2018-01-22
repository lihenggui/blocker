package com.merxury.core.root;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import com.merxury.core.ApplicationComponents;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class RootControllerTest {
    private static final String TAG = "Test";
    private RootController controller;
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getContext();
        controller = RootController.getInstance();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void switchComponent() throws Exception {
        boolean isSuccess = controller.switchComponent("com.weico.international", "service.CompleteReceiver1", PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        assertEquals(false, isSuccess);
        isSuccess = controller.switchComponent("com.weico.international", "service.CompleteReceiver", PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        assertEquals(true, isSuccess);
        isSuccess = controller.switchComponent("com.weico.international", "service.CompleteReceiver", PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        assertEquals(true, isSuccess);
    }

    @Test
    public void performanceTest() {
        long time1, time2;
        time1 = System.currentTimeMillis();
        List<PackageInfo> info = ApplicationComponents.getApplicationList(context);
        time2 = System.currentTimeMillis();
        Log.d(TAG, "get applitation list takes " + (time2 - time1) + "milliseconds");

        time1 = System.currentTimeMillis();
        List<PackageInfo> thirdPartyApplicationList = ApplicationComponents.getThirdPartyApplicationList(context);
        time2 = System.currentTimeMillis();
        Log.d(TAG, "get 3rd applitation list takes " + (time2 - time1) + "milliseconds");

        time1 = System.currentTimeMillis();
        List<PackageInfo> systemApplicationList = ApplicationComponents.getSystemApplicationList(context);
        time2 = System.currentTimeMillis();
        Log.d(TAG, "get system applitation list takes " + (time2 - time1) + "milliseconds");

        time1 = System.currentTimeMillis();
        for (PackageInfo info1 : info) {
            String packageName = info1.packageName;
            ActivityInfo[] activities = ApplicationComponents.getActivitiyList(context, packageName);
            ActivityInfo[] receiver = ApplicationComponents.getReceiverList(context, packageName);
            ProviderInfo[] providers = ApplicationComponents.getProviderList(context, packageName);
            ServiceInfo[] services = ApplicationComponents.getServiceList(context, packageName);
        }
        time2 = System.currentTimeMillis();
        Log.d(TAG, "get all componments takes " + (time2 - time1) + "milliseconds");
    }


}