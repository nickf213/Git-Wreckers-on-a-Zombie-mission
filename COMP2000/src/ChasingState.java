/**
 * Zombie chases its target and infects on contact. Transitions back to
 * {@link WanderingState} when the target is lost, hidden, or deactivated.
 */
public class ChasingState implements ZombieState {

    @Override
    public void execute(Zombie zombie, World world) {
        Human target = zombie.getTarget();

        if (target == null || !target.isActive() || target.isHidden()
                || zombie.distanceTo(target) > Zombie.DETECTION_RADIUS) {
            zombie.setTarget(null);
            zombie.setState(new WanderingState());
            zombie.wander(world);
            return;
        }

        zombie.chase(world);

        if (zombie.distanceTo(target) <= Zombie.INFECT_DISTANCE) {
            target.infect();
            zombie.setTarget(null);
            zombie.setState(new WanderingState());
        }
    }

    @Override
    public String describe() {
        return "Chasing";
    }
}
