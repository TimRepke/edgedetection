import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FilenameUtils;

/**
 * Created by Tim Repke
 *
 * This class implements the Sobel Edge Detection algorithm.
 * Example usage found in TestSobel.java
 *
 */
public class SobelEdgeDetection {

    // theshold at which edges are edges
    private final static int EDGE_THRESHOLD = 50;

    private int height; // height of image
    private int width;  // width of image
    private int picsize; // width*height of image (number of pixels)

    private int[] data; // grayscale image
    private BufferedImage image; // input image

    private float gaussianSigma; // sigma (spread) of the gauss kernel
    private int gaussianSize; // width of gauss kernel (MUST be uneven)
    private boolean gauss; // apply gauss smoothing?
    private boolean contrastNormalized; // apply normalisation?
    private boolean intermediates; // write intermediate images?
    private boolean invert; // invert image? (lines are black, bg white)

    private float[] xGradient; // x-gradients
    private float[] yGradient; // y-gradients
    private float[] fimg; // working copy of image

    // some funny variables to manipulate output file names
    private String outputprefix = "outputs/sobel_";
    private String outputpostprefix = "";
    private String outname = "";

    /* *************
     * Some setter methods to configure
     * the behavior of the system
     */
    public void doNormalise(boolean contrastNormalized) {
        this.contrastNormalized = contrastNormalized;
    }

    public void setPrefix(String pre){
        this.outputpostprefix = pre+"_";
    }

    public void doGauss(boolean doit){
        gauss = doit;
    }
    public void setGauss(float sigma, int size){
        gaussianSize = size;
        gaussianSigma = sigma;
    }

    public void doIntermediates(boolean doit){
        intermediates = doit;
    }

    public void doInvert(boolean b) {
        invert = b;
    }

    /**
     * Constructor
     */
    public SobelEdgeDetection(){
        gaussianSigma = 1f;
        gaussianSize  = 3;
        gauss = false;
        contrastNormalized = false;
        intermediates = false;
        invert = false;
    }

    /**
     * main method that runs the process
     *
     * @param file filename of the image to analyse
     * @throws IOException
     */
    public void runner(String file) throws IOException {
        long start;
        image = ImageIO.read(new File(file));
        outname = FilenameUtils.getBaseName(file);
        width = image.getWidth();
        height = image.getHeight();
        picsize = width * height;

        initArrays();
        start = System.currentTimeMillis();
        readLuminance();
        System.out.println("readLuminance(): " + (System.currentTimeMillis() - start) + "ms");

        if(intermediates) writeImg("lumi", data, 1.0f, false);

        if(gauss) {
            start = System.currentTimeMillis();
            applyGauss(gaussianSigma, gaussianSize);
            System.out.println("applyGauss(): " + (System.currentTimeMillis() - start) + "ms");
            if (intermediates) writeImg("gauss", fimg, 1.0f, false);
        }
        if(contrastNormalized) {
            start = System.currentTimeMillis();
            normalizeContrast();
            System.out.println("normalizeContrast(): " + (System.currentTimeMillis() - start) + "ms");
            if(intermediates) writeImg("normed", data, 1.0f, false);
        }

        start = System.currentTimeMillis();
        applySobelKernel();
        System.out.println("applySobelKernel(): " + (System.currentTimeMillis() - start) + "ms");

        if(intermediates) writeImg("xgrad", xGradient, 1.0f, false);
        if(intermediates) writeImg("ygrad", yGradient, 1.0f, false);
        writeImg("sum", fimg, 1.0f, false);
        writeImg("final", fimg, 1.0f, true);
    }

    /**
     * initialises all arrays needed
     */
    private void initArrays() {
        data = new int[picsize];
        
        xGradient = new float[picsize];
        yGradient = new float[picsize];
        fimg = new float[picsize];
    }


