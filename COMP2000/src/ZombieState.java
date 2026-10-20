/**
 * State interface for the State design pattern applied to Zombie behaviour.
 * Each state encapsulates a distinct mode of behaviour (wandering, chasing,
 * feeding) and decides when to transition to another state.
 */
public interface ZombieState {

    void execute(Zombie zombie, World world);

    String describe();
}
