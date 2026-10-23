package dev.middleshare;

import android.os.storage.StorageManager;
import java.io.File;
import java.util.UUID;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowStorageManager;

/** The host has no Android storage Binder service; model its allocation queries for download tests. */
@Implements(StorageManager.class)
public class TestStorage extends ShadowStorageManager {
    static long allocatable;
    @Implementation protected UUID getUuidForPath(File path) { return StorageManager.UUID_DEFAULT; }
    @Implementation protected long getAllocatableBytes(UUID uuid) { return allocatable; }
}
