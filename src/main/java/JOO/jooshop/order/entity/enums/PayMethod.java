package JOO.jooshop.order.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PayMethod {
    card,trans, vbank, phone;
    // 신용카드, 실시간 계좌이체, 가상계좌, 휴대폰 결제

    // 문자열을 결제수단으로 변환 (대소문자 무시)
    @JsonCreator // (static factory method) Enum.valueOf() 호출 x
    public static PayMethod from(String s) {
        return PayMethod.valueOf(s.toLowerCase()); // 대소문자 구분x
    }
}
