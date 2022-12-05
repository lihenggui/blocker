package com.merxury.ifw.entity;

import org.simpleframework.xml.ElementList;

import java.util.List;

public class IntentFilter {
    @ElementList(inline = true)
    List<Action> actions;

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }
}
