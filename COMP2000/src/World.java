import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class World {

    private static final int FOOD_SPAWN_INTERVAL_TICKS = 25;
    private static final int HUMAN_SPAWN_INTERVAL_TICKS = 100;
    private static final double FOOD_ENERGY_VALUE = 45.0;

    private final List<Entity> entities;
    private final int width;
    private final int height;
    private final Random random;
    private long tick;

    public World(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "World dimensions must be positive"
            );
        }

        this.width = width;
        this.height = height;
        this.entities = new ArrayList<>();
        this.random = new Random();
        this.tick = 0;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getTick() {
        return tick;
    }

    public void addEntity(Entity entity) {
        entities.add(Objects.requireNonNull(
                entity,
                "Entity cannot be null"
        ));
    }

    public List<Entity> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    // REVIEW: this loop is a filter pipeline: entities.stream().filter(e -> e != source && e.isActive()
    // && source.distanceTo(e) <= radius).toList(). Note World is not thread-safe, so any parallel
    // stream over it must only read, and only between ticks.
    public List<Entity> getNearby(Entity source, double radius) {
        Objects.requireNonNull(source, "Source entity cannot be null");

        if (radius < 0) {
            throw new IllegalArgumentException(
                    "Radius cannot be negative"
            );
        }

        List<Entity> nearbyEntities = new ArrayList<>();

        for (Entity entity : entities) {
            if (entity == source) {
                continue;
            }

            if (!entity.isActive()) {
                continue;
            }

            if (source.distanceTo(entity) <= radius) {
                nearbyEntities.add(entity);
            }
        }

        return nearbyEntities;
    }

    public void update() {
        for(Entity entity : List.copyOf(entities)) {
            if(entity.isActive()) {
                entity.update(this);
            }
        }

        removeInactiveEntities();
        tick++;
        spawnEntities();
    }

    public void removeInactiveEntities() {
        entities.removeIf(entity -> !entity.isActive());
    }

    /** Clears the simulation state so the caller can build a fresh scenario. */
    public void reset() {
        entities.clear();
        tick = 0;
    }

    /** Adds food and humans at random locations at regular simulation intervals. */
    private void spawnEntities() {
        if (tick % FOOD_SPAWN_INTERVAL_TICKS == 0) {
            addEntity(new Food(random.nextDouble() * width,
                    random.nextDouble() * height, FOOD_ENERGY_VALUE));
        }

        if (tick % HUMAN_SPAWN_INTERVAL_TICKS == 0) {
            addEntity(new Human(random.nextDouble() * width,
                    random.nextDouble() * height));
        }
    }

    
}
