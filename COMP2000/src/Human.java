import java.awt.Graphics2D;
import java.awt.Image;
import java.io.File;
import java.net.URL;
import java.util.Comparator;
import java.util.Random;
import javax.swing.ImageIcon;

/**
 * A person who expends energy, eats food, and can temporarily shelter inside a
 * building. The supplied front, side, and back PNGs are selected from the
 * human's movement direction.
 */
public class Human extends Entity {

    private static final double MAX_ENERGY = 100.0;
    private static final double ENERGY_LOSS_PER_TICK = 0.20;
    private static final double FOOD_SEARCH_RADIUS = 250.0;
    private static final double LEAVE_SHELTER_ENERGY = 35.0;
    private static final double SPEED = 2.0;
    private static final double BUILDING_ENTRY_DISTANCE = SPEED;
    private static final int CHARACTER_WIDTH = 38;
    private static final int CHARACTER_HEIGHT = 54;
    private static final int TICKS_PER_WALK_FRAME = 8;

    private final Random random = new Random();
    private double energy;
    private double directionX;
    private double directionY;
    private int wanderTicksRemaining;
    private final Image[] frontWalkFrames;
    private final Image[] sideWalkFrames;
    private final Image frontIdleFrame;
    private final Image sideIdleFrame;
    private final Image backFrame;
    private int walkFrame;
    private int ticksOnCurrentFrame;
    private boolean facingLeft;
    private Facing facing = Facing.FRONT;
    private boolean moving;
    private boolean hidden;
    private Building shelter;

    public Human(double x, double y) {
        super(x, y);
        energy = MAX_ENERGY;
        frontWalkFrames = new Image[]{
                loadImage("/HumanFoodAssets/HumanFrontWalk1.png"),
                loadImage("/HumanFoodAssets/HumanFrontWalk2.png"),
                loadImage("/HumanFoodAssets/HumanFrontWalk3.png"),
                loadImage("/HumanFoodAssets/HumanFrontWalk4.png")
        };
        sideWalkFrames = new Image[]{
                loadImage("/HumanFoodAssets/HumanWalk1.png"),
                loadImage("/HumanFoodAssets/HumanWalk2.png"),
                loadImage("/HumanFoodAssets/HumanWalk3.png")
        };
        frontIdleFrame = loadImage("/HumanFoodAssets/HumanFrontIdle.png");
        sideIdleFrame = loadImage("/HumanFoodAssets/HumanSideIdle.png");
        backFrame = loadImage("/HumanFoodAssets/HumanBack.png");
        walkFrame = 0;
        chooseNewWanderDirection();
    }

    public double getEnergy() {
        return energy;
    }

    /** Returns whether this human is currently inside a building. */
    public boolean isHidden() {
        return hidden;
    }

    /** Returns the building currently sheltering this human, if any. */
    public Building getShelter() {
        return shelter;
    }

    /**
     * Attempts to enter a building with available capacity. A hidden human is
     * not drawn and should be ignored by zombie target-selection code.
     */
    // REVIEW: building.enter() runs before leaveBuilding(), so a human already sheltered elsewhere
    // is moved to the old entrance while hidden. Calling leaveBuilding() first (and restoring on failure) avoids that.
    public boolean enterBuilding(Building building) {
        if (building == null || !isActive()) {
            return false;
        }

        if (shelter == building) {
            return true;
        }

        if (!building.enter()) {
            return false;
        }

        leaveBuilding();
        shelter = building;
        hidden = true;
        return true;
    }

    /** Leaves the current shelter and places the human at its entrance. */
    public void leaveBuilding() {
        if (shelter == null) {
            return;
        }

        Building previousShelter = shelter;
        shelter = null;
        hidden = false;
        previousShelter.leave();
        setPosition(previousShelter.getEntranceX(), previousShelter.getEntranceY());
    }

    /** Called by a zombie when it catches this human. */
    public void infect() {
        leaveBuilding();
        deactivate();
    }

    @Override
    public void update(World world) {
        energy -= ENERGY_LOSS_PER_TICK;
        if (energy <= 0) {
            leaveBuilding();
            deactivate();
            return;
        }

        // Sheltering protects the human, but food scarcity eventually forces
        // them back into the world where normal food-seeking can resume.
        if (hidden) {
            if (energy > LEAVE_SHELTER_ENERGY) {
                return;
            }
            leaveBuilding();
        }

        // While they are still healthy, humans seek the closest available
        // shelter. Once their energy is low they leave shelter to find food.
        if (energy > LEAVE_SHELTER_ENERGY) {
            Building building = findNearestAvailableBuilding(world);
            if (building != null) {
                moveTowards(building.getEntranceX(), building.getEntranceY(), world);
                if (distanceTo(building.getEntranceX(), building.getEntranceY())
                        <= BUILDING_ENTRY_DISTANCE) {
                    enterBuilding(building);
                }
                return;
            }
        }

        try {
            Food food = findNearestFood(world);
            moveTowards(food.getX(), food.getY(), world);
            if (distanceTo(food) <= Food.EAT_DISTANCE) {
                eat(food);
            }
        } catch (TargetNotFoundException exception) {
            wander(world);
        }
    }

    /** Consumes an active food item when close enough to it. */
    public boolean eat(Food food) {
        if (food == null || !food.isActive() || distanceTo(food) > Food.EAT_DISTANCE) {
            return false;
        }

        energy = Math.min(MAX_ENERGY, energy + food.getEnergyValue());
        food.consume();
        return true;
    }

