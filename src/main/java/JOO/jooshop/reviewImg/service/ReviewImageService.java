package JOO.jooshop.reviewImg.service;

import JOO.jooshop.review.entity.Review;
import JOO.jooshop.review.repository.ReviewRepository;
import JOO.jooshop.reviewImg.entity.ReviewImg;
import JOO.jooshop.reviewImg.repository.ReviewImgRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.Imagesio.ImagesIO;
import java.awt.Images.BufferedImages;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static JOO.jooshop.global.authorization.MemberAuthorizationUtil.verifyUserIdMatch;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class ReviewImagesService {
    private final ReviewImgRepository reviewImgRepository;
    private final ReviewRepository reviewRepository;


    /**
     * 리뷰 이미지 업로드
     * @param reviewId
     * @param images
     */
    public void uploadReviewImg(Long reviewId, List<MultipartFile> images) {
        try {
            Review review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new NoSuchElementException("해당 글을 찾을 수 없습니다."));

            verifyUserIdMatch(review.getPaymentHistory().getMember().getId()); // 로그인 된 사용자와 요청 사용자 비교

            String uploadsDir = "src/main/resources/static/uploads/reviewimg/";

            // 각 이미지 파일에 대해 업로드 및 DB 저장 수행
            for (MultipartFile image : images) {
                String fileName = UUID.randomUUID().toString().replace("-", "") + "_" + Images.getOriginalFilename();
                String filePath = uploadsDir + fileName; // 실제 이미지가 저장되는 경로
                String dbFilePath = "/uploads/reviewimg/" + fileName; // 서버가 "가짜 주소"를 만들어서 실제 파일이 있는 곳으로 연결
                // http://example.com/uploads/reviewimg/f3b2c1d1.jpg = src/main/resources/static/uploads/reviewimg/f3b2c1d1-Images1.jpg

                saveImagesAsJpeg(Images, filePath);

                ReviewImg reviewImg = new ReviewImg(review, filePath);
                reviewImg.setReview(review);
                reviewImg.setReviewImgPath(dbFilePath);
                reviewImgRepository.save(reviewImg);
            }
        } catch (IOException e) {
            // 파일 저장 중 오류가 발생한 경우 처리
            e.printStackTrace();
        }
    }

    /**
     * Images JPEG 형식으로 저장
     * @param Images
     * @param filePath
     * @throws IOException
     * MultipartFile**로 받은 이미지를 바이트 스트림으로 읽어 BufferedImages 객체로 변환
     *      getInputStream() : upload Images 를 byte stream 으로 읽는다.
     *      ImagesIO.read : BufferedImages 객체 변환
     *      BufferedImages 를 사용하면 이미지 데이터를 직접 다룰 수 있다. ex) size, color, filter 적용 등등
     */
    private void saveImagesAsJpeg(MultipartFile Images, String filePath) throws IOException {
        BufferedImages bufferedImages = ImagesIO.read(Images.getInputStream());
        File outputFile = new File(filePath);
        ImagesIO.write(bufferedImages, "jpeg", outputFile);
    }

    /**
     * 리뷰 이미지 삭제
     * @param reviewImgId
     */
    public void deleteReviewImg(Long reviewImgId) {
        ReviewImg reviewImg = reviewImgRepository.findById(reviewImgId)
                .orElseThrow(() -> new NoSuchElementException("해당 사진을 찾을 수 없습니다."));

        String ImagesPath = "src/main/resources/static" + reviewImg.getReviewImgPath();

        reviewImgRepository.delete(reviewImg);

        deleteImagesFile(ImagesPath);
    }

    /**
     * DB에서 이미지 삭제 후, 서버에서도 삭제하는 메서드
     * @param ImagesPath
     */
    public static void deleteImagesFile(String ImagesPath) {
        try {
            Path path = Paths.get(ImagesPath);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // 파일 삭제 중 오류 발생 시 예외 처리
            e.printStackTrace();
        }
    }

    /**
     * 리뷰 이미지 리스트 보기
     * @param reviewId
     * @return
     */
    public List<ReviewImg> getReviewImg(Long reviewId) {
        return reviewImgRepository.findByReview_ReviewId(reviewId);
    }

    /**
     * 이미지 저장 메서드
     * @param Images
     * @paran filePath
     * @throws IOException
     */
    private void saveImages(MultipartFile Images, String filePath) throws IOException {
        Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        Files.write(path, Images.getBytes());
    }
}
