import java.awt.*;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

public class Building extends Entity {
    private final Image image;
    private final int width;
    private final int height;
    private final int capacity;
    private int occupantCount;

    public Building(double x, double y, int width, int height) {
        this(x, y, width, height, 10);
    }

    // REVIEW: three levels of nested if/else. Throwing early for bad input
    // (if (width <= 0 || height <= 0) throw ...; if (capacity <= 0) throw ...;)
    // would flatten this. The image loading could also move into a helper method.
    public Building(double x, double y, int width, int height, int capacity) {
        super(x, y);
        if (width > 0 && height > 0) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Building capacity must be positive");
            } else {
                this.width = width;
                this.height = height;
                this.capacity = capacity;
                this.occupantCount = 0;

                Image loadedImage;
                try {
                    URL imageURL = Building.class.getResource("/Building.png");
                    if (imageURL != null) {
                        loadedImage = ImageIO.read(imageURL);
                    } else {
                        File sourceAsset = new File("src", "Building.png");
                        if (!sourceAsset.isFile()) {
                            sourceAsset = new File("COMP2000/src", "Building.png");
                        }
                        if (!sourceAsset.isFile()) {
                            throw new IOException("Building.png could not be found");
                        }
                        loadedImage = new ImageIcon(sourceAsset.getAbsolutePath()).getImage();
                    }
                    if (loadedImage == null) {
                        throw new IOException("Building.png is corrupted or unsupported");
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to load the building image", exception);
                }

                this.image = loadedImage;
            }
        } else {
            throw new IllegalArgumentException("Building dimensions must be positive");
        }
    }

    public boolean isFull() {
        return this.occupantCount >= this.capacity;
    }

    public boolean canEnter() {
        return !this.isFull();
    }

    public boolean enter() {
        if (this.isFull()) {
            return false;
        } else {
            ++this.occupantCount;
            return true;
        }
    }

    public boolean leave() {
        if (this.occupantCount == 0) {
            return false;
        } else {
            --this.occupantCount;
            return true;
        }
    }

    public boolean containsPoint(double pointX, double pointY) {
        return pointX >= this.getX() && pointX < this.getX() + (double)this.width && pointY >= this.getY() && pointY < this.getY() + (double)this.height;
    }

    public double getEntranceX() {
        return this.getX() + (double)this.width / (double)2.0F;
    }

    public double getEntranceY() {
        return this.getY() + (double)this.height;
    }

    public int getOccupantCount() {
        return this.occupantCount;
    }

    public int getCapacity() {
        return this.capacity;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    // REVIEW: empty on purpose? A one-line comment saying so would help readers.
    public void update(World world) {
    }

    // REVIEW: add @Override (and the same on update above) so the compiler checks the signature.
    public void draw(Graphics2D graphics) {
        graphics.drawImage(this.image, (int)this.getX(), (int)this.getY(), this.width, this.height, (ImageObserver)null);
    }
}