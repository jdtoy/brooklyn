package brooklyn.event;

/**
 * A listener for {@link SensorEvent}s on an {@link Entity}.
 */
public interface SensorEventListener<T> {
    /**
     * The {@link SensorEvent} handler method.
     */
    void onEvent(SensorEvent<T> event);
}
