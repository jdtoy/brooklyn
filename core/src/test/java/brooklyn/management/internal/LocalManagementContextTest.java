package brooklyn.management.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynProperties.Factory.Builder;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext.PropertiesReloadListener;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class LocalManagementContextTest {
    
    private File globalPropertiesFile;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        globalPropertiesFile = File.createTempFile("local-brooklyn-properties-test", ".properties");
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (globalPropertiesFile != null) globalPropertiesFile.delete();
    }
    
    @Test
    public void testReloadPropertiesFromBuilder() throws IOException {
        String globalPropertiesContents = "brooklyn.location.localhost.displayName=myname";
        Files.write(globalPropertiesContents, globalPropertiesFile, Charsets.UTF_8);
        Builder builder = new BrooklynProperties.Factory.Builder()
            .globalPropertiesFile(globalPropertiesFile.getAbsolutePath());
        LocalManagementContext context = new LocalManagementContext(builder);
        Location location = context.getLocationRegistry().resolve("localhost");
        assertEquals(location.getDisplayName(), "myname");
        String newGlobalPropertiesContents = "brooklyn.location.localhost.displayName=myname2";
        Files.write(newGlobalPropertiesContents, globalPropertiesFile, Charsets.UTF_8);
        context.reloadBrooklynProperties();
        Location location2 = context.getLocationRegistry().resolve("localhost");
        assertEquals(location.getDisplayName(), "myname");
        assertEquals(location2.getDisplayName(), "myname2");
    }
    
    @Test
    public void testReloadPropertiesFromProperties() throws IOException {
        String globalPropertiesContents = "brooklyn.location.localhost.displayName=myname";
        Files.write(globalPropertiesContents, globalPropertiesFile, Charsets.UTF_8);
        BrooklynProperties brooklynProperties = new BrooklynProperties.Factory.Builder()
            .globalPropertiesFile(globalPropertiesFile.getAbsolutePath())
            .build();
        LocalManagementContext context = new LocalManagementContext(brooklynProperties);
        Location location = context.getLocationRegistry().resolve("localhost");
        assertEquals(location.getDisplayName(), "myname");
        String newGlobalPropertiesContents = "brooklyn.location.localhost.displayName=myname2";
        Files.write(newGlobalPropertiesContents, globalPropertiesFile, Charsets.UTF_8);
        context.reloadBrooklynProperties();
        Location location2 = context.getLocationRegistry().resolve("localhost");
        assertEquals(location.getDisplayName(), "myname");
        assertEquals(location2.getDisplayName(), "myname");
    }
    
    @Test
    public void testPropertiesModified() throws IOException {
        BrooklynProperties properties = BrooklynProperties.Factory.newEmpty();
        properties.put("myname", "myval");
        LocalManagementContext context = new LocalManagementContext(properties);
        assertEquals(context.getBrooklynProperties().get("myname"), "myval");
        properties.put("myname", "newval");
        assertEquals(properties.get("myname"), "newval");
        // TODO: Should changes in the 'properties' collection be reflected in context.getBrooklynProperties()?
        assertNotEquals(context.getBrooklynProperties().get("myname"), "newval");
    }
    
    @Test
    public void testAddAndRemoveReloadListener() {
        final AtomicInteger reloadedCallbackCount = new AtomicInteger(0);
        BrooklynProperties properties = BrooklynProperties.Factory.newEmpty();
        properties.put("myname", "myval");
        LocalManagementContext context = new LocalManagementContext(properties);
        PropertiesReloadListener listener = new PropertiesReloadListener() {
            public void reloaded() {
                reloadedCallbackCount.incrementAndGet();
            }
        };
        assertEquals(reloadedCallbackCount.get(), 0);
        context.addPropertiesReloadListener(listener);
        context.reloadBrooklynProperties();
        assertEquals(reloadedCallbackCount.get(), 1);
        context.removePropertiesReloadListener(listener);
        context.reloadBrooklynProperties();
        assertEquals(reloadedCallbackCount.get(), 1);
    }
}
