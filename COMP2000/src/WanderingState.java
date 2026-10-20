import java.util.Comparator;

/**
 * Zombie wanders randomly until a visible civilian enters detection range,
 * then transitions to {@link ChasingState}.
 */
public class WanderingState implements ZombieState {

    @Override
    public void execute(Zombie zombie, World world) {
        Human target = findTarget(zombie, world);

        if (target != null) {
            zombie.setTarget(target);
            zombie.setState(new ChasingState());
            zombie.getState().execute(zombie, world);
            return;
        }

        zombie.wander(world);
    }

    private Human findTarget(Zombie zombie, World world) {
        return world.getNearby(zombie, Zombie.DETECTION_RADIUS).stream()
                .filter(entity -> entity instanceof Human
                        && !(entity instanceof Military))
                .map(entity -> (Human) entity)
                .filter(human -> !human.isHidden())
                .min(Comparator.comparingDouble(zombie::distanceTo))
                .orElse(null);
    }

    @Override
    public String describe() {
        return "Wandering";
    }
}
