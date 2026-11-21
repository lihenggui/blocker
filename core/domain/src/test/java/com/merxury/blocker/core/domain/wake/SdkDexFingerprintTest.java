// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.writer.io.MemoryDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SdkDexFingerprintTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File apk(String... names) throws Exception {
        DexPool pool = new DexPool(Opcodes.forApi(24));
        for (String name : names) {
            pool.internClass(new ImmutableClassDef(name, 1, "Ljava/lang/Object;", null, null, null, null, null));
        }
        File file = temp.newFile();
        MemoryDataStore data = new MemoryDataStore();
        pool.writeTo(data);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write(data.getBuffer(), 0, data.getSize());
            zip.closeEntry();
        } finally {
            data.close();
        }
        return file;
    }

    @Test public void hostClassesDoNotChangeEvidenceAndShadowedSdkIsRejected() throws Exception {
        File original = apk("Llibrary/Service;");
        File host = apk("Lhost/App;", "Llibrary/Service;");
        String expected = SdkDexFingerprint.compute(Collections.singletonList(original), "Llibrary/");
        assertNotEquals(null, expected);
        assertEquals(expected, SdkDexFingerprint.compute(Collections.singletonList(host), "Llibrary/"));
        assertNotEquals(expected, SdkDexFingerprint.compute(Collections.singletonList(apk("Llibrary/Other;")), "Llibrary/"));
        assertNull(SdkDexFingerprint.compute(Arrays.asList(original, host), "Llibrary/"));
    }
}
