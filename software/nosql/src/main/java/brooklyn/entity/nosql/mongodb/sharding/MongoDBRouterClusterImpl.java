package brooklyn.entity.nosql.mongodb.sharding;

import java.util.Collection;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class MongoDBRouterClusterImpl extends DynamicClusterImpl implements MongoDBRouterCluster {

    @Override
    public void init() {
        super.init();
        subscribeToChildren(this, MongoDBRouter.RUNNING, new SensorEventListener<Boolean>() {
            @Override public void onEvent(SensorEvent<Boolean> event) {
                setAnyRouter();
            }
        });
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);
        AbstractMembershipTrackingPolicy policy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "Router cluster membership tracker")) {
            @Override protected void onEntityAdded(Entity member) {
                setAnyRouter();
            }
            @Override protected void onEntityRemoved(Entity member) {
                setAnyRouter();
            }
            @Override protected void onEntityChange(Entity member) {
                setAnyRouter();
            }
        };
        addPolicy(policy);
        policy.setGroup(this);
    }
    
    protected void setAnyRouter() {
        setAttribute(MongoDBRouterCluster.ANY_ROUTER, Iterables.tryFind(getRouters(), 
                EntityPredicates.attributeEqualTo(Startable.SERVICE_UP, true)).orNull());

        setAttribute(
                MongoDBRouterCluster.ANY_RUNNING_ROUTER, 
                Iterables.tryFind(getRouters(), EntityPredicates.attributeEqualTo(MongoDBRouter.RUNNING, true))
                .orNull());
    }
    
    @Override
    public Collection<MongoDBRouter> getRouters() {
        return ImmutableList.copyOf(Iterables.filter(getMembers(), MongoDBRouter.class));
    }
    
    @Override
    protected EntitySpec<?> getMemberSpec() {
        if (super.getMemberSpec() != null)
            return super.getMemberSpec();
        return EntitySpec.create(MongoDBRouter.class);
    }

    @Override
    public MongoDBRouter getAnyRouter() {
        return getAttribute(MongoDBRouterCluster.ANY_ROUTER);
    }
    
    @Override
    public MongoDBRouter getAnyRunningRouter() {
        return getAttribute(MongoDBRouterCluster.ANY_RUNNING_ROUTER);
    }
 
}
