package JOO.jooshop.global.image;

import org.springframework.stereotype.Component;

@Component
public class ImageUrlResolver {

    private static final String UPLOAD_PREFIX = "/uploads/";

    public String toClientUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String trimmed = path.trim();

        if (isExternalUrl(trimmed)) {
            return trimmed;
        }

        if (trimmed.startsWith(UPLOAD_PREFIX)) {
            return trimmed;
        }

        return UPLOAD_PREFIX + trimmed;
    }

    public String normalizeExternalUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("이미지 URL은 null일 수 없습니다.");
        }

        String trimmed = url.trim();

        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("이미지 URL은 비어 있을 수 없습니다.");
        }

        if (!isExternalUrl(trimmed)) {
            throw new IllegalArgumentException("외부 이미지 URL 형식이 아닙니다.");
        }

        return trimmed;
    }

    public String normalizeExternalUrlOrNull(String url) {
        if (url == null) {
            return null;
        }

        String trimmed = url.trim();

        if (trimmed.isBlank()) {
            return null;
        }

        if (!isExternalUrl(trimmed)) {
            return null;
        }

        return trimmed;
    }

    private boolean isExternalUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }
}