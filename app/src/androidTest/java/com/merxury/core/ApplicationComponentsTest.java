package com.merxury.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.support.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Wiki on 2018/1/25.
 */
public class ApplicationComponentsTest {
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getContext();
    }

    @Test
    public void getApplicationList() throws Exception {
    }

    @Test
    public void getThirdPartyApplicationList() throws Exception {
    }

    @Test
    public void getSystemApplicationList() throws Exception {
    }

    @Test
    public void getActivitiyList() throws Exception {
    }

    @Test
    public void getReceiverList() throws Exception {
    }

    @Test
    public void getServiceList() throws Exception {
    }

    @Test
    public void getProviderList() throws Exception {
    }

    @Test
    public void getApplicationComponents() throws Exception {

    }

    @Test
    public void getApplicationComponents1() throws Exception {
        PackageInfo info = ApplicationComponents.getApplicationComponents(context.getPackageManager(), "com.weico.international");
        assertNotEquals(info, null);
    }

    @Test
    public void checkComponentIsEnabled() throws Exception {
        ComponentName name = new ComponentName("com.weico.international", "com.tencent.android.tpush.XGPushReceiver");
        boolean enabled = ApplicationComponents.checkComponentIsEnabled(context.getPackageManager(), name);
        assertEquals(enabled, false);
    }

}