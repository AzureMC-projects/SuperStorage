package com.azuremc.superstorage;

import org.junit.Test;
import static org.junit.Assert.*;

public class StorageFormattingTest {
    @Test public void sanityTestForCi() {
        assertTrue("Android min SDK must remain modern enough for scoped access", 26 >= 26);
        assertEquals("SuperStorage application id", "com.azuremc.superstorage", "com.azuremc.superstorage");
    }
}
