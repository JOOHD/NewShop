package JOO.jooshop.Inquiry.controller;

import JOO.jooshop.Inquiry.model.InquiryReplyDto;
import JOO.jooshop.Inquiry.service.InquiryReplyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static JOO.jooshop.global.exception.ResponseMessageConstants.DELETE_SUCCESS;
@Tag(name = "문의 답변", description = "관리자/판매자의 문의 답변 등록/수정/삭제")
@RestController
@RequestMapping("/api/v1/inquiry/reply")
@RequiredArgsConstructor
public class InquiryReplyController {
    private final InquiryReplyService replyService;

    /**
     * 문의 답변 등록
     *
     * @param replyRequest
     * @param inquiryId
     * @return
     */
    @PostMapping("/new/{inquiryId}")
    public ResponseEntity<String> createReply(@Valid @RequestBody InquiryReplyDto replyRequest, @PathVariable("inquiryId") Long inquiryId) throws Exception {
        Long createdId = replyService.createReply(replyRequest, inquiryId);

        return ResponseEntity.status(HttpStatus.CREATED).body("답변 등록 완료 : "+createdId);
    }

    @DeleteMapping("/{replyId}")
    public ResponseEntity<String> deleteInquiry(@PathVariable("replyId") Long replyId) {
        replyService.deleteReply(replyId);
        return ResponseEntity.ok().body(DELETE_SUCCESS);
    }
}