    /**
     * apply the 3x3 sobel kernel in x and y direction
     *
     * result is written in xGradient and yGradient
     * as well as the sum of both in fimg
     *
     * clipping above 255 and below 0 is already removed in the sum (not x and y)
     */
    private void applySobelKernel(){
        float sum = 0, xsum = 0, ysum = 0;

        // copy the image to avoid unwanted influences
        float[] bild = new float[fimg.length];
        for(int run = 0; run < fimg.length; run++)
            bild[run] = fimg[run];

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                xsum = ysum = 0;

                // mask cannot be applied to the outer edge
                if(x == 0 || x >= width-1 || y == 0 || y >= height-1)
                    sum = 0;
                else {
                    xsum = (2*bild[x+1+(width*(y+0))]
                            +bild[x+1+(width*(y-1))]
                            +bild[x+1+(width*(y+1))])
                            -(2*bild[x-1+(width*(y+0))]
                            +bild[x-1+(width*(y-1))]
                            +bild[x-1+(width*(y+0))]);
                    ysum = (2*bild[x+(width*(y+1))]+
                            bild[x-1+(width*(y+1))]+
                            bild[x+1+(width*(y+1))])
                            -(2*bild[x+(width*(y-1))]
                            +bild[x-1+(width*(y-1))]
                            +bild[x+1+(width*(y-1))]);
                    sum = Math.abs(xsum) + Math.abs(ysum);
                }

                // remove clippings
                if(sum>255) sum = 255;
                if(sum<0)   sum = 0;

                // write results
                xGradient[x+(width*y)] = xsum;
                yGradient[x+(width*y)] = ysum;
                fimg[x+(width*y)]      = sum;
            }
        }
    }

    /**
     * applies gaussian filter with mask width size and spead sigma
     * filter masks is generated first by this function
     *
     * @param sigma spread of the filter
     * @param size width of the mask (must be uneven!!)
     */
    private void applyGauss(float sigma, int size) {

        //generate the gaussian mask
        float kernel[] = new float[size * size];
        int offset = (int) Math.floor(size/2);
        int center = (int) Math.floor((size*size)/2);

        // generate kernel
        float su = 0f;
        for (int x = -offset; x <= offset; x++) {
            for (int y = -offset; y <= offset; y++) {
                kernel[center + x + (size * y)] =
                        (1f / (2f * ((float) Math.PI) * sigma * sigma))
                                * (float) Math.exp(-((x * x) + (y * y)) / (2f * sigma * sigma));
                su+= kernel[center + x + (size * y)];
             }
        }
        // normalise kernel
        for (int x = -offset; x <= offset; x++) {
            for (int y = -offset; y <= offset; y++) {
                kernel[center + x + (size * y)] = kernel[center + x + (size * y)] / su;
            }
        }

        // apply kernel to image
        int sum = 0;
        for(int x = offset; x < (width-offset); x++) {
            for (int y = offset; y < (height-offset); y++) {
                sum = 0;
                for(int m = -offset; m <= offset; m++){
                    for(int n = -offset; n <= offset; n++){
                        sum += kernel[center+m+(n*size)] * fimg[(x-m) + (width*(y-n))];
                    }
                }
                fimg[x+(width*y)] = sum;
            }
        }
    }

    /* ****************************
     * following methods taken from CannyEdgeDetector.java
     * programmed by Tom Gibara
     * some changes where applied
     * ****************************
     */

    /**
     * normalises the contrast of the image
     * takes data from int[] data and writes it back into the same array
     */
    private void normalizeContrast() {
        int[] histogram = new int[256];
        for (int i = 0; i < data.length; i++) {
            histogram[data[i]]++;
        }
        int[] remap = new int[256];
        int sum = 0;
        int j = 0;
        for (int i = 0; i < histogram.length; i++) {
            sum += histogram[i]; // cumulative histogram
            int target = sum*255/picsize;
            for (int k = j+1; k <=target; k++) {
                remap[k] = i;
            }
            j = target;
        }

        for (int i = 0; i < data.length; i++) {
            data[i] = remap[data[i]];
        }
    }


    /**
     * reads the input image to a grayscale array of pixels
     * result is saved in float[] data
     */
    private void readLuminance() {
        int type = image.getType();

        if (type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB) {
            int[] pixels = (int[]) image.getData().getDataElements(0, 0, width, height, null);
            for (int i = 0; i < picsize; i++) {
                int p = pixels[i];
                int r = (p & 0xff0000) >> 16;
                int g = (p & 0xff00) >> 8;
                int b = p & 0xff;
                data[i] = luminance(r, g, b);
            }
        } else if (type == BufferedImage.TYPE_BYTE_GRAY) {
            byte[] pixels = (byte[]) image.getData().getDataElements(0, 0, width, height, null);
            for (int i = 0; i < picsize; i++) {
                data[i] = (pixels[i] & 0xff);
            }
        } else if (type == BufferedImage.TYPE_USHORT_GRAY) {
            short[] pixels = (short[]) image.getData().getDataElements(0, 0, width, height, null);
            for (int i = 0; i < picsize; i++) {
                data[i] = (pixels[i] & 0xffff) / 256;
            }
        } else if (type == BufferedImage.TYPE_3BYTE_BGR) {
            byte[] pixels = (byte[]) image.getData().getDataElements(0, 0, width, height, null);
            int offset = 0;
            for (int i = 0; i < picsize; i++) {
                int b = pixels[offset++] & 0xff;
                int g = pixels[offset++] & 0xff;
                int r = pixels[offset++] & 0xff;
                data[i] = luminance(r, g, b);
            }
        } else {
            throw new IllegalArgumentException("Unsupported image type: " + type);
        }
        for (int run = 0; run < picsize; run++)
            fimg[run] = data[run];
    }

    /**
     * Converts the RBG values to a grayscale value
     *
     * @param r red value
     * @param g green value
     * @param b blue value
     * @return grayscale pixel value
     */
    private int luminance(float r, float g, float b) {
        return Math.round(0.299f * r + 0.587f * g + 0.114f * b);
    }

    /**
     * converts the given array to hard black/white values without scale
     * @param pixels pixeldata
     * @return black/white only pixels
     */
    private int[] thresholdEdges(int pixels[]) {
        int[] ret = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            if(invert)
                ret[i] = pixels[i] > (255-EDGE_THRESHOLD) ? -1 : 0xff000000;
            else
                ret[i] = pixels[i] > EDGE_THRESHOLD ? -1 : 0xff000000;
        }
        return ret;
    }

    /**
     * writes the image to a file
     * prefix and filename + type will be added
     *
     * @param name filename
     * @param img pixel data
     * @param fac factor to brighten image (choose 1.0f if no change)
     * @param edge set true if hard black/white image wanted
     * @throws IOException
     */
    private void writeImg(String name, float[] img, float fac, boolean edge) throws IOException {
        int[] pixels = new int[img.length];

        for(int r = 0; r < img.length; r++){
            // convert float2int && remove clipping over 255 or under 0
            pixels[r] = Math.max(0, Math.min(255, Math.round(img[r]*fac)));

            if(invert)
                pixels[r] = 255-pixels[r];

            // make values grayscale by shifting the same value to r,g,b positions and set opacity to 100%
            if(!edge)
                pixels[r] = pixels[r] | (pixels[r] << 8 ) | (pixels[r] << 16 ) | 0xff000000;
        }

        // if hard edges selected, apply the filter
        if(edge)
            pixels = thresholdEdges(pixels);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.getWritableTile(0, 0).setDataElements(0, 0, width, height, pixels);
        ImageIO.write(image, "PNG", new File(outputprefix + outputpostprefix + outname + "_" + name + ".png"));
    }
    private void writeImg(String name, int[] img, float fac, boolean edge) throws IOException {
        float[] pixels = new float[img.length];
        for(int r = 0; r < img.length; r++) {
            pixels[r] = (float) img[r];
        }
        writeImg(name, pixels, fac, edge);
    }

}
