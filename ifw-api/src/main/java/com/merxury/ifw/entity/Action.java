package com.merxury.ifw.entity;

import org.simpleframework.xml.Attribute;

public class Action {
    @Attribute
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
