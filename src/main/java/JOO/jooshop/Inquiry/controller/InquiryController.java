package JOO.jooshop.Inquiry.controller;

import JOO.jooshop.Inquiry.entity.Inquiry;
import JOO.jooshop.Inquiry.model.InquiryCreateDto;
import JOO.jooshop.Inquiry.model.InquiryDto;
import JOO.jooshop.Inquiry.model.InquiryUpdateDto;
import JOO.jooshop.Inquiry.service.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import static JOO.jooshop.global.exception.ResponseMessageConstants.*;

@RestController
@RequestMapping("/api/v1/inquiry")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    /** 전체 문의글 보기 */
    @GetMapping("")
    public List<InquiryDto> getAllInquiries() {
        return inquiryService.allInquiryList();
    }

    /** 특정 상품 문의글 리스트 */
    @GetMapping("/list/{productId}")
    public List<InquiryDto> getProductInquiries(@PathVariable("productId") Long productId) {
        return inquiryService.inquiryListByProductId(productId);
    }

    /**
     * 문의글 작성
     * - Spring Security Authentication 객체로 로그인 여부 판단
     *   (헤더 파싱보다 안전하고 명확함)
     */
    @PostMapping("/new/{productId}")
    public ResponseEntity<String> createInquiry(
            @Valid @RequestBody InquiryCreateDto requestDto,
            @PathVariable("productId") Long productId,
            Authentication authentication
    ) {
        try {
            boolean isLoggedIn = authentication != null && authentication.isAuthenticated();
            Long createdId = inquiryService.createInquiry(requestDto, productId, isLoggedIn);
            return ResponseEntity.status(HttpStatus.CREATED).body("문의 등록 완료. Id : " + createdId);
        } catch (HttpMessageNotReadableException e) {
            return ResponseEntity.badRequest().body("유효하지 않은 문의 유형입니다.");
        }
    }

    /** 문의글 상세보기 */
    @GetMapping("/{inquiryId}")
    public ResponseEntity<InquiryDto> getInquiryById(@PathVariable("inquiryId") Long inquiryId) {
        return ResponseEntity.ok(inquiryService.inquiryDetail(inquiryId));
    }

    /** 문의글 수정 */
    @PutMapping("/{inquiryId}")
    public ResponseEntity<String> updateInquiry(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody InquiryUpdateDto requestDto
    ) {
        Inquiry updated = inquiryService.updateInquiry(inquiryId, requestDto, requestDto.getPassword());
        return ResponseEntity.ok("수정 완료 : " + updated.getInquiryId());
    }

    /** 문의글 삭제 */
    @DeleteMapping("/{inquiryId}")
    public ResponseEntity<String> deleteInquiry(
            @PathVariable("inquiryId") Long inquiryId,
            @RequestHeader("password") String password
    ) {
        inquiryService.deleteInquiry(inquiryId, password);
        return ResponseEntity.ok(DELETE_SUCCESS);
    }
}
