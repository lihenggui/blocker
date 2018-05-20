package com.merxury.ifw;

import com.merxury.ifw.entity.ComponentType;

public interface IntentFirewall {
    void save() throws Exception;

    boolean add(String packageName, String componentName, ComponentType type);

    boolean remove(String packageName, String componentName, ComponentType type);

    boolean getComponentEnableState(String packageName, String componentName);

    void clear();

    void clear(String name);

    void reload();
}
