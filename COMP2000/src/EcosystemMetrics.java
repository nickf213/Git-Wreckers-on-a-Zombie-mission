import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Computes ecosystem health metrics from the simulation world using streams
 * and collectors. Demonstrates:
 * <ul>
 *     <li>{@code Collectors.groupingBy} — species to population count</li>
 *     <li>{@code Collectors.summarizingDouble} — min/max/avg energy per species</li>
 *     <li>{@link PopulationTimeSeriesCollector} — custom collector that
 *         accumulates tick-by-tick population data into a time series</li>
 * </ul>
 *
 * Usage: call {@link #recordTick(World)} each simulation tick to accumulate
 * data, then query with the getter methods.
 */
public class EcosystemMetrics {

    private final List<Map<String, Long>> populationHistory = new ArrayList<>();
    private final List<Map<String, DoubleSummaryStatistics>> energyHistory = new ArrayList<>();
    private long lastRecordedTick = -1;

    /**
     * Snapshots population counts and energy statistics per species using
     * {@code Collectors.groupingBy} and {@code Collectors.summarizingDouble}.
     */
    public void recordTick(World world) {
        if (world.getTick() == lastRecordedTick) {
            return;
        }
        lastRecordedTick = world.getTick();

        List<Entity> entities = world.getEntities();

        Map<String, Long> populationBySpecies = entities.stream()
                .filter(Entity::isActive)
                .collect(Collectors.groupingBy(
                        entity -> entity.getClass().getSimpleName(),
                        Collectors.counting()
                ));
        populationHistory.add(populationBySpecies);

        Map<String, DoubleSummaryStatistics> energyBySpecies = entities.stream()
                .filter(Entity::isActive)
                .filter(entity -> entity instanceof Human)
                .map(entity -> (Human) entity)
                .collect(Collectors.groupingBy(
                        human -> human.getClass().getSimpleName(),
                        Collectors.summarizingDouble(Human::getEnergy)
                ));
        energyHistory.add(energyBySpecies);
    }

    /** Species-to-population-count map for the most recent tick. */
    public Map<String, Long> getCurrentPopulations() {
        if (populationHistory.isEmpty()) {
            return Collections.emptyMap();
        }
        return populationHistory.get(populationHistory.size() - 1);
    }

    /** Energy statistics (min, max, average) per species for the most recent tick. */
    public Map<String, DoubleSummaryStatistics> getCurrentEnergyStats() {
        if (energyHistory.isEmpty()) {
            return Collections.emptyMap();
        }
        return energyHistory.get(energyHistory.size() - 1);
    }

    /**
     * Uses the custom {@link PopulationTimeSeriesCollector} to produce a
     * time-series mapping each species to a list of its count at each
     * recorded tick.
     */
    public Map<String, List<Long>> getPopulationTimeSeries() {
        return populationHistory.stream()
                .collect(new PopulationTimeSeriesCollector());
    }

    /**
     * Computes an ecosystem health score from the latest tick.
     * Higher when humans survive, zombies are contained, and resources exist.
     */
    public double getHealthScore() {
        Map<String, Long> populations = getCurrentPopulations();
        long humans = populations.getOrDefault("Human", 0L);
        long zombies = populations.getOrDefault("Zombie", 0L);
        long military = populations.getOrDefault("Military", 0L);
        long food = populations.getOrDefault("Food", 0L);

        double humanSurvival = Math.min(humans, 20) / 20.0;
        double zombieContainment = 1.0 / (1.0 + zombies);
        double resourceAvailability = Math.min(food, 10) / 10.0;
        double defenseStrength = Math.min(military, 5) / 5.0;

        return (humanSurvival * 0.4
                + zombieContainment * 0.3
                + resourceAvailability * 0.15
                + defenseStrength * 0.15) * 100.0;
    }

    /**
     * Infection rate: zombies as a percentage of all living entities
     * (humans + zombies + military).
     */
    public double getInfectionRate() {
        Map<String, Long> populations = getCurrentPopulations();
        long humans = populations.getOrDefault("Human", 0L);
        long zombies = populations.getOrDefault("Zombie", 0L);
        long military = populations.getOrDefault("Military", 0L);

        long total = humans + zombies + military;
        if (total == 0) {
            return 0.0;
        }
        return (zombies * 100.0) / total;
    }

    public int getRecordedTicks() {
        return populationHistory.size();
    }

    /**
     * Custom collector that accumulates a stream of per-tick population maps
     * into a time series: species name to list of counts across all ticks.
     * Implements the full {@link Collector} interface (supplier, accumulator,
     * combiner, finisher, characteristics) and supports parallel streams.
     */
    static class PopulationTimeSeriesCollector implements
            Collector<Map<String, Long>,
                    Map<String, List<Long>>,
                    Map<String, List<Long>>> {

        @Override
        public Supplier<Map<String, List<Long>>> supplier() {
            return HashMap::new;
        }

        @Override
        public BiConsumer<Map<String, List<Long>>, Map<String, Long>> accumulator() {
            return (timeSeries, tickData) ->
                    tickData.forEach((species, count) ->
                            timeSeries.computeIfAbsent(species, k -> new ArrayList<>())
                                    .add(count));
        }

        @Override
        public BinaryOperator<Map<String, List<Long>>> combiner() {
            return (left, right) -> {
                right.forEach((species, counts) ->
                        left.merge(species, counts, (existing, incoming) -> {
                            existing.addAll(incoming);
                            return existing;
                        }));
                return left;
            };
        }

        @Override
        public Function<Map<String, List<Long>>, Map<String, List<Long>>> finisher() {
            return Function.identity();
        }

        @Override
        public Set<Characteristics> characteristics() {
            return EnumSet.of(Characteristics.IDENTITY_FINISH);
        }
    }
}
