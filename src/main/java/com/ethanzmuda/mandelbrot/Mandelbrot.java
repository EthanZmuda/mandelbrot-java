package com.ethanzmuda.mandelbrot;

import java.awt.Graphics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.Semaphore;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class Mandelbrot implements Runnable {
    private MandelbrotPanel mandelbrotPanel;
    private JFrame frame;

    public Mandelbrot() {
        this.frame = new JFrame("Mandelbrot Set");
        this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.frame.setSize(800, 600);
        this.mandelbrotPanel = new MandelbrotPanel();
        this.frame.add(mandelbrotPanel);
        
        // Add resize listener
        this.frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                mandelbrotPanel.resizeImage();
            }
        });
        
        this.frame.setVisible(true);
    }

    public static void main(String[] args) {
        Mandelbrot mandelbrot = new Mandelbrot();
        Thread animationThread = new Thread(mandelbrot);
        animationThread.start();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            mandelbrotPanel.updateImage();
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

class MandelbrotPanel extends JPanel {
    private int[] pixels;
    private BufferedImage image;
    private double centerX = -0.75; // Center of view in complex plane
    private double centerY = 0.0;
    private double zoom = 200.0;    // Zoom level (pixels per unit)
    
    // Mouse dragging variables
    private boolean dragging = false;
    private int lastMouseX, lastMouseY;

    Semaphore semaphore = new Semaphore(1);

    public MandelbrotPanel() {
        createImage();
        setupMouseListeners();
    }
    
    private void setupMouseListeners() {
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragging = true;
                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging) {
                    try {
                        semaphore.acquire();
                        int deltaX = e.getX() - lastMouseX;
                        int deltaY = e.getY() - lastMouseY;
                        
                        // Convert pixel movement to complex plane movement
                        centerX -= deltaX / zoom;
                        centerY -= deltaY / zoom;
                        
                        lastMouseX = e.getX();
                        lastMouseY = e.getY();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release();
                    }
                }
            }
            
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                try {
                    semaphore.acquire();
                    int mouseX = e.getX();
                    int mouseY = e.getY();
                    
                    // Convert mouse position to complex plane coordinates before zoom
                    double complexX = centerX + (mouseX - getWidth() / 2.0) / zoom;
                    double complexY = centerY + (mouseY - getHeight() / 2.0) / zoom;
                    
                    // Zoom factor: negative rotation = zoom in, positive rotation = zoom out
                    zoom *= 1 + e.getPreciseWheelRotation() * 0.2;

                    // Adjust center to keep the mouse position fixed in complex plane
                    centerX = complexX - (mouseX - getWidth() / 2.0) / zoom;
                    centerY = complexY - (mouseY - getHeight() / 2.0) / zoom;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    semaphore.release();
                }
            }
        };
        
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
    }
    
    private void createImage() {
        int width = getWidth() > 0 ? getWidth() : 800;
        int height = getHeight() > 0 ? getHeight() : 600;
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }
    
    public void resizeImage() {
        createImage();
        updateImage();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            g.drawImage(image, 0, 0, null);
        }
    }

    private void drawMandelbrotSet() {
        try {
            semaphore.acquire();
            if (image == null || pixels == null) return; // Safety check

            int width = image.getWidth();
            int height = image.getHeight();

            // Ensure pixels array matches image size
            if (pixels.length != width * height) {
                pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            }
            
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    // Convert pixel coordinates to complex plane using dynamic scaling
                    double zx = centerX + (x - width / 2.0) / zoom;
                    double zy = centerY + (y - height / 2.0) / zoom;

                    double zr = 0.0;
                    double zc = 0.0;
                    double temp;
                    boolean diverge = false;
                    int iterations = 0;
                    for (int i = 0; i < 1000; i++) {
                        temp = zr * zr - zc * zc + zx;
                        zc = 2 * zr * zc + zy;
                        zr = temp;

                        if (zr * zr + zc * zc > 4) {
                            diverge = true;
                            iterations = i;
                            break;
                        }
                    }

                    int pixelIndex = y * width + x;
                    if (pixelIndex < pixels.length) { // Bounds check
                        if (diverge) {
                            int color = (int)(iterations * 255.0 / 100.0) % 255; // Scale iterations to color
                            int r = color;
                            int g = (color * 2) % 255;
                            int b = (color * 4) % 255;
                            pixels[pixelIndex] = (r << 16) | (g << 8) | b;
                        } else {
                            pixels[pixelIndex] = 0xFFFFFF;
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
    }

    public void updateImage() {
        drawMandelbrotSet();
        repaint();
    }
}
