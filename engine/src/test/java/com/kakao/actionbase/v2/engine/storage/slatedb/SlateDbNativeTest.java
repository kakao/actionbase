package com.kakao.actionbase.v2.engine.storage.slatedb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SlateDbNativeTest {

    @TempDir
    Path tempDir;

    private SlateDbNative db;

    private static Path findLibraryPath() {
        // Find project root by looking for settings.gradle.kts
        Path dir = Path.of(System.getProperty("user.dir"));
        while (!dir.resolve("settings.gradle.kts").toFile().exists() && dir.getParent() != null) {
            dir = dir.getParent();
        }
        return dir.resolve("native/lib/libslatedb_c.dylib");
    }

    @BeforeEach
    void setUp() throws Throwable {
        // SlateDB uses object_store:
        // - url: object store root (file:// for local filesystem)
        // - path: prefix/directory within the store
        String fileUrl = "file://" + tempDir.toAbsolutePath();
        String dbPath = "data";  // prefix within the object store
        db = SlateDbNative.open(dbPath, fileUrl, findLibraryPath());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void putAndGet() throws Throwable {
        // Given
        byte[] key = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] value = "world".getBytes(StandardCharsets.UTF_8);

        // When
        db.put(key, value);
        byte[] result = db.get(key);

        // Then
        assertNotNull(result);
        assertEquals("world", new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void getNonExistentKeyReturnsNull() throws Throwable {
        // When
        byte[] result = db.get("nonexistent".getBytes(StandardCharsets.UTF_8));

        // Then
        assertNull(result);
    }

    @Test
    void deleteRemovesKey() throws Throwable {
        // Given
        byte[] key = "to-delete".getBytes(StandardCharsets.UTF_8);
        db.put(key, "value".getBytes(StandardCharsets.UTF_8));

        // When
        db.delete(key);
        byte[] result = db.get(key);

        // Then
        assertNull(result);
    }

    @Test
    void overwriteExistingKey() throws Throwable {
        // Given
        byte[] key = "key".getBytes(StandardCharsets.UTF_8);
        db.put(key, "value1".getBytes(StandardCharsets.UTF_8));

        // When
        db.put(key, "value2".getBytes(StandardCharsets.UTF_8));
        byte[] result = db.get(key);

        // Then
        assertNotNull(result);
        assertEquals("value2", new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void binaryData() throws Throwable {
        // Given
        byte[] key = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF};
        byte[] value = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};

        // When
        db.put(key, value);
        byte[] result = db.get(key);

        // Then
        assertNotNull(result);
        assertArrayEquals(value, result);
    }
}
