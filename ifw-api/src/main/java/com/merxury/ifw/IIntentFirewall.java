package com.merxury.ifw;

import android.content.pm.ComponentInfo;

import com.merxury.ifw.entity.ComponentType;

import java.io.File;

public interface IIntentFirewall {
    File saveRules();

    void addComponent(ComponentInfo component, ComponentType type);

    void removeComponent(ComponentInfo component, ComponentType type);

    boolean getComponentEnableState(ComponentInfo componentInfo);
}
