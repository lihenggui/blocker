package com.merxury.ifw;

import android.content.pm.ComponentInfo;

import com.merxury.ifw.entity.ComponentType;

public interface IntentFirewall {
    void save() throws Exception;

    boolean add(String packageName, String componentName, ComponentType type);

    boolean remove(String packageName, String componentName, ComponentType type);

    boolean getComponentEnableState(ComponentInfo componentInfo);

    void clear();

    void clear(String name);

    void reload();
}
