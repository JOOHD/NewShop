package JOO.jooshop.Inquiry.service;

import JOO.jooshop.Inquiry.entity.Inquiry;
import JOO.jooshop.Inquiry.model.InquiryCreateDto;
import JOO.jooshop.Inquiry.model.InquiryDto;
import JOO.jooshop.Inquiry.model.InquiryUpdateDto;
import JOO.jooshop.Inquiry.repository.InquiryRepository;
import JOO.jooshop.global.authorization.MemberAuthorizationUtil;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static JOO.jooshop.global.exception.ResponseMessageConstants.*;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final MemberAccountService memberAccountService;
    private final ProductRepository productRepository;

    /**
     * 문의 작성
     * - 로그인 사용자: SecurityContext에서 memberId 추출 후 Member 정보 조회
     * - 비회원: 요청 DTO의 이름/이메일 사용
     */
    @Transactional
    public Long createInquiry(InquiryCreateDto requestDto, Long productId, boolean isLoggedIn) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new NoSuchElementException(PRODUCT_NOT_FOUND));

        Inquiry inquiry = Inquiry.builder()
                .product(product)
                .inquiryType(requestDto.getInquiryType())
                .inquiryTitle(requestDto.getInquiryTitle())
                .inquiryContent(requestDto.getInquiryContent())
                .password(requestDto.getPassword())
                .build();

        if (isLoggedIn) {
            Long memberId = MemberAuthorizationUtil.getLoginMemberId();
            Member member = memberAccountService.findMemberById(memberId);
            inquiry.createInquiryWriter(member, member.getNickname(), member.getEmail());
        } else {
            inquiry.createInquiryWriter(null, requestDto.getName(), requestDto.getEmail());
        }

        inquiryRepository.save(inquiry);
        return inquiry.getInquiryId();
    }

    /** 전체 문의 리스트 */
    @Transactional(readOnly = true)
    public List<InquiryDto> allInquiryList() {
        List<InquiryDto> inquiryDtoList = new ArrayList<>();
        List<Inquiry> inquiryList = inquiryRepository.findAll();

        for (Inquiry inquiry : inquiryList) {
            inquiryDtoList.add(InquiryDto.mapInquiryToDto(inquiry, false));
        }

        return inquiryDtoList;
    }

    /** 특정 상품의 문의글 리스트 */
    @Transactional(readOnly = true)
    public List<InquiryDto> inquiryListByProductId(Long productId) {
        return inquiryRepository.findByProduct_ProductId(productId).stream()
                .map(inquiry -> InquiryDto.mapInquiryToDto(inquiry, false))
                .collect(Collectors.toList());
    }

    /** 문의글 상세보기 */
    @Transactional(readOnly = true)
    public InquiryDto inquiryDetail(Long inquiryId) {
        Inquiry inquiryDetail = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new NoSuchElementException(WRITING_NOT_FOUND));
        return InquiryDto.mapInquiryToDto(inquiryDetail, true);
    }

    /** 문의글 수정 */
    public Inquiry updateInquiry(Long inquiryId, InquiryUpdateDto requestDto, String password) {
        Inquiry existingInquiry = validatePasswordAndGetInquiry(inquiryId, password);
        inquiryRepository.updateInquiryFields(inquiryId, requestDto.getInquiryType(), requestDto.getInquiryContent());
        return inquiryRepository.save(existingInquiry);
    }

    /** 문의글 삭제 */
    public void deleteInquiry(Long inquiryId, String password) {
        Inquiry existingInquiry = validatePasswordAndGetInquiry(inquiryId, password);
        inquiryRepository.delete(existingInquiry);
    }

    /** 수정/삭제 시 비밀번호 검증 후 문의글 반환 */
    private Inquiry validatePasswordAndGetInquiry(Long inquiryId, String password) {
        Inquiry existingInquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new NoSuchElementException(WRITING_NOT_FOUND));

        if (!existingInquiry.getPassword().equals(password)) {
            throw new IllegalArgumentException("잘못된 비밀번호 입니다.");
        }

        return existingInquiry;
    }
}
