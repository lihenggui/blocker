package com.merxury.ifw;

import android.content.Context;
import android.content.pm.ComponentInfo;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import com.merxury.ifw.entity.ComponentType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

/**
 * Created by Mercury on 2018/4/19.
 */
public class IntentFirewallTest {
    private static final String TAG = "IntentFirewallTest";
    Context context;
    IIntentFirewall firewall;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getTargetContext();
        firewall = new IntentFirewall(context, "com.weico");
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void saveRules() throws Exception {
    }

    @Test
    public void addComponent() throws Exception {
        ComponentInfo componentInfo = new ComponentInfo();
        componentInfo.packageName = "com.weico";
        componentInfo.name = "test";
        ComponentInfo componentInfo1 = new ComponentInfo();
        componentInfo1.packageName = "com.weico1";
        componentInfo1.name = "test";
        firewall.addComponent(componentInfo, ComponentType.ACTIVITY);
        firewall.addComponent(componentInfo1, ComponentType.ACTIVITY);
        File file = firewall.saveRules();
        Log.i(TAG, file.toString());
    }

    @Test
    public void removeComponent() throws Exception {
    }

    @Test
    public void getComponentEnableState() throws Exception {
    }

}