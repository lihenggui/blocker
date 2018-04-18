package com.merxury.ifw.entity;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root
public class Rules {
    @Element(required = false)
    private Activity activity;
    @Element(required = false)
    private Broadcast broadcast;
    @Element(required = false)
    private Service service;
}
