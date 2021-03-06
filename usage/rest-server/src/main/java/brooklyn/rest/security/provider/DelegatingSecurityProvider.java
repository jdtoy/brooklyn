package brooklyn.rest.security.provider;

import java.lang.reflect.Constructor;

import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.StringConfigMap;
import brooklyn.management.ManagementContext;
import brooklyn.rest.BrooklynWebConfig;
import brooklyn.util.text.Strings;

public class DelegatingSecurityProvider implements SecurityProvider {

    public static final Logger log = LoggerFactory.getLogger(DelegatingSecurityProvider.class);
    protected final ManagementContext mgmt;

    public DelegatingSecurityProvider(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }
    
    private SecurityProvider targetProvider;

    @SuppressWarnings("unchecked")
    public synchronized SecurityProvider getTargetProvider() {
        if (this.targetProvider!=null) {
            return targetProvider;
        }

        StringConfigMap brooklynProperties = mgmt.getConfig();

        String className = brooklynProperties.getConfig(BrooklynWebConfig.SECURITY_PROVIDER_CLASSNAME);
        log.info("Web console using security provider "+className);

        try {
            Class<? extends SecurityProvider> clazz;
            try {
                clazz = (Class<? extends SecurityProvider>)Class.forName(className);
            } catch (Exception e) {
                String OLD_PACKAGE = "brooklyn.web.console.security.";
                if (className.startsWith(OLD_PACKAGE)) {
                    className = Strings.removeFromStart(className, OLD_PACKAGE);
                    className = DelegatingSecurityProvider.class.getPackage().getName()+"."+className;
                    clazz = (Class<? extends SecurityProvider>)Class.forName(className);
                    log.warn("Deprecated package "+OLD_PACKAGE+" detected; please update security provider to point to "+className);
                } else throw e;
            }
            
            Constructor<? extends SecurityProvider> constructor;
            try {
                constructor = clazz.getConstructor(ManagementContext.class);
                targetProvider = constructor.newInstance(mgmt);
            } catch (Exception e) {
                constructor = clazz.getConstructor();
                targetProvider = constructor.newInstance();
            }
        } catch (Exception e) {
            log.warn("Web console unable to instantiate security provider "+className+"; all logins are being disallowed",e);
            targetProvider = new BlackholeSecurityProvider();
        }
        return targetProvider;
    }
    
    @Override
    public boolean isAuthenticated(HttpSession session) {
        return getTargetProvider().isAuthenticated(session);
    }

    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
        return getTargetProvider().authenticate(session, user, password);
    }

    @Override
    public boolean logout(HttpSession session) { 
        return getTargetProvider().logout(session);
    }
}
