package JOO.jooshop.reviewImg.controller;

import JOO.jooshop.reviewImg.entity.ReviewImg;
import JOO.jooshop.reviewImg.service.ReviewImagesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/review/img")
@RequiredArgsConstructor
public class ReviewImgController {
    private final ReviewImagesService reviewImagesService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadReviewImg(@RequestParam("reviewId") Long reviewId, @RequestParam("images") List<MultipartFile> images) {
        reviewImagesService.uploadReviewImg(reviewId, images);
        return ResponseEntity.status(HttpStatus.CREATED).body("사진 업로드 완료");
    }

    @DeleteMapping("/delete/{reviewImgId}")
    public ResponseEntity<String> deleteReviewImg(@PathVariable("reviewImgId") Long reviewImgId) {
        reviewImagesService.deleteReviewImg(reviewImgId);
        return ResponseEntity.status(HttpStatus.OK).body("사진 삭제 완료");
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<List<String>> getReviewImages(@PathVariable("reviewId") Long reviewId) {
        List<ReviewImg> images = reviewImagesService.getReviewImg(reviewId);
        if (!images.isEmpty()) {
            List<String> ImagesPaths = images.stream()
                    .map(ReviewImg::getReviewImgPath)
                    .collect(Collectors.toList());
            return ResponseEntity.ok().body(ImagesPaths);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
