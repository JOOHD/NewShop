package JOO.jooshop.Inquiry.model;

import JOO.jooshop.Inquiry.entity.Inquiry;
import JOO.jooshop.Inquiry.entity.enums.InquiryType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InquiryUpdateDto {
    private InquiryType inquiryType;
    @NotBlank(message = "내용은 필수입니다.")
    private String inquiryContent;
    private String password;

    // 문의 엔티티를 수정용 DTO로 변환
    public InquiryUpdateDto(Inquiry inquiry) {
        this(
                inquiry.getInquiryType(),
                inquiry.getInquiryContent(),
                inquiry.getPassword()
        );

    }
}
