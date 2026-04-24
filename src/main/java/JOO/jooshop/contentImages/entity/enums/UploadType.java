package JOO.jooshop.contentImages.entity.enums;

public enum UploadType {
    PROFILE("/uploads/profileImages/", "src/main/resources/static/uploads/profileImages/"),
    PRODUCT("/uploads/productImages/", "src/main/resources/static/uploads/productImages/"),
    THUMBNAILS("/uploads/thumbnails/", "src/main/resources/static/uploads/thumbnails/"),
    contentImages("/uploads/contentImages/", "src/main/resources/static/uploads/contentImages/");

    // Getter 메서드, enum 외부에서 UploadType의 DB 경로와 로컬 경로를 가져올 때 사용
    private final String dbPath;    // /uploads/adminUpload/
    private final String localPath; // src/main/resources/static/uploads/adminUpload/

    // 생성자
    UploadType(String dbPath, String localPath) {
        this.dbPath = dbPath;
        this.localPath = localPath;
    }

    public String getDbPath() { return dbPath; }
    public String getLocalPath() { return localPath; }
}
