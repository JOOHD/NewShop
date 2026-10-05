package JOO.jooshop.Inquiry.model;

import JOO.jooshop.Inquiry.entity.Inquiry;
import JOO.jooshop.Inquiry.entity.enums.InquiryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class InquiryDto {
    private Long inquiryId;
    private Long memberId;
    private Long productId;
    private String name;
    private String email;
    InquiryType inquiryType;
    private String inquiryTitle;
    private String inquiryContent;
    private String password;
    private LocalDateTime createdAt;
    private Boolean isSecret;
    private Boolean isResponse;
    private List<InquiryReplyDto> replies;

    // 문의 DTO 생성자 — 문의 정보와 답변 목록 포함
    @Builder
    public InquiryDto(Long inquiryId, Long memberId, Long productId, String name, String email,
                      InquiryType inquiryType, String inquiryTitle, String inquiryContent, String password,
                      LocalDateTime createdAt, Boolean isSecret, Boolean isResponse, List<InquiryReplyDto> replies) {
        this.inquiryId = inquiryId;
        this.memberId = memberId;
        this.productId = productId;
        this.name = name;
        this.email = email;
        this.inquiryType = inquiryType;
        this.inquiryTitle = inquiryTitle;
        this.inquiryContent = inquiryContent;
        this.password = password;
        this.createdAt = createdAt;
        this.isSecret = isSecret;
        this.isResponse = isResponse;
        this.replies = replies;
    }

    // Entity -> DTO 변환용 생성자
    public InquiryDto(Inquiry inquiry) {
        this( // 한 번에 변환하기 위해 체이닝된 생성자 호출 this() 사용.
                inquiry.getInquiryId(),
                inquiry.getMember().getId(),
                inquiry.getProduct().getProductId(),
                inquiry.getName(),
                inquiry.getEmail(),
                inquiry.getInquiryType(),
                inquiry.getInquiryTitle(),
                inquiry.getInquiryContent(),
                inquiry.getPassword(),
                inquiry.getCreatedAt(),
                inquiry.getIsSecret(),
                inquiry.getIsResponse(),
                inquiry.getReplies().stream()
                        .map(InquiryReplyDto::new)
                        .collect(Collectors.toList())

        );

    }

    // 문의 엔티티를 DTO로 변환 (includeContent=false면 내용 제외 — 비밀글 등)
    public static InquiryDto mapInquiryToDto(Inquiry inquiry, boolean includeContent) {
        if (inquiry == null) {
            return null;
        }

        return InquiryDto.builder()
                .inquiryId(inquiry.getInquiryId())
                .memberId(inquiry.getMember() != null ? inquiry.getMember().getId() : null)
                .productId(inquiry.getProduct() != null ? inquiry.getProduct().getProductId() : null)
                .name(inquiry.getName())
                .email(inquiry.getEmail())
                .inquiryType(inquiry.getInquiryType())
                .inquiryTitle(inquiry.getInquiryTitle())
                .createdAt(inquiry.getCreatedAt())
                .isSecret(inquiry.getIsSecret())
                .replies(inquiry.getReplies() != null
                        ? inquiry.getReplies().stream().map(InquiryReplyDto::new).collect(Collectors.toList())
                        : null)
                .inquiryContent(includeContent ? inquiry.getInquiryContent() : null)
                .password(includeContent ? inquiry.getPassword() : null)
                .build();
    }

}
