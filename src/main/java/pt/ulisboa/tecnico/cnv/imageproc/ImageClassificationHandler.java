package pt.ulisboa.tecnico.cnv.imageproc;

import boofcv.abst.scene.ImageClassifier;
import boofcv.abst.scene.ImageClassifier.Score;
import boofcv.factory.scene.ClassifierAndSource;
import boofcv.factory.scene.FactoryImageClassifier;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.Planar;
import deepboof.io.DeepBoofDataBaseOps;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

public class ImageClassificationHandler extends ImageProcessingHandler {

    public BufferedImage process(BufferedImage bi) throws IOException {
        ClassifierAndSource cs = FactoryImageClassifier.vgg_cifar10();
        ImageClassifier<Planar<GrayF32>> classifier = cs.getClassifier();
        classifier.loadModel(DeepBoofDataBaseOps.downloadModel(cs.getSource(), new File("/opt")));
        List<String> categories = classifier.getCategories();
        Planar<GrayF32> image = new Planar<>(GrayF32.class, bi.getWidth(), bi.getHeight(), 3);
        ConvertBufferedImage.convertFromPlanar(bi, image, true, GrayF32.class);
        classifier.classify(image);

        List<Score> scores = classifier.getAllResults();
        for (Score s : scores) {
            System.out.println(String.format("%s = %s", categories.get(s.category), s.score));
        }

        String resultImage = String.format("/%s.jpg", categories.get(scores.get(0).category));
        return ImageIO.read(getClass().getResourceAsStream(resultImage));
    }

    public static void main( String[] args ) throws IOException {

        if (args.length != 2) {
            System.err.println("Syntax ImageClassificationHandler <input image path> <output image path>");
            return;
        }

        String inputImagePath = args[0];
        String outputImagePath = args[1];
        BufferedImage bufferedInput = UtilImageIO.loadImageNotNull(inputImagePath);
        BufferedImage bufferedOutput = new ImageClassificationHandler().process(bufferedInput);
        UtilImageIO.saveImage(bufferedOutput, outputImagePath);
    }
}