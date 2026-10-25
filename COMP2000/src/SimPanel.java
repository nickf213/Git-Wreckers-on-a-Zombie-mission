import java.awt.*;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.*;

public class SimPanel extends JPanel {

    private final World world;
    private final Image backgroundImage;
    private final Runnable resetAction;

    private boolean started;
    private boolean paused;
    private int speedLevel;

    private static final int MIN_SPEED_LEVEL = 1;
    private static final int MAX_SPEED_LEVEL = 5;
    private static final int BASE_TIMER_DELAY = 100;

    public SimPanel(World world) {
        this(world, () -> { });
    }

    public SimPanel(World world, Runnable resetAction) {
        this.world = Objects.requireNonNull(world);
        this.resetAction = Objects.requireNonNull(resetAction);
        started = false;
        paused = false;
        speedLevel = 3;

        setPreferredSize(
                new Dimension(world.getWidth(), world.getHeight())
        );
        URL backgroundURL =
                SimPanel.class.getResource("/Background.png");

        if (backgroundURL != null) {
            backgroundImage = new ImageIcon(backgroundURL).getImage();
        } else {
            File sourceAsset = new File("src", "Background.png");
            if (!sourceAsset.isFile()) {
                sourceAsset = new File("COMP2000/src", "Background.png");
            }
            if (!sourceAsset.isFile()) {
                throw new IllegalStateException(
                        "Background.png could not be found"
                );
            }
            backgroundImage = new ImageIcon(sourceAsset.getAbsolutePath()).getImage();
        }
        setupKeyBindings();
    }
    private void setupKeyBindings() {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("pressed R"),
                "start"
        );

