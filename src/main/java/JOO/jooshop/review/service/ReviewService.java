package JOO.jooshop.review.service;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.repository.MemberRepository;
import JOO.jooshop.payment.entity.PaymentHistory;
import JOO.jooshop.payment.repository.PaymentRepository;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.repository.ProductRepository;
import JOO.jooshop.review.entity.Review;
import JOO.jooshop.review.model.ReviewCreateDto;
import JOO.jooshop.review.model.ReviewDto;
import JOO.jooshop.review.repository.ReviewRepository;
import JOO.jooshop.reviewImg.entity.ReviewImg;
import JOO.jooshop.reviewImg.repository.ReviewImgRepository;
import JOO.jooshop.reviewImg.service.ReviewImagesService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.lang.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import static JOO.jooshop.global.exception.ResponseMessageConstants.*;
import static JOO.jooshop.global.authorization.MemberAuthorizationUtil.*;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ReviewService {
    public final PaymentRepository paymentRepository;
    public final ReviewRepository reviewRepository;
    public final ProductRepository productRepository;
    public final MemberRepository memberRepository;
    public final ModelMapper modelMapper;
    public final ReviewImagesService reviewImagesService;
    public final ReviewImgRepository reviewImgRepository;

    /**
     * 리뷰 작성
     * @param request reviewTitle, reviewContent, rating
     * @param paymentId paymentHistoryId
     * @return message
     */
    public Review createReview(ReviewCreateDto request, @Nullable List<MultipartFile> images, Long paymentId) {
        PaymentHistory paymentHistory = findPaymentHistoryById(paymentId);
        verifyUserIdMatch(paymentHistory.getMember().getId());

        // 이미 Review 가 존재하는지 확인
        if (Boolean.TRUE.equals(paymentHistory.getReview())) {
            throw new IllegalStateException("이미 후기가 작성되었습니다.");
        }

        // entity 객체 생성, 저장, 리뷰 작성 여부 true
        Review review = new Review(paymentHistory, request.getReviewContent(), request.getRating());
        reviewRepository.save(review);
        paymentHistory.setReview(true);

        reviewImagesService.uploadReviewImg(review.getReviewId(), images);
        return review;
    }

    /**
     * 모든 리뷰 보기
     * @return
     */
    public List<ReviewDto> allReview() {
        return reviewRepository.findAll()
                .stream()
                .map(ReviewDto::new)
                .collect(Collectors.toList());

        // new ReviewDto(review.getId(), review.getContent(), review.getRating()))
        // Review Entity -> ReviewDto 변환, (ReviewDto 의 생성자를 직접 호출하여 객체를 생성)
    }

    /**
     * 특정 상품의 리뷰 모아보기
     * @param productId
     * @return
     */
    public List<ReviewDto> findReviewByProduct(Long productId) {
        Product product = findProductId(productId);
        return reviewRepository.findByPaymentHistoryProduct(product)
                .stream()
                .map(ReviewDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 특정 회원의 리뷰 모아보기
     * @param memberId
     * @return
     */
    public List<ReviewDto> findReviewByUser(Long memberId) {
        Member member = findMemberById(memberId);
        return reviewRepository.findByPaymentHistoryMember(member)
                .stream()
                .map(ReviewDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 리뷰 수정
     * @param updateRequest (reviewContent, rating)
     * @param memberId 수정 요청 당사자
     * @return
     */
    public Review updateReview(ReviewCreateDto updateRequest, Long reviewId, Long memberId) {
        Review currentReview = findReviewById(reviewId);
        validateReview(currentReview, memberId);

        currentReview.updateReview(updateRequest.getReviewContent(), updateRequest.getRating());
        return reviewRepository.save(currentReview);
    }

    /**
     * 리뷰 삭제
     * @param reviewId
     *
     * 1. 리뷰는 결제와 연결되어 있다. (리뷰가 작성될 때, PaymentHistory 의 review = true 설정
     * 2. review = false 설정을 먼저 하는 이유? (이미지 삭제 먼저 할 수도 있을텐데..)
     *      - 리뷰 삭제 중 이미지 파일 삭제 시 예외가 발생함.
     *      - 예외가 발생하면 이후 로직(review = false 설정)이 실행되지 못할 수도 있음.
     *      - 그러면 결제 내역(PaymentHistory)에는 여전히 review = true로 남아 있음.
     *      - 하지만 실제 리뷰는 삭제되어 존재하지 않음 → 데이터 정합성이 깨짐.
     */
    public void deleteReview(Long reviewId, Long memberId) {

        Review currentReview = findReviewById(reviewId);
        validateReview(currentReview, memberId);

        PaymentHistory paymentHistory = currentReview.getPaymentHistory();
        paymentHistory.setReview(false);

        deleteReviewImages(reviewId);
        reviewRepository.delete(currentReview);
    }

    /**
     * 결제 내역 조회 (예외 포함)
     */
    private PaymentHistory findPaymentHistoryById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("해당 주문 내역을 찾을 수 없습니다."));
    }

    /**
     * 상품 조회 (예외 포함)
     */
    private Product findProductId(Long productId) {
        return productRepository.findByProductId(productId)
                .orElseThrow(() -> new NoSuchElementException(PRODUCT_NOT_FOUND));
    }

    /**
     * 회원 조회 (예외 포함)
     */
    private Member findMemberById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException(MEMBER_NOT_FOUND));
    }

    /**
     * 리뷰 조회 (예외 포함)
     */
    private Review findReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NoSuchElementException(WRITING_NOT_FOUND));
    }

    /**
     * 리뷰 작성자 검증
     */
    private void validateReview(Review review, Long memberId) {
        verifyUserIdMatch(memberId);
        if (!review.getPaymentHistory().getMember().getId().equals(memberId)) {
            throw new SecurityException(ACCESS_DENIED);
        }
    }

    /**
     * 리뷰 이미지 업로드
     */
    private void uploadReviewImages(Long reviewId, @Nullable List<MultipartFile> images) {
        if (images != null && !Objects.equals(images.get(0).getOriginalFilename(), "")) {
            reviewImagesService.uploadReviewImg(reviewId, images);
        }
    }

    /**
     * 리뷰 이미지 삭제
     */
    private void deleteReviewImages(Long reviewId) {
        List<ReviewImg> reviewImgList = reviewImgRepository.findByReview_ReviewId(reviewId);
        if (reviewImgList != null) {
            for (ReviewImg reviewImg : reviewImgList) {
                reviewImgRepository.delete(reviewImg);
                reviewImagesService.deleteImagesFile("src/main/resources/static" + reviewImg.getReviewImgPath());
            }
        }
    }
}












