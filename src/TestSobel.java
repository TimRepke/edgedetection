import java.io.IOException;

public class TestSobel {

    public static void main(String[] args){
        //                      0            1              2                     3             4           5
        String[] files = {"CIMG9197.JPG", "baby.jpg", "firstRunTriclub.jpg", "smhouse.jpg", "text.jpg", "text2.jpg"};

        SobelEdgeDetection detection = new SobelEdgeDetection();

        // config run settings
        detection.doIntermediates(false);
        detection.doInvert(true);

        switch (3) { // select setting
            case 0: // no filter
                detection.setPrefix("none");
                break;
            case 1: // just normalisation
                detection.setPrefix("normnogauss");
                detection.doNormalise(true);
                break;
            case 2: // just gauss
                detection.setPrefix("gaussnonorm");
                detection.doGauss(true);
                detection.setGauss(1f, 5);
                break;
            case 3:  // gauss + normalisation
                detection.setPrefix("all");
                detection.doNormalise(true);
                detection.doGauss(true);
                detection.setGauss(1f, 5);
                break;
            default: break;
        }

        try {
            long start = System.currentTimeMillis();

            detection.runner(files[5]);

            System.out.println("Total: " + (System.currentTimeMillis() - start) + "ms");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
