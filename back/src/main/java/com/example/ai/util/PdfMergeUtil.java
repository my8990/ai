package com.example.ai.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public final class PdfMergeUtil {

    private PdfMergeUtil() {
    }

    public static int mergeImages(List<File> images, File target, int dpi) throws IOException {
        int pageCount = 0;
        int renderDpi = dpi <= 0 ? 150 : dpi;
        try (PDDocument document = new PDDocument()) {
            for (File imageFile : images) {
                BufferedImage image = ImageIO.read(imageFile);
                if (image == null) {
                    continue;
                }
                BufferedImage rgb = toRgb(image);
                PDImageXObject pdImage = LosslessFactory.createFromImage(document, rgb);
                float width = rgb.getWidth() * 72f / renderDpi;
                float height = rgb.getHeight() * 72f / renderDpi;
                PDPage page = new PDPage(new PDRectangle(width, height));
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.drawImage(pdImage, 0, 0, width, height);
                }
                pageCount++;
            }
            if (pageCount == 0) {
                throw new IOException("没有可用的课件图片");
            }
            document.save(target);
        }
        return pageCount;
    }

    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }
}