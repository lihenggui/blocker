package com.merxury.entity.restriction;

/**
 * Created by Wiki on 2018/2/2.
 */

public class Item {
    public Item(String name) {
        this.name = name;
    }

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
