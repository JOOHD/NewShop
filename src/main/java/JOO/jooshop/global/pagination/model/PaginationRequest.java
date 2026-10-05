package JOO.jooshop.global.pagination.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaginationRequest {

    private int page = 1;  // 기본: 1페이지
    private int size = 10; // 기본: 10개씩
    private String sort = "id"; // 기본 정렬 컬럼
    private String direction = "DESC"; // 기본 내림차순

    // 조회 시작 위치 계산 — (페이지-1) × 크기
    public int getOffset() {
        return (page - 1) * size;
    }
}
