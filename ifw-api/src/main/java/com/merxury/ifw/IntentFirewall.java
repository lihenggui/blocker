package com.merxury.ifw;

import android.content.pm.ComponentInfo;

import com.merxury.ifw.entity.ComponentType;

public interface IntentFirewall {
    void save() throws Exception;

    boolean add(ComponentInfo component, ComponentType type);

    boolean remove(ComponentInfo component, ComponentType type);

    boolean getComponentEnableState(ComponentInfo componentInfo);

    void clear();

    void clear(String name);
}
