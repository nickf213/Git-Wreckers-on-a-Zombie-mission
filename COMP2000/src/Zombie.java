import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Comparator;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * A zombie that wanders, chases visible civilians, and infects on contact.
 * Uses the State design pattern: behaviour is delegated to the current
 * {@link Mode}, whose lambda behaviour handles transitions between modes.
 *
 * <p>Why State: the zombie's behaviour changes at runtime based on what it
 * sees, and each mode decides its own next mode. Alternatives considered:
 * an if/else on a flag (the original code; grows messy with each new mode),
 * an enum with a switch (still one big method), and Strategy as used by
 * Military (fits swapping an algorithm, but the strategy does not decide
 * when to switch, which is the core need here).
 */
public class Zombie extends Entity {

    private static final double CHASE_SPEED = 1.4;
    private static final double WANDER_SPEED = 0.6;
    private static final double DETECTION_RADIUS = 300.0;
    private static final double INFECT_DISTANCE = 12.0;
    private static final int WIDTH = 38;
    private static final int HEIGHT = 54;
    private static final int FRAME_TICKS = 8;

    private final Random random = new Random();

    private final Image frontIdle;
    private final Image[] frontWalk;
    private final Image[] sideWalk;
    private final Image backIdle;

    private Human target;
    private double directionX;
    private double directionY;
    private int wanderTicks;
    private int animationTicks;
    private int animationFrame;
    private boolean moving;
    private boolean facingLeft;
    private Facing facing = Facing.FRONT;
    private Mode mode = Mode.WANDERING;

    public Zombie(double x, double y) {
        super(x, y);

        BufferedImage sheet = loadSpriteSheet();

        int frameWidth = sheet.getWidth() / 5;
        int frameHeight = sheet.getHeight() / 2;

        frontIdle = crop(sheet, 0, 0, frameWidth, frameHeight);

        frontWalk = new Image[4];
        for (int column = 0; column < frontWalk.length; column++) {
            frontWalk[column] = crop(
                    sheet,
                    column + 1,
                    0,
                    frameWidth,
                    frameHeight
            );
        }

        sideWalk = new Image[4];
        for (int column = 0; column < sideWalk.length; column++) {
            sideWalk[column] = crop(
                    sheet,
                    column,
                    1,
                    frameWidth,
                    frameHeight
            );
        }

        backIdle = crop(sheet, 4, 1, frameWidth, frameHeight);

        chooseNewDirection();
    }

    @Override
    public void update(World world) {
        mode.behaviour.accept(this, world);
    }

    /**
     * Civilians this zombie can currently see: active, not soldiers, not
     * sheltering. Returned as a lazy stream so callers choose the terminal
     * operation (min for targeting, count for threat level, and so on).
     */
    Stream<Human> visibleCivilians(World world) {
        return world.getNearby(this, DETECTION_RADIUS).stream()
                .filter(entity -> entity instanceof Human
                        && !(entity instanceof Military))
                .map(entity -> (Human) entity)
                .filter(human -> !human.isHidden());
    }

    /** Closest visible civilian, or TargetNotFoundException if none is in view. */
    Human findTarget(World world) throws TargetNotFoundException {
        return visibleCivilians(world)
                .min(Comparator.comparingDouble(this::distanceTo))
                .orElseThrow(() -> new TargetNotFoundException(
                        "No visible civilian in range"));
    }

    /** Number of civilians currently in view, counted with a stream. */
    long countVisibleCivilians(World world) {
        return visibleCivilians(world).count();
    }

    private void startChase(Human newTarget, World world) {
        target = newTarget;
        mode = Mode.CHASING;
        mode.behaviour.accept(this, world);
    }

    private void stopChase() {
        target = null;
        mode = Mode.WANDERING;
    }

    private void chase(World world) {
        double xDifference = target.getX() - getX();
        double yDifference = target.getY() - getY();
        double distance = Math.hypot(
                xDifference,
                yDifference
        );

        if (distance > 0) {
            move(
                    xDifference / distance,
                    yDifference / distance,
                    CHASE_SPEED,
                    world
            );
        }
    }

    private void wander(World world) {
        if (wanderTicks-- <= 0) {
            chooseNewDirection();
        }

        move(
                directionX,
                directionY,
                WANDER_SPEED,
                world
        );
    }

    private void chooseNewDirection() {
        double angle = random.nextDouble() * Math.PI * 2;

        directionX = Math.cos(angle);
        directionY = Math.sin(angle);

        wanderTicks = 50 + random.nextInt(30);
    }

