package JOO.jooshop.global.validation;

import JOO.jooshop.product.model.ProductRequestDto ;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DiscountRateValidator implements ConstraintValidator<ValidDiscountRate, ProductRequestDto> {
    // 할인 여부와 할인율 조합 검증 — 할인이면 1~100, 할인이 아니면 0 또는 null
    @Override // 25.04.13 에러 메시지 추가
    public boolean isValid(ProductRequestDto dto, ConstraintValidatorContext context) {
       Boolean isDiscount = dto.getIsDiscount();
       Integer discountRate = dto.getDiscountRate();

       if (Boolean.TRUE.equals(isDiscount)) { // 할인 적용 됨
           if (!isValidDiscountRate(discountRate)) { // 1 ~ 100 (필수 입력)
               context.disableDefaultConstraintViolation(); // 기본 에러 메시지 제거 (커스텀한 메시지만 출력)
               context.buildConstraintViolationWithTemplate("할인율은 1~100 사이의 값이어야 합니다.")
                       .addPropertyNode("discountRate") // 어떤 필드에 연결할지 지정
                       .addConstraintViolation(); // 이걸 호출해야 에러 메시지가 유효성 검사 결과에 포함
               return false; // 위에서 등록한 커스텀 메시지와 함께 실패 응답이 클라이언트로
           }
       } else { // 할인 적용 안됨
           if (!isValidNonDiscountRate(discountRate)) { // 	null 또는 0 (입력 금지)
               context.disableDefaultConstraintViolation();
               context.buildConstraintViolationWithTemplate("할인이 적용되지 않으면 할인율을 입력할 수 없습니다.")
                       .addPropertyNode("discountRate")
                       .addConstraintViolation();
               return false;
           }
       }

       return true;
    }
    // 할인율이 1~100 범위인지
    private boolean isValidDiscountRate(Integer discountRate) {
        return discountRate != null && discountRate >= 1 && discountRate <= 100;
    }

    // 할인이 아닐 때 할인율이 0 또는 null인지
    private boolean isValidNonDiscountRate(Integer discountRate) {
        return discountRate == null || discountRate == 0;
    }
}
