package JOO.jooshop.productVariant.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Size {
    XS,
    S,
    M,
    L,
    XL,
    XXL,
    FREE;

//    private String description;
//
//    Size(String description) {
//        this.description = description;
//    }
//
//    @JsonValue // JSON 에서 해당 값이 어떻게 보여질지 정의
//    public String getDescription() {
//        return description;
//    }
//
//    @JsonCreator // JSON 요청 시, 문자열을 해당 Enum 으로 매핑
//    public static Size fromDescription(String description) {
//        for (Size size : Size.values()) {
//            if (size.getDescription().equalsIgnoreCase(description)) {
//                return size; // "Free"(입력 값) -> Size.FREE 변환
//            }
//        }
//        throw new IllegalArgumentException("Unknown size: " + description);
//    }
}