    private Food findNearestFood(World world) throws TargetNotFoundException {
        return findNearestEntity(world, FOOD_SEARCH_RADIUS, Food.class);
    }

    private <T extends Entity> T findNearestEntity(World world,
                                                   double radius,
                                                   Class<T> entityType)
            throws TargetNotFoundException {
        return world.getNearby(this, radius).stream()
                .filter(entityType::isInstance)
                .map(entityType::cast)
                .min(Comparator.comparingDouble(this::distanceTo))
                .orElseThrow(() -> new TargetNotFoundException(
                        "No nearby " + entityType.getSimpleName() + " target found"
                ));
    }

    private Building findNearestAvailableBuilding(World world) {
        return world.getEntities().stream()
                .filter(entity -> entity instanceof Building)
                .map(entity -> (Building) entity)
                .filter(Building::canEnter)
                .min(Comparator.comparingDouble(this::distanceTo))
                .orElse(null);
    }

    private double distanceTo(double targetX, double targetY) {
        return Math.hypot(targetX - getX(), targetY - getY());
    }

    private void wander(World world) {
        if (wanderTicksRemaining-- <= 0) {
            chooseNewWanderDirection();
        }
        move(directionX, directionY, world);
    }

    private void chooseNewWanderDirection() {
        double angle = random.nextDouble() * Math.PI * 2;
        directionX = Math.cos(angle);
        directionY = Math.sin(angle);
        wanderTicksRemaining = 20 + random.nextInt(50);
    }

    protected void moveTowards(double targetX, double targetY, World world) {
        double xDifference = targetX - getX();
        double yDifference = targetY - getY();
        double distance = Math.hypot(xDifference, yDifference);

        if (distance > 0) {
            move(xDifference / distance, yDifference / distance, world);
        }
    }

    private void move(double xDirection, double yDirection, World world) {
        moving = Math.abs(xDirection) > 0.01 || Math.abs(yDirection) > 0.01;
        if (Math.abs(yDirection) > Math.abs(xDirection)) {
            facing = yDirection < 0 ? Facing.BACK : Facing.FRONT;
        } else if (Math.abs(xDirection) > 0.01) {
            facing = Facing.SIDE;
            facingLeft = xDirection < 0;
        }

        double nextX = getX() + xDirection * SPEED;
        double nextY = getY() + yDirection * SPEED;
        nextX = Math.max(CHARACTER_WIDTH / 2.0,
                Math.min(world.getWidth() - CHARACTER_WIDTH / 2.0, nextX));
        nextY = Math.max(CHARACTER_HEIGHT / 2.0,
                Math.min(world.getHeight() - CHARACTER_HEIGHT / 2.0, nextY));
        setPosition(nextX, nextY);
        advanceWalkAnimation();
    }

    // REVIEW: the src / COMP2000/src fallback is copy-pasted in Human, Food, Building, SimPanel and Zombie. A shared asset-loading helper would remove four copies.
    private Image loadImage(String imagePath) {
        URL imageUrl = Human.class.getResource(imagePath);
        if (imageUrl != null) {
            return new ImageIcon(imageUrl).getImage();
        }

        // When Java is run with only the generated out directory on its
        // classpath, image assets remain in src rather than being duplicated.
        String fileName = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
        File sourceAsset = new File("src", fileName);
        if (!sourceAsset.isFile()) {
            sourceAsset = new File("COMP2000/src", fileName);
        }
        if (!sourceAsset.isFile()) {
            throw new IllegalStateException(imagePath + " was not found");
        }
        return new ImageIcon(sourceAsset.getAbsolutePath()).getImage();
    }

    private void advanceWalkAnimation() {
        ticksOnCurrentFrame++;
        if (ticksOnCurrentFrame >= TICKS_PER_WALK_FRAME) {
            // Twelve is a shared cycle for four front frames and three side frames.
            walkFrame = (walkFrame + 1) % 12;
            ticksOnCurrentFrame = 0;
        }
    }

    @Override
    public void draw(Graphics2D graphics) {
        if (hidden) {
            return;
        }

        Image frame = getCurrentFrame();
        int left = (int) getX() - CHARACTER_WIDTH / 2;
        int top = (int) getY() - CHARACTER_HEIGHT / 2
                - (moving && walkFrame % 2 == 1 ? 2 : 0);

        if (facing == Facing.SIDE && facingLeft) {
            // A negative width mirrors the current walking frame horizontally.
            graphics.drawImage(frame, left + CHARACTER_WIDTH, top,
                    -CHARACTER_WIDTH, CHARACTER_HEIGHT, null);
        } else {
            graphics.drawImage(frame, left, top,
                    CHARACTER_WIDTH, CHARACTER_HEIGHT, null);
        }
    }

    private Image getCurrentFrame() {
        if (!moving) {
            if (facing == Facing.SIDE) {
                return sideIdleFrame;
            }
            return facing == Facing.BACK ? backFrame : frontIdleFrame;
        }

        if (facing == Facing.SIDE) {
            return sideWalkFrames[walkFrame % sideWalkFrames.length];
        }
        if (facing == Facing.BACK) {
            return backFrame;
        }
        return frontWalkFrames[walkFrame % frontWalkFrames.length];
    }

    // REVIEW: Facing is also defined in Zombie and Military. A shared enum or base class would remove the duplication.
    private enum Facing {
        FRONT,
        SIDE,
        BACK
    }
}
