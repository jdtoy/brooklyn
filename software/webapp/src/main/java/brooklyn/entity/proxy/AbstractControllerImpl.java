package brooklyn.entity.proxy;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.rebind.BasicEntityRebindSupport;
import brooklyn.entity.rebind.RebindContext;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.access.BrooklynAccessUtils;
import brooklyn.management.Task;
import brooklyn.mementos.EntityMemento;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Maybe;
import brooklyn.util.task.Tasks;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
public abstract class AbstractControllerImpl extends SoftwareProcessImpl implements AbstractController {
    
    // TODO Should review synchronization model. Currently, all changes to the serverPoolTargets
    // (and checking for potential changes) is done while synchronized on this. That means it 
    // will also call update/reload while holding the lock. This is "conservative", but means
    // sub-classes need to be extremely careful about any additional synchronization and of
    // their implementations of update/reconfigureService/reload.
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractControllerImpl.class);

    protected volatile boolean isActive;
    protected volatile boolean updateNeeded = true;

    protected AbstractMembershipTrackingPolicy serverPoolMemberTrackerPolicy;
    protected Set<String> serverPoolAddresses = Sets.newLinkedHashSet();
    protected Map<Entity,String> serverPoolTargets = Maps.newLinkedHashMap();
    
    public AbstractControllerImpl() {
        this(MutableMap.of(), null, null);
    }
    public AbstractControllerImpl(Map<?, ?> properties) {
        this(properties, null, null);
    }
    public AbstractControllerImpl(Entity parent) {
        this(MutableMap.of(), parent, null);
    }
    public AbstractControllerImpl(Map<?, ?> properties, Entity parent) {
        this(properties, parent, null);
    }
    public AbstractControllerImpl(Entity parent, Cluster cluster) {
        this(MutableMap.of(), parent, cluster);
    }
    public AbstractControllerImpl(Map<?, ?> properties, Entity parent, Cluster cluster) {
        super(properties, parent);
    }

    @Override
    public void init() {
        super.init();
        initServerPoolMemberTrackingPolicy();
    }
    @Override
    // TODO When persists policies, then can delete this code
    protected void rebind() {
        initServerPoolMemberTrackingPolicy();
        
        super.rebind();
    }
    
    protected void initServerPoolMemberTrackingPolicy() {
        AttributeSensor<?> hostAndPortSensor = getConfig(HOST_AND_PORT_SENSOR);
        AttributeSensor<?> hostnameSensor = getConfig(HOSTNAME_SENSOR);
        AttributeSensor<?> portSensor = getConfig(PORT_NUMBER_SENSOR);
        Set<AttributeSensor<?>> sensorsToTrack;
        if (hostAndPortSensor != null) {
            sensorsToTrack = ImmutableSet.<AttributeSensor<?>>of(hostAndPortSensor);
        } else {
            sensorsToTrack = ImmutableSet.<AttributeSensor<?>>of(hostnameSensor, portSensor);
        }
        Map<?, ?> policyFlags = MutableMap.of("name", "Controller targets tracker",
            "sensorsToTrack", sensorsToTrack);
        serverPoolMemberTrackerPolicy = new AbstractMembershipTrackingPolicy(policyFlags) {
            @Override protected void onEntityEvent(EventType type, Entity entity) { onServerPoolMemberChanged(entity); }
        };
    }
    
    /**
     * Opportunity to do late-binding of the cluster that is being controlled. Must be called before start().
     * Can pass in the 'serverPool'.
     */
    @Override
    public void bind(Map flags) {
        if (flags.containsKey("serverPool")) {
            setConfigEvenIfOwned(SERVER_POOL, (Group) flags.get("serverPool"));
        } 
    }

    @Override
    public void onManagementNoLongerMaster() {
        super.onManagementNoLongerMaster(); // TODO remove when deprecated method in parent removed
        isActive = false;
        if (serverPoolMemberTrackerPolicy!=null)
            serverPoolMemberTrackerPolicy.reset();
    }

    private Group getServerPool() {
        return getConfig(SERVER_POOL);
    }
    
    @Override
    public boolean isActive() {
        return isActive;
    }
    
    @Override
    public boolean isSsl() {
        return getSslConfig() != null;
    }
    
    @Override
    public ProxySslConfig getSslConfig() {
        return getConfig(SSL_CONFIG);
    }
    
    @Override
    public String getProtocol() {
        return getAttribute(PROTOCOL);
    }

    /** returns primary domain this controller responds to, or null if it responds to all domains */
    @Override
    public String getDomain() {
        return getAttribute(DOMAIN_NAME);
    }
    
    @Override
    public Integer getPort() {
        return getAttribute(PROXY_HTTP_PORT);
    }

    /** primary URL this controller serves, if one can / has been inferred */
    @Override
    public String getUrl() {
        return getAttribute(ROOT_URL);
    }

    @Override
    public AttributeSensor<Integer> getPortNumberSensor() {
        return getAttribute(PORT_NUMBER_SENSOR);
    }

    @Override
    public AttributeSensor<String> getHostnameSensor() {
        return getAttribute(HOSTNAME_SENSOR);
    }

    @Override
    public AttributeSensor<String> getHostAndPortSensor() {
        return getAttribute(HOST_AND_PORT_SENSOR);
    }
    
    @Override
    public Set<String> getServerPoolAddresses() {
        return serverPoolAddresses;
    }

    @Override
    public abstract void reload();

    protected String inferProtocol() {
        return getConfig(SSL_CONFIG)!=null ? "https" : "http";
    }
    
    /** returns URL, if it can be inferred; null otherwise */
    protected String inferUrl(boolean requireManagementAccessible) {
        String protocol = checkNotNull(getProtocol(), "no protocol configured");
        String domain = getDomain();
        if (domain != null && domain.startsWith("*.")) {
            domain = domain.replace("*.", ""); // Strip wildcard
        }
        Integer port = checkNotNull(getPort(), "no port configured (the requested port may be in use)");
        if (requireManagementAccessible) {
            HostAndPort accessible = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, port);
            if (accessible!=null) {
                domain = accessible.getHostText();
                port = accessible.getPort();
            }
        }
        if (domain==null) domain = getAttribute(LoadBalancer.HOSTNAME);
        if (domain==null) return null;
        return protocol+"://"+domain+":"+port+"/"+getConfig(SERVICE_UP_URL_PATH);
    }

    protected String inferUrl() {
        return inferUrl(false);
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts();
        if (groovyTruth(getAttribute(PROXY_HTTP_PORT))) result.add(getAttribute(PROXY_HTTP_PORT));
        return result;
    }

    @Override
    protected void preStart() {
        super.preStart();

        AttributeSensor<?> hostAndPortSensor = getConfig(HOST_AND_PORT_SENSOR);
        Maybe<Object> hostnameSensor = getConfigRaw(HOSTNAME_SENSOR, true);
        Maybe<Object> portSensor = getConfigRaw(PORT_NUMBER_SENSOR, true);
        if (hostAndPortSensor != null) {
            checkState(!hostnameSensor.isPresent() && !portSensor.isPresent(), 
                    "Must not set %s and either of %s or %s", HOST_AND_PORT_SENSOR, HOSTNAME_SENSOR, PORT_NUMBER_SENSOR);
        }

        ConfigToAttributes.apply(this);

        setAttribute(PROTOCOL, inferProtocol());
        setAttribute(ROOT_URL, inferUrl());
 
        checkNotNull(getPortNumberSensor(), "no sensor configured to infer port number");
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        LOG.info("Adding policy {} to {}, during start", serverPoolMemberTrackerPolicy, this);
        addPolicy(serverPoolMemberTrackerPolicy);
        if (getUrl()==null) setAttribute(ROOT_URL, inferUrl());
        
        resetServerPoolMemberTrackerPolicy();
    }
    
    @Override
    protected void postStart() {
        super.postStart();
        isActive = true;
        update();
    }

    @Override
    protected void postRebind() {
        super.postRebind();
        isActive = true;
        update();
    }

    @Override
    protected void preStop() {
        super.preStop();
        if (serverPoolMemberTrackerPolicy!=null)
            serverPoolMemberTrackerPolicy.reset();
    }

    /** 
     * Implementations should update the configuration so that 'serverPoolAddresses' are targeted.
     * The caller will subsequently call reload to apply the new configuration.
     */
    protected abstract void reconfigureService();
    
    public synchronized void updateNeeded() {
        if (updateNeeded) return;
        updateNeeded = true;
        LOG.debug("queueing an update-needed task for "+this+"; update will occur shortly");
        Entities.submit(this, Tasks.builder().name("update-needed").body(new Runnable() {
            @Override
            public void run() {
                if (updateNeeded)
                    AbstractControllerImpl.this.update();
            } 
        }).build());
    }
    
    @Override
    public void update() {
        Task<?> task = updateAsync();
        if (task != null) task.getUnchecked();
    }
    
    public synchronized Task<?> updateAsync() {
        Task<?> result = null;
        if (!isActive()) updateNeeded = true;
        else {
            updateNeeded = false;
            LOG.debug("Updating {} in response to changes", this);
            LOG.info("Updating {}, members {} with address {}", new Object[] {this, serverPoolTargets, serverPoolAddresses});
            reconfigureService();
            LOG.debug("Reloading {} in response to changes", this);
            // reload should happen synchronously
            result = invoke(RELOAD);
        }
        setAttribute(SERVER_POOL_TARGETS, serverPoolAddresses);
        return result;
    }

    protected synchronized void resetServerPoolMemberTrackerPolicy() {
        serverPoolMemberTrackerPolicy.reset();
        serverPoolAddresses.clear();
        serverPoolTargets.clear();
        if (groovyTruth(getServerPool())) {
            serverPoolMemberTrackerPolicy.setGroup(getServerPool());
            
            // Initialize ourselves immediately with the latest set of members; don't wait for
            // listener notifications because then will be out-of-date for short period (causing 
            // problems for rebind)
            for (Entity member : getServerPool().getMembers()) {
                if (belongsInServerPool(member)) {
                    if (LOG.isTraceEnabled()) LOG.trace("Done {} checkEntity {}", this, member);
                    String address = getAddressOfEntity(member);
                    serverPoolTargets.put(member, address);
                    if (address != null) {
                        serverPoolAddresses.add(address);
                    }
                }
            }
            
            LOG.info("Resetting {}, members {} with address {}", new Object[] {this, serverPoolTargets, serverPoolAddresses});
        }
        
        setAttribute(SERVER_POOL_TARGETS, serverPoolAddresses);
    }

    protected synchronized void onServerPoolMemberChanged(Entity member) {
        if (LOG.isTraceEnabled()) LOG.trace("For {}, considering membership of {} which is in locations {}", 
                new Object[] {this, member, member.getLocations()});
        if (belongsInServerPool(member)) {
            addServerPoolMember(member);
        } else {
            removeServerPoolMember(member);
        }
        if (LOG.isTraceEnabled()) LOG.trace("Done {} checkEntity {}", this, member);
    }
    
    protected boolean belongsInServerPool(Entity member) {
        if (!groovyTruth(member.getAttribute(Startable.SERVICE_UP))) {
            if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, eliminating because not up", this, member);
            return false;
        }
        if (!getServerPool().getMembers().contains(member)) {
            if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, eliminating because not member", this, member);
            return false;
        }
        if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, approving", this, member);
        return true;
    }
    
    protected synchronized void addServerPoolMember(Entity member) {
        String oldAddress = serverPoolTargets.get(member);
        String newAddress = getAddressOfEntity(member);
        if (newAddress == null) {
            if (oldAddress != null) {
                LOG.info("Removing from {}, member {} with old address {}, because inferred address is now null", new Object[] {this, member, oldAddress});
                serverPoolAddresses.remove(oldAddress);
            }
        } else if (Objects.equal(newAddress, oldAddress)) {
            if (LOG.isTraceEnabled())
                LOG.trace("Ignoring unchanged address "+oldAddress);
            return;
        } else {
            if (oldAddress != null) {
                LOG.info("Replacing in {}, member {} with old address {}, new address {}", new Object[] {this, member, oldAddress, newAddress});
                serverPoolAddresses.remove(oldAddress);
            } else {
                LOG.info("Adding to {}, new member {} with address {}", new Object[] {this, member, newAddress});
            }
            serverPoolAddresses.add(newAddress);
        }
        
        if (Objects.equal(oldAddress, newAddress)) {
            if (LOG.isTraceEnabled()) LOG.trace("For {}, ignoring change in member {} because address still {}", new Object[] {this, member, newAddress});
            return;
        }
        
        // TODO this does it synchronously; an async method leaning on `updateNeeded` and `update` might
        // be more appropriate, especially when this is used in a listener
        updateAsync();
        serverPoolTargets.put(member, newAddress);
    }
    
    protected synchronized void removeServerPoolMember(Entity member) {
        if (!serverPoolTargets.containsKey(member)) {
            if (LOG.isTraceEnabled()) LOG.trace("For {}, not removing as don't have member {}", new Object[] {this, member});
            return;
        }
        
        String address = serverPoolTargets.get(member);
        if (address != null) {
            serverPoolAddresses.remove(address);
        }
        
        LOG.info("Removing from {}, member {} with address {}", new Object[] {this, member, address});
        
        updateAsync();
        serverPoolTargets.remove(member);
    }
    
    protected String getAddressOfEntity(Entity member) {
        AttributeSensor<String> hostAndPortSensor = getHostAndPortSensor();
        if (hostAndPortSensor != null) {
            String result = member.getAttribute(hostAndPortSensor);
            if (result != null) {
                return result;
            } else {
                LOG.error("No host:port set for {} (using attribute {}); skipping in {}", 
                        new Object[] {member, hostAndPortSensor, this});
                return null;
            }
        } else {
            String ip = member.getAttribute(getHostnameSensor());
            Integer port = member.getAttribute(getPortNumberSensor());
            if (ip!=null && port!=null) {
                return ip+":"+port;
            }
            LOG.error("Unable to construct hostname:port representation for {} ({}:{}); skipping in {}", 
                    new Object[] {member, ip, port, this});
            return null;
        }
    }

    @Override
    public RebindSupport<EntityMemento> getRebindSupport() {
        return new BasicEntityRebindSupport(this) {
            @Override public EntityMemento getMemento() {
                // Note: using MutableMap so accepts nulls
                Map<String, Object> flags = Maps.newLinkedHashMap();
                flags.put("serverPoolAddresses", serverPoolAddresses);
                flags.put("serverPoolTargets", serverPoolTargets);
                return super.getMementoWithProperties(flags);
            }
            @SuppressWarnings({ "unchecked", "serial" })
            @Override protected void doReconstruct(RebindContext rebindContext, EntityMemento memento) {
                super.doReconstruct(rebindContext, memento);
                // TODO If pool-target entity couldn't be resolved, then  serverPoolAddresses and serverPoolTargets
                // will be out-of-sync (for ever more?)
                serverPoolAddresses.addAll((Collection<String>) memento.getCustomField("serverPoolAddresses"));
                serverPoolTargets.putAll((Map<Entity,String>) memento.getCustomField("serverPoolTargets"));
            }
        };
    }

    private final Function<Entity, String> entityIdFunction = new Function<Entity, String>() {
        @Override
        @Nullable
        public String apply(@Nullable Entity input) {
            return (input != null) ? input.getId() : null;
        }
    };
}
