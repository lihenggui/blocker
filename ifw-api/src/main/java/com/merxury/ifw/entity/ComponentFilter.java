package com.merxury.ifw.entity;

import org.simpleframework.xml.Attribute;

public class ComponentFilter {
    @Attribute
    private String name;

    public ComponentFilter(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


}
