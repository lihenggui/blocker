package com.merxury.ifw.entity;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;

import java.util.List;

public class Component {
    @Attribute
    protected boolean block = true;

    @Attribute
    protected boolean log = false;

    @ElementList(entry = "component-filter", inline = true, empty = false, required = false)
    protected List<ComponentFilter> componentFilters;

    @Element(name = "intent-filter", required = false)
    protected IntentFilter intentFilter;

    public List<ComponentFilter> getComponentFilters() {
        return componentFilters;
    }

    public void setComponentFilters(List<ComponentFilter> componentFilters) {
        this.componentFilters = componentFilters;
    }

    public IntentFilter getIntentFilter() {
        return intentFilter;
    }

    public void setIntentFilter(IntentFilter intentFilter) {
        this.intentFilter = intentFilter;
    }

    public boolean isBlock() {
        return block;
    }

    public void setBlock(boolean block) {
        this.block = block;
    }

    public boolean isLog() {
        return log;
    }

    public void setLog(boolean log) {
        this.log = log;
    }
}
