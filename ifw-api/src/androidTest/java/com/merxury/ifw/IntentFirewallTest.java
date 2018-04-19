package com.merxury.ifw;

import android.content.Context;
import android.content.pm.ComponentInfo;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import com.merxury.ifw.entity.ComponentType;

import junit.framework.Assert;

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
        componentInfo.name = "activity1";
        ComponentInfo componentInfo1 = new ComponentInfo();
        componentInfo1.packageName = "com.weico";
        componentInfo1.name = "activity2";
        firewall.addComponent(componentInfo, ComponentType.ACTIVITY);
        firewall.addComponent(componentInfo1, ComponentType.ACTIVITY);
        ComponentInfo componentInfo3 = new ComponentInfo();
        componentInfo3.packageName = "com.weico";
        componentInfo3.name = "broadcast1";
        ComponentInfo componentInfo4 = new ComponentInfo();
        componentInfo4.packageName = "com.weico";
        componentInfo4.name = "broadcast2";
        firewall.addComponent(componentInfo3, ComponentType.BROADCAST);
        firewall.addComponent(componentInfo4, ComponentType.BROADCAST);
        ComponentInfo componentInfo5 = new ComponentInfo();
        componentInfo5.packageName = "com.weico";
        componentInfo5.name = "service1";
        ComponentInfo componentInfo6 = new ComponentInfo();
        componentInfo6.packageName = "com.weico";
        componentInfo6.name = "service";
        firewall.addComponent(componentInfo5, ComponentType.SERVICE);
        firewall.addComponent(componentInfo6, ComponentType.SERVICE);
        File file = firewall.saveRules();
        Log.i(TAG, file.toString());
    }

    @Test
    public void removeComponent() throws Exception {
        ComponentInfo componentInfo = new ComponentInfo();
        componentInfo.packageName = "com.weico";
        componentInfo.name = "activity1";
        firewall.removeComponent(componentInfo, ComponentType.ACTIVITY);
        File file = firewall.saveRules();
        Log.i(TAG, file.toString());
    }

    @Test
    public void getComponentEnableState() throws Exception {
        addComponent();
        removeComponent();
        ComponentInfo componentInfo = new ComponentInfo();
        componentInfo.packageName = "com.weico";
        componentInfo.name = "activity1";
        Assert.assertEquals(false, firewall.getComponentEnableState(componentInfo));
        ComponentInfo componentInfo1 = new ComponentInfo();
        componentInfo1.packageName = "com.weico";
        componentInfo1.name = "activity2";
        Assert.assertEquals(true, firewall.getComponentEnableState(componentInfo1));
        ComponentInfo componentInfo2 = new ComponentInfo();
        componentInfo2.packageName = "com.weico";
        componentInfo2.name = "broadcast1";
        Assert.assertEquals(true, firewall.getComponentEnableState(componentInfo2));
    }

}