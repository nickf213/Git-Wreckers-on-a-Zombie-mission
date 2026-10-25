import java.util.Comparator;

/**
 * Zombie wanders randomly until a visible civilian enters detection range,
 * then transitions to {@link ChasingState}.
 */
public class WanderingState implements ZombieState {

    @Override
    public void execute(Zombie zombie, World world) {
        try {
            Human target = findTarget(zombie, world);
            zombie.setTarget(target);
            zombie.setState(new ChasingState());
            zombie.getState().execute(zombie, world);
        } catch (TargetNotFoundException exception) {
            zombie.wander(world);
        }
    }

    private Human findTarget(Zombie zombie, World world)
            throws TargetNotFoundException {
        return zombie.visibleCivilians(world)
                .min(Comparator.comparingDouble(zombie::distanceTo))
                .orElseThrow(() -> new TargetNotFoundException(
                        "No visible civilian in range"));
    }

    @Override
    public String describe() {
        return "Wandering";
    }
}
