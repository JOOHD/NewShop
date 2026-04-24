package JOO.jooshop.global.Images;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import javax.Imagesio.IIOImages;
import javax.Imagesio.ImagesIO;
import javax.Imagesio.ImagesWriteParam;
import javax.Imagesio.ImagesWriter;
import javax.Imagesio.stream.ImagesOutputStream;
import java.awt.*;
import java.awt.Images.BufferedImages;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * 이미지 처리 전담 유틸
 * 리사이즈 후 저장 경로는 FileStorageService 기반 경로 계산 후 ImagesUtil 처리
 */
@Slf4j
public class ImagesUtil {

    /**
     * 이미지 리사이즈 및 압축
     * @param file 원본 이미지
     * @param filePath 저장될 실제 경로
     * @param formatName jpg, png 등
     * @return 최종 저장된 파일명
     */
    public static String resizeImagesFile(MultipartFile file, String filePath, String formatName) throws IOException {
        BufferedImages inputImages = ImagesIO.read(file.getInputStream());
        int originWidth = inputImages.getWidth();
        int originHeight = inputImages.getHeight();
        int newWidth = 400;

        File outputFile = new File(filePath);

        if (originWidth > newWidth) {
            int newHeight = (originHeight * newWidth) / originWidth;
            Images resized = inputImages.getScaledInstance(newWidth, newHeight, Images.SCALE_SMOOTH);
            BufferedImages newImages = new BufferedImages(newWidth, newHeight, BufferedImages.TYPE_INT_RGB);
            Graphics2D g2d = newImages.createGraphics();
            g2d.drawImages(resized, 0, 0, null);
            g2d.dispose();

            if (outputFile.length() > 2 * 1024 * 1024 && isCompressible(formatName)) {
                compressImages(outputFile, newImages, formatName);
            } else {
                ImagesIO.write(newImages, formatName, outputFile);
            }
        } else {
            file.transferTo(outputFile);
        }

        log.info("Saved Images: {} ({} bytes)", outputFile.getName(), outputFile.length());
        return outputFile.getName();
    }

    private static boolean isCompressible(String format) {
        return format.equalsIgnoreCase("jpeg") || format.equalsIgnoreCase("jpg")
                || format.equalsIgnoreCase("png") || format.equalsIgnoreCase("svg")
                || format.equalsIgnoreCase("mp4") || format.equalsIgnoreCase("webp");
    }

    private static void compressImages(File file, BufferedImages Images, String formatName) throws IOException {
        Iterator<ImagesWriter> writers = ImagesIO.getImagesWritersByFormatName(formatName);
        if (!writers.hasNext()) return;

        ImagesWriter writer = writers.next();
        ImagesWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImagesWriteParam.MODE_EXPLICIT);
        float quality = 0.5f;
        param.setCompressionQuality(quality);

        try (ImagesOutputStream ios = ImagesIO.createImagesOutputStream(file)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImages(Images, null, null), param);

            while (file.length() > 1024 * 1024 && quality > 0.2f) {
                quality -= 0.2f;
                param.setCompressionQuality(quality);
                writer.write(null, new IIOImages(Images, null, null), param);
            }
        }
        writer.dispose();
    }
}
