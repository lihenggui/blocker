// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake;

import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.writer.io.MemoryDataStore;
import org.jf.dexlib2.writer.pool.DexPool;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Rebuild the reviewed class namespace with independent DEX indexes before hashing it. */
final class SdkDexFingerprint {
    private static final int MAX_DEX_BYTES = 24 * 1024 * 1024;
    private static final int MAX_TOTAL_BYTES = 256 * 1024 * 1024;
    private static final int MAX_CLASSES = 1200;

    static String compute(List<File> apks, String prefix) throws IOException {
        if (!prefix.startsWith("L") || !prefix.endsWith("/") || prefix.length() < 5) {
            throw new IllegalArgumentException("Invalid reviewed namespace");
        }
        TreeMap<String, ClassDef> classes = new TreeMap<>();
        long total = 0;
        for (File apk : apks) {
            try (ZipFile zip = new ZipFile(apk)) {
                if (zip.size() > 100000) return null;
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) continue;
                    long size = entry.getSize();
                    total += size;
                    if (size < 0 || size > MAX_DEX_BYTES || total > MAX_TOTAL_BYTES) return null;
                    byte[] data;
                    try (InputStream in = zip.getInputStream(entry)) {
                        data = new byte[(int) size];
                        new DataInputStream(in).readFully(data);
                        if (in.read() != -1) throw new IOException("Invalid dex size");
                    }
                    DexBackedDexFile dex = new DexBackedDexFile(null, data);
                    for (ClassDef definition : dex.getClasses()) {
                        String name = definition.getType();
                        if (!name.startsWith(prefix)) continue;
                        if (classes.size() >= MAX_CLASSES || classes.containsKey(name)) return null;
                        classes.put(name, ImmutableClassDef.of(definition));
                    }
                }
            }
        }
        if (classes.isEmpty()) return null;
        DexPool pool = new DexPool(Opcodes.forApi(24));
        for (ClassDef definition : classes.values()) pool.internClass(definition);
        MemoryDataStore output = new MemoryDataStore();
        try {
            pool.writeTo(output);
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(output.getBuffer(), 0, output.getSize());
            StringBuilder text = new StringBuilder();
            for (byte b : hash.digest()) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return text.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        } finally {
            output.close();
        }
    }

    private SdkDexFingerprint() {}
}
