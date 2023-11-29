package com.merxury.blocker.core.controller.root.service;

interface IRootService {
    boolean switchComponent(String pkg, String cls, int state);
    int getUid();
    int getPid();
}
