// Copyright 2026 Blocker. Licensed under the Apache License, Version 2.0.
package com.merxury.blocker.core.controllers.ifw.shizuku;

interface IIfwFileService {
    String readFile(String path) = 1;
    boolean writeFile(String path, String content) = 2;
    boolean deleteFile(String path) = 3;
    boolean fileExists(String path) = 4;
    List<String> listFiles(String dir) = 5;
    void destroy() = 16777114;
}
