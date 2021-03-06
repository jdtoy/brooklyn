package brooklyn.rest.resources;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.Task;
import brooklyn.management.internal.EffectorUtils;
import brooklyn.rest.api.EffectorApi;
import brooklyn.rest.domain.EffectorSummary;
import brooklyn.rest.transform.EffectorTransformer;
import brooklyn.rest.transform.TaskTransformer;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

public class EffectorResource extends AbstractBrooklynRestResource implements EffectorApi {

    private static final Logger log = LoggerFactory.getLogger(EffectorResource.class);
    private static final Comparator<EffectorSummary> SUMMARY_NAME_COMPARATOR = new Comparator<EffectorSummary>() {
        @Override public int compare(EffectorSummary o1, EffectorSummary o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };

   @Override
  public List<EffectorSummary> list(final String application, final String entityToken) {
       final EntityLocal entity = brooklyn().getEntity(application, entityToken);
       return FluentIterable.from(entity.getEntityType().getEffectors())
            .transform(new Function<Effector<?>, EffectorSummary>() {
                @Override
                public EffectorSummary apply(Effector<?> effector) {
                    return EffectorTransformer.effectorSummary(entity, effector);
                }
            })
           .toSortedList(SUMMARY_NAME_COMPARATOR);
   }

    @Override
  public Response invoke(String application, String entityToken, String effectorName,
      String timeout, Map<String, String> parameters
  ) {
      final EntityLocal entity = brooklyn().getEntity(application, entityToken);

    // TODO check effectors?
    Maybe<Effector<?>> effector = EffectorUtils.findEffectorDeclared(entity, effectorName);
    if (effector.isAbsentOrNull())
      throw WebResourceUtils.notFound("Entity '%s' has no effector with name '%s'", entityToken, effectorName);

    log.info("REST invocation of "+entity+"."+effector.get()+" "+parameters);
    Task<?> t = entity.invoke(effector.get(), parameters);
    
    try {
        Object result = null;
        if (timeout==null || timeout.isEmpty() || "never".equalsIgnoreCase(timeout)) {
            result = t.get();
        } else {
            long timeoutMillis = "always".equalsIgnoreCase(timeout) ? 0 : Time.parseTimeString(timeout);
            try {
                if (timeoutMillis==0) throw new TimeoutException();
                result = t.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                return Response.status(Response.Status.ACCEPTED).entity(TaskTransformer.taskSummary(t)).build();
            }
        }
        return Response.status(Response.Status.ACCEPTED).entity( result ).build();
    } catch (Exception e) {
        throw Exceptions.propagate(e);
    }
  }

}
