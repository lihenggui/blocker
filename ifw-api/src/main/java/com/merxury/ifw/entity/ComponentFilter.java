package com.merxury.ifw.entity;

import org.simpleframework.xml.Attribute;

public class ComponentFilter {
    @Attribute(required = true)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


}