    private void move(
            double xDirection,
            double yDirection,
            double speed,
            World world
    ) {
        moving = Math.abs(xDirection) > 0.01
                || Math.abs(yDirection) > 0.01;

        if (Math.abs(xDirection) > Math.abs(yDirection)) {
            facing = Facing.SIDE;
            facingLeft = xDirection < 0;
        } else if (Math.abs(yDirection) > 0.01) {
            facing = yDirection < 0
                    ? Facing.BACK
                    : Facing.FRONT;
        }

        double nextX = getX() + xDirection * speed;
        double nextY = getY() + yDirection * speed;

        nextX = Math.max(
                WIDTH / 2.0,
                Math.min(
                        world.getWidth() - WIDTH / 2.0,
                        nextX
                )
        );

        nextY = Math.max(
                HEIGHT / 2.0,
                Math.min(
                        world.getHeight() - HEIGHT / 2.0,
                        nextY
                )
        );

        setPosition(nextX, nextY);

        animationTicks++;

        if (animationTicks >= FRAME_TICKS) {
            animationFrame++;
            animationTicks = 0;
        }
    }

    @Override
    public void draw(Graphics2D graphics) {
        Image frame = currentFrame();

        int left = (int) getX() - WIDTH / 2;
        int top = (int) getY() - HEIGHT / 2;

        if (facing == Facing.SIDE && facingLeft) {
            graphics.drawImage(
                    frame,
                    left + WIDTH,
                    top,
                    -WIDTH,
                    HEIGHT,
                    null
            );
        } else {
            graphics.drawImage(
                    frame,
                    left,
                    top,
                    WIDTH,
                    HEIGHT,
                    null
            );
        }
    }

    private Image currentFrame() {
        if (!moving) {
            return facing == Facing.BACK
                    ? backIdle
                    : frontIdle;
        }

        if (facing == Facing.SIDE) {
            return sideWalk[
                    animationFrame % sideWalk.length
            ];
        }

        if (facing == Facing.BACK) {
            return backIdle;
        }

        return frontWalk[
                animationFrame % frontWalk.length
        ];
    }

    private BufferedImage loadSpriteSheet() {
        try {
            URL resource =
                    Zombie.class.getResource(
                            "/ZombieSprites.png"
                    );

            if (resource != null) {
                return ImageIO.read(resource);
            }

            File sourceFile =
                    new File("src", "ZombieSprites.png");

            if (!sourceFile.isFile()) {
                sourceFile = new File(
                        "COMP2000/src",
                        "ZombieSprites.png"
                );
            }

            if (sourceFile.isFile()) {
                return ImageIO.read(sourceFile);
            }
        } catch (IOException exception) {
            // Use fallback sprite below.
        }

        return createFallbackSheet();
    }

    private Image crop(
            BufferedImage sheet,
            int column,
            int row,
            int frameWidth,
            int frameHeight
    ) {
        return sheet.getSubimage(
                column * frameWidth,
                row * frameHeight,
                frameWidth,
                frameHeight
        );
    }

    private BufferedImage createFallbackSheet() {
        BufferedImage sheet = new BufferedImage(
                WIDTH * 5,
                HEIGHT * 2,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = sheet.createGraphics();

        graphics.setColor(new Color(86, 145, 73));
        graphics.fillOval(
                5,
                2,
                WIDTH - 10,
                HEIGHT - 4
        );

        graphics.setColor(Color.BLACK);
        graphics.fillOval(11, 16, 5, 7);
        graphics.fillOval(WIDTH - 16, 16, 5, 7);

        graphics.dispose();

        return sheet;
    }

    /** Zombie states; each lambda replaces what would be a State subclass. */
    private enum Mode {
        WANDERING((zombie, world) -> {
            try {
                zombie.startChase(zombie.findTarget(world), world);
            } catch (TargetNotFoundException exception) {
                zombie.wander(world);
            }
        }),
        CHASING((zombie, world) -> {
            Human target = zombie.target;

            if (!target.isActive() || target.isHidden()
                    || zombie.distanceTo(target) > DETECTION_RADIUS) {
                zombie.stopChase();
                zombie.wander(world);
                return;
            }

            zombie.chase(world);

            if (zombie.distanceTo(target) <= INFECT_DISTANCE) {
                target.infect();
                zombie.stopChase();
            }
        });

        private final BiConsumer<Zombie, World> behaviour;

        Mode(BiConsumer<Zombie, World> behaviour) {
            this.behaviour = behaviour;
        }
    }

    private enum Facing {
        FRONT,
        SIDE,
        BACK
    }
}
