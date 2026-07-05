package JOO.jooshop.members.service;

import JOO.jooshop.global.exception.customException.ExistingMemberException;
import JOO.jooshop.global.mail.service.EmailMemberService;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.members.entity.enums.MemberStatus;
import JOO.jooshop.members.model.request.JoinMemberRequest;
import JOO.jooshop.members.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * MemberAccountService 단위 테스트.
 *
 * @ExtendWith(MockitoExtension.class) — Spring 컨텍스트 없이 Mockito만 사용.
 * 빠르고 단위 테스트 본연의 목적에 충실.
 *
 * @Mock — 실제 Bean 대신 가짜 객체. DB/메일 서버 없이 동작.
 * @InjectMocks — 테스트 대상. Mock들을 생성자로 주입.
 */
@ExtendWith(MockitoExtension.class)
class MemberAccountServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private EmailMemberService emailMemberService;

    @InjectMocks
    private MemberAccountService memberAccountService;

    // ========================================================
    // 회원가입 테스트
    // ========================================================
    @Nested
    @DisplayName("회원가입")
    class RegisterMember {

        private JoinMemberRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new JoinMemberRequest(
                    "test@example.com",
                    "password123!",
                    "password123!",
                    "홍길동",
                    "길동이",
                    "010-1234-5678"
            );
        }

        @Test
        @DisplayName("정상 회원가입 시 Member가 저장된다")
        void registerMember_success() {
            // given
            given(memberRepository.existsByEmail(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded_password");

            Member savedMember = Member.registerGeneral(
                    "test@example.com", "encoded_password",
                    "홍길동", "길동이", "010-1234-5678", "uuid"
            );
            given(memberRepository.save(any(Member.class))).willReturn(savedMember);

            // when
            Member result = memberAccountService.registerMember(validRequest);

            // then
            assertThat(result.getEmail()).isEqualTo("test@example.com");
            verify(memberRepository, times(1)).save(any(Member.class));
            // 이메일 발송이 호출됐는지 검증
            verify(emailMemberService, times(1)).sendEmailVerification(anyString());
        }

        @Test
        @DisplayName("중복 이메일로 가입 시 ExistingMemberException 발생")
        void registerMember_duplicateEmail_throwsException() {
            // given — 이미 같은 이메일이 존재
            given(memberRepository.existsByEmail("test@example.com")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> memberAccountService.registerMember(validRequest))
                    .isInstanceOf(ExistingMemberException.class);

            // DB 저장이 호출되지 않았음을 검증
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("비밀번호 불일치 시 예외 발생")
        void registerMember_passwordMismatch_throwsException() {
            // given
            JoinMemberRequest mismatchRequest = new JoinMemberRequest(
                    "test@example.com",
                    "password123!",
                    "different!",    // ← 불일치
                    "홍길동", "길동이", "010-1234-5678"
            );
            given(memberRepository.existsByEmail(anyString())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> memberAccountService.registerMember(mismatchRequest))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ========================================================
    // 회원 조회 테스트
    // ========================================================
    @Nested
    @DisplayName("회원 조회")
    class FindMember {

        @Test
        @DisplayName("존재하는 memberId로 조회 시 Member 반환")
        void findMemberById_success() {
            // given
            Member member = Member.registerGeneral(
                    "test@example.com", "pw", "이름", "닉네임", "010-0000-0000", "uuid"
            );
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));

            // when
            Member result = memberAccountService.findMemberById(1L);

            // then
            assertThat(result.getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("존재하지 않는 memberId 조회 시 예외 발생")
        void findMemberById_notFound_throwsException() {
            // given
            given(memberRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> memberAccountService.findMemberById(999L))
                    .isInstanceOf(RuntimeException.class); // MemberNotFoundException
        }
    }

    // ========================================================
    // 회원 상태 변경 테스트 (도메인 메서드 검증)
    // ========================================================
    @Nested
    @DisplayName("회원 상태 변경")
    class MemberStatus {

        @Test
        @DisplayName("ban() 호출 시 회원 상태가 BANNED로 변경된다")
        void ban_member() {
            // given — DB 저장 없이 도메인 메서드 직접 테스트
            Member member = Member.registerGeneral(
                    "test@example.com", "pw", "이름", "닉네임", "010-0000-0000", "uuid"
            );

            // when
            member.ban();

            // then
            assertThat(member.getStatus()).isEqualTo(MemberStatus.BANNED);
        }

        @Test
        @DisplayName("activate() 호출 시 회원 상태가 ACTIVE로 변경된다")
        void activate_member() {
            Member member = Member.registerGeneral(
                    "test@example.com", "pw", "이름", "닉네임", "010-0000-0000", "uuid"
            );
            member.ban(); // BANNED로 먼저 변경

            member.activate();

            assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        }
    }
}