        getActionMap().put("start", new AbstractAction() {
            @Override
            public void actionPerformed(
                    java.awt.event.ActionEvent event
            ) {
                if (started) {
                    resetAction.run();
                    speedLevel = 3;
                }
                started = true;
                paused = false;
                repaint();
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("pressed P"),
                "pause"
        );

        getActionMap().put("pause", new AbstractAction() {
            @Override
            public void actionPerformed(
                    java.awt.event.ActionEvent event
            ) {
                if (started) {
                    paused = !paused;
                    repaint();
                }
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("pressed PLUS"),
                "increase-speed"
        );
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("pressed ADD"),
                "increase-speed"
        );
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke('+'),
                "increase-speed"
        );
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("shift pressed EQUALS"),
                "increase-speed"
        );
        getActionMap().put("increase-speed", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                increaseSpeed();
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("pressed MINUS"),
                "decrease-speed"
        );
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("pressed SUBTRACT"),
                "decrease-speed"
        );
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke('-'),
                "decrease-speed"
        );
        getActionMap().put("decrease-speed", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                decreaseSpeed();
            }
        });

    }

    private void increaseSpeed() {
        speedLevel = Math.min(MAX_SPEED_LEVEL, speedLevel + 1);
        repaint();
    }

    private void decreaseSpeed() {
        speedLevel = Math.max(MIN_SPEED_LEVEL, speedLevel - 1);
        repaint();
    }

    public boolean isRunning() {
        return started && !paused;
    }

    /** Returns the current timer delay requested by the speed controls. */
    public int getTimerDelay() {
        return BASE_TIMER_DELAY - (speedLevel - MIN_SPEED_LEVEL) * 15;
    }

    private String getSpeedLabel() {
        return speedLevel + "/" + MAX_SPEED_LEVEL;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D graphics2D =
                (Graphics2D) graphics.create();

        try {
            // Draw the background
            graphics2D.drawImage(
                    backgroundImage,
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    this
            );
            if (!started) {
                drawStartScreen(graphics2D);
                return;
            }



            // Draw every active entity
            for (Entity entity : world.getEntities()) {
                if (entity.isActive()) {
                    entity.draw(graphics2D);
                }
            }

            // Draw each building's occupancy on top
            graphics2D.setFont(
                    new Font("Arial", Font.BOLD, 14)
            );


            for (Building building : findEntities(Building.class)) {
                String occupancyText =
                        "Occupancy: "
                                + building.getOccupantCount()
                                + "/"
                                + building.getCapacity();

                int textX = (int) building.getX();
                int textY = (int) building.getY() - 10;

                graphics2D.setColor(Color.BLACK);
                graphics2D.drawString(
                        occupancyText,
                        textX + 1,
                        textY + 1
                );

                graphics2D.setColor(Color.WHITE);
                graphics2D.drawString(
                        occupancyText,
                        textX,
                        textY
                );
            }
            int humanCount = countActiveEntitiesNamed("Human");
            int zombieCount = countActiveEntitiesNamed("Zombie");
            int militaryCount = countActiveEntitiesNamed("Military");
            int foodCount = countActiveEntitiesNamed("Food");

            graphics2D.setColor(new Color(0, 0, 0, 170));
            graphics2D.fillRoundRect(15, 15, 220, 116, 12, 12);

            graphics2D.setColor(Color.WHITE);
            graphics2D.drawString("Tick: " + world.getTick(), 25, 38);
            graphics2D.drawString("Humans: " + humanCount, 25, 58);
            graphics2D.drawString("Zombies: " + zombieCount, 25, 78);
            graphics2D.drawString("Military: " + militaryCount, 25, 98);
            graphics2D.drawString("Food: " + foodCount, 115, 58);
            graphics2D.drawString("Speed: " + getSpeedLabel(), 115, 78);
            graphics2D.setColor(Color.LIGHT_GRAY);
            graphics2D.drawString("+/- speed", 115, 98);

        } finally {
            graphics2D.dispose();
        }
    }
    private void drawStartScreen(Graphics2D graphics2D) {
        graphics2D.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        graphics2D.setColor(new Color(0, 0, 0, 165));
        graphics2D.fillRect(
                0,
                0,
                getWidth(),
                getHeight()
        );

        graphics2D.setFont(
                new Font(
                        "Serif",
                        Font.BOLD | Font.ITALIC,
                        60
                )
        );

        graphics2D.setColor(new Color(190, 30, 30));
        drawCenteredText(
                graphics2D,
                "ZOMBIE SIMULATION",
                getHeight() / 2 - 50
        );

        graphics2D.setFont(
                new Font("Serif", Font.BOLD, 28)
        );

        graphics2D.setColor(Color.WHITE);
        drawCenteredText(
                graphics2D,
                "PRESS R TO START",
                getHeight() / 2 + 30
        );

        graphics2D.setFont(
                new Font("Serif", Font.ITALIC, 18)
        );

        graphics2D.setColor(Color.LIGHT_GRAY);
        drawCenteredText(
                graphics2D,
                "Press P to pause or resume",
                getHeight() / 2 + 70
        );
    }

    private void drawCenteredText(
            Graphics2D graphics2D,
            String text,
            int y
    ) {
        FontMetrics fontMetrics =
                graphics2D.getFontMetrics();

        int x = (
                getWidth()
                        - fontMetrics.stringWidth(text)
        ) / 2;

        graphics2D.drawString(text, x, y);
    }
    // REVIEW: this loop is a filter + map, so it could be a stream pipeline
    // (stream().filter(entityType::isInstance).map(entityType::cast).toList()).
    private <T extends Entity> List<T> findEntities(
            Class<T> entityType
    ) {
        List<T> matchingEntities = new ArrayList<>();

        for (Entity entity : world.getEntities()) {
            if (entityType.isInstance(entity)) {
                matchingEntities.add(entityType.cast(entity));
            }
        }

        return matchingEntities;
    }

    // REVIEW: this matches class names as strings and is called four times per frame.
    // One stream would do it in a single pass and not break on a rename:
    // world.getEntities().stream().filter(Entity::isActive)
    //     .collect(Collectors.groupingBy(e -> e.getClass().getSimpleName(), Collectors.counting()))
    private int countActiveEntitiesNamed(String className) {
        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entity.isActive()
                    && entity.getClass().getSimpleName().equals(className)) {
                count++;
            }
        }
        return count;
    }
}
