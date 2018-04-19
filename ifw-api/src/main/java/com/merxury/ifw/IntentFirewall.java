package com.merxury.ifw;

import android.content.pm.ComponentInfo;

import com.merxury.ifw.entity.ComponentType;

public interface IntentFirewall {
    String saveRules() throws Exception;

    void addComponent(ComponentInfo component, ComponentType type);

    void removeComponent(ComponentInfo component, ComponentType type);

    boolean getComponentEnableState(ComponentInfo componentInfo);

    void removeRules();

    void removeRules(String name);
}
