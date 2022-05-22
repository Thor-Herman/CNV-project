package pt.ulisboa.tecnico.cnv.imageproc;

import boofcv.abst.fiducial.QrCodeDetector;
import boofcv.alg.fiducial.qrcode.QrCode;
import boofcv.factory.fiducial.ConfigQrCode;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.gui.feature.VisualizeShapes;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.GrayU8;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;

public class DetectQrCodeHandler extends ImageProcessingHandler {

    public BufferedImage process(BufferedImage bi) {
        QrCodeDetector<GrayU8> detector = FactoryFiducial.qrcode(new ConfigQrCode(), GrayU8.class);
        detector.process(ConvertBufferedImage.convertFrom(bi, (GrayU8)null));
        Graphics2D g2 = bi.createGraphics();
        g2.setColor(Color.GREEN);
        g2.setStroke(new BasicStroke(Math.max(4, bi.getWidth()/200)));
        for (QrCode qr : detector.getDetections()) {
            VisualizeShapes.drawPolygon(qr.bounds, true, 1, g2);
        }
        return bi;
    }

    public static void main(String[] args) {

        if (args.length != 2) {
            System.err.println("Syntax DetectQrCodeHandler <input image path> <output image path>");
            return;
        }

        String inputImagePath = args[0];
        String outputImagePath = args[1];
        BufferedImage bufferedInput = UtilImageIO.loadImageNotNull(inputImagePath);
        BufferedImage bufferedOutput  = new DetectQrCodeHandler().process(bufferedInput);
        UtilImageIO.saveImage(bufferedOutput, outputImagePath);
    }
}
