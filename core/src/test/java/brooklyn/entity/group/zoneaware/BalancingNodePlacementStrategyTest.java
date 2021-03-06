package brooklyn.entity.group.zoneaware;

import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.zoneaware.BalancingNodePlacementStrategy;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;

public class BalancingNodePlacementStrategyTest {

    // TODO Should move BrooklynMgmtContext*TestSupport to core, from software/base
    
    private ManagementContext managementContext;
    private TestApplication app;
    private TestEntity entity1;
    private TestEntity entity2;
    private TestEntity entity3;
    private SimulatedLocation loc1;
    private SimulatedLocation loc2;
    private BalancingNodePlacementStrategy placementStrategy;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        managementContext = app.getManagementContext();
        loc1 = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        loc2 = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        entity1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        Thread.sleep(10); // tiny sleep is to ensure creation time is different for each entity
        entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        Thread.sleep(10);
        entity3 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        placementStrategy = new BalancingNodePlacementStrategy();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testAddsBalancedWhenEmpty() throws Exception {
        LinkedHashMultimap<Location, Entity> currentMembers = LinkedHashMultimap.<Location,Entity>create();
        List<Location> result = placementStrategy.locationsForAdditions(currentMembers, ImmutableList.of(loc1, loc2), 4);
        Asserts.assertEqualsIgnoringOrder(result, ImmutableList.of(loc1, loc1, loc2, loc2));
    }

    @Test
    public void testAddsToBalanceWhenPopulated() throws Exception {
        LinkedHashMultimap<Location, Entity> currentMembers = LinkedHashMultimap.<Location,Entity>create();
        currentMembers.put(loc1, entity1);
        currentMembers.put(loc1, entity2);
        List<Location> result = placementStrategy.locationsForAdditions(currentMembers, ImmutableList.of(loc1, loc2), 4);
        Asserts.assertEqualsIgnoringOrder(result, ImmutableList.of(loc1, loc2, loc2, loc2));
    }

    @Test
    public void testAddWillIgnoredDisallowedLocation() throws Exception {
        LinkedHashMultimap<Location, Entity> currentMembers = LinkedHashMultimap.<Location,Entity>create();
        currentMembers.put(loc1, entity1);
        currentMembers.put(loc2, entity2);
        List<Location> result = placementStrategy.locationsForAdditions(currentMembers, ImmutableList.of(loc1), 2);
        Asserts.assertEqualsIgnoringOrder(result, ImmutableList.of(loc1, loc1));
    }

    @Test
    public void testRemovesNewest() throws Exception {
        LinkedHashMultimap<Location, Entity> currentMembers = LinkedHashMultimap.<Location,Entity>create();
        currentMembers.put(loc1, entity1);
        currentMembers.put(loc1, entity2);
        List<Entity> result = placementStrategy.entitiesToRemove(currentMembers, 1);
        Asserts.assertEqualsIgnoringOrder(result, ImmutableList.of(entity2));
    }

    @Test
    public void testRemovesFromBiggestLocation() throws Exception {
        LinkedHashMultimap<Location, Entity> currentMembers = LinkedHashMultimap.<Location,Entity>create();
        currentMembers.put(loc1, entity1);
        currentMembers.put(loc1, entity2);
        currentMembers.put(loc2, entity3);
        List<Entity> result = placementStrategy.entitiesToRemove(currentMembers, 1);
        Asserts.assertEqualsIgnoringOrder(result, ImmutableList.of(entity2));

        // and confirm that not just taking first location!
        currentMembers = LinkedHashMultimap.<Location,Entity>create();
        currentMembers.put(loc1, entity3);
        currentMembers.put(loc2, entity1);
        currentMembers.put(loc2, entity2);
        result = placementStrategy.entitiesToRemove(currentMembers, 1);
        Asserts.assertEqualsIgnoringOrder(result, ImmutableList.of(entity2));
    }

    @Test
    public void testRemovesFromBiggestLocation2() throws Exception {
    }
}
