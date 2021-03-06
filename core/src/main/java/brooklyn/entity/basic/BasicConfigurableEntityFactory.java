package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import brooklyn.entity.Entity;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;

public class BasicConfigurableEntityFactory<T extends Entity> extends AbstractConfigurableEntityFactory<T> {
    private transient Class<? extends T> clazz;
    private final String clazzName;

    public BasicConfigurableEntityFactory(Class<? extends T> clazz) {
        this(new HashMap(), clazz);
    }

    public BasicConfigurableEntityFactory(Map flags, Class<? extends T> clazz) {
        super(flags);
        this.clazz = checkNotNull(clazz, "clazz");
        this.clazzName = clazz.getName();
    }

    public T newEntity2(Map flags, Entity parent) {
        try {
            Constructor<? extends T> constructor = clazz.getConstructor(Map.class, Entity.class);
            return constructor.newInstance(flags, parent);
        } catch (InstantiationException e) {
            throw Throwables.propagate(e);
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        } catch (InvocationTargetException e) {
            throw Throwables.propagate(e);
        } catch (NoSuchMethodException e) {
            throw Throwables.propagate(e);
        }
    }
    
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        clazz = (Class<T>) getClass().getClassLoader().loadClass(clazzName);
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("type", clazzName).toString();
    }
}
