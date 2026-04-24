package JOO.jooshop.global.file;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.UUID;

/**
 * 파일 저장 및 삭제 전담 서비스
 * MultipartFile 저장
 * 외부 URL -> 파일 저장
 * 삭제 및 URL 반환
 */
@Service
public class FileStorageService {

    // FileStorageService가 “경로 + 리사이징 여부 + 실제 저장”을 전부 담당

    private final Path baseUploadPath  = Paths.get(System.getProperty("user.dir"), "uploads");

    /**
     * 파일 저장
     * @param file 업로드할 MultipartFile
     * @param subDir 하위 디렉토리명 (예: "thumbnails", "contentImages")
     * @return DB에 저장할 상대 URL (예: "/upload/thumbnails/abc123.jpg")
     */
    public String saveFile(MultipartFile file, String subDir) throws IOException {
        if (file == null || file.isEmpty()) return null;

        String originalFilename = file.getOriginalFilename();
        String ext = extractExtension(originalFilename);
        String fileName = generateFileName(ext);

        Path dirPath = baseUploadPath.resolve(subDir);
        Files.createDirectories(dirPath);

        Path filePath = dirPath.resolve(fileName);
        file.transferTo(filePath.toFile());

        return normalizeRelativePath(subDir + "/" + fileName);
    }

    /**
     * 외부 URL 이미지 다운로드 후 저장
     */
    public String saveFileFromUrl(URL url, String subDir) throws IOException {
        if (url == null) { return null; }

        String ext = extractExtension(url.getPath());
        String fileName = generateFileName(ext);

        Path dirPath = baseUploadPath.resolve(subDir);
        Files.createDirectories(dirPath);

        Path filePath = dirPath.resolve(fileName);

        try (InputStream in = url.openStream()) {
            Files.copy(in, filePath);
        }

        return normalizeRelativePath(subDir + "/" + fileName);
    }

    /**
     * 파일 삭제
     * @param relativePath DB에 저장된 상대 경로
     */
    public void deleteFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;

        try {
            String cleanPath = relativePath
                    .replaceFirst("^/uploads/", "")
                    .replaceFirst("^uploads/", "");

            Path fullPath = baseUploadPath.resolve(cleanPath);
            Files.deleteIfExists(fullPath);
        } catch (IOException e) {
            throw new RuntimeException("파일 삭제 실패: " + relativePath, e);
        }
    }

    /**
     * HTML/브라우저에서 접근 가능한 URL 생성
     * @param relativePath DB에 저장된 상대 경로
     * @return 절대 URL 형태 (ex: /uploads/thumbnails/xxx.jpg)
     */
    public String getUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        // 방어 코드 추가
        String cleaned = relativePath.replaceAll("^/+", "").replaceFirst("^upload/", "");
        return "/uploads/" + cleaned;
    }

    private String extractExtension(String path) {
        if (path == null || !path.contains(".")) {
            return "";
        }
        return path.substring(path.lastIndexOf("."));
    }

    private String generateFileName(String ext) {
        return UUID.randomUUID().toString().replace("-", "") + ext;
    }

    private String normalizeRelativePath(String path) {
        return path.replaceAll("//+", "/");
    }
}
