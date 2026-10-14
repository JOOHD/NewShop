package JOO.jooshop.members.service;

import JOO.jooshop.global.exception.customException.ExistingMemberException;
import JOO.jooshop.global.exception.customException.InvalidCredentialsException;
import JOO.jooshop.global.exception.customException.MemberNotFoundException;
import JOO.jooshop.global.mail.service.EmailMemberService;
import JOO.jooshop.members.entity.Member;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * MemberAccountService 단위 테스트.
 *
 * [리팩토링 반영]
 * - 기존: MemberStatus enum + member.getStatus() 를 검증하는 테스트였으나,
 *   실제 Member 엔티티는 상태를 boolean 플래그(active/banned/...)로 관리하도록
 *   설계되어 있어 isActive()/isBanned() 기준으로 재작성
 * - JoinMemberRequest는 생성자가 없는 순수 바인딩 DTO라 ReflectionTestUtils로 값 주입
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

    private JoinMemberRequest buildRequest(String email, String password1, String password2,
                                            String username, String nickname, String phoneNumber) {
        JoinMemberRequest request = new JoinMemberRequest();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password1", password1);
        ReflectionTestUtils.setField(request, "password2", password2);
        ReflectionTestUtils.setField(request, "username", username);
        ReflectionTestUtils.setField(request, "nickname", nickname);
        ReflectionTestUtils.setField(request, "phoneNumber", phoneNumber);
        return request;
    }

    // ========================================================
    // 회원가입 테스트
    // ========================================================
    @Nested
    @DisplayName("회원가입")
    class RegisterMember {

        private JoinMemberRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = buildRequest(
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
        void registerMember_success() throws Exception {
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
            JoinMemberRequest mismatchRequest = buildRequest(
                    "test@example.com",
                    "password123!",
                    "different!",    // ← 불일치
                    "홍길동", "길동이", "010-1234-5678"
            );
            given(memberRepository.existsByEmail(anyString())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> memberAccountService.registerMember(mismatchRequest))
                    .isInstanceOf(RuntimeException.class); // InvalidCredentialsException
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
    class AccountStatusChange {

        @Test
        @DisplayName("ban() 호출 시 정지 상태가 된다")
        void ban_member() {
            // given — DB 저장 없이 도메인 메서드 직접 테스트
            Member member = Member.registerGeneral(
                    "test@example.com", "pw", "이름", "닉네임", "010-0000-0000", "uuid"
            );

            // when
            member.ban();

            // then
            assertThat(member.isBanned()).isTrue();
        }

        @Test
        @DisplayName("unban() 호출 시 정지가 해제된다")
        void unban_member() {
            Member member = Member.registerGeneral(
                    "test@example.com", "pw", "이름", "닉네임", "010-0000-0000", "uuid"
            );
            member.ban();

            member.unban();

            assertThat(member.isBanned()).isFalse();
        }

        @Test
        @DisplayName("deactivate() 호출 시 비활성 상태가 된다")
        void deactivate_member() {
            Member member = Member.registerGeneral(
                    "test@example.com", "pw", "이름", "닉네임", "010-0000-0000", "uuid"
            );

            member.deactivate();

            assertThat(member.isActive()).isFalse();
        }

        @Test
        @DisplayName("activate() 호출 시 다시 활성 상태가 된다")
        void activate_member() {
            Member member = Member.registerGeneral(
                    "test@example.com", "pw", "이름", "닉네임", "010-0000-0000", "uuid"
            );
            member.deactivate();

            member.activate();

            assertThat(member.isActive()).isTrue();
        }
    }

    // ========================================================
    // 서비스 레벨 상태 변경 — 이미 그 상태면 예외 (Repository는 Mock)
    // ========================================================
    @Nested
    @DisplayName("서비스 상태 변경 (중복 요청 방어)")
    class ServiceStatusChange {

        private Member newMember() {
            return Member.registerGeneral("test@example.com", "pw", "이름", "닉네임", "010-0000-0000", "uuid");
        }

        @Test
        @DisplayName("ban()은 정지 상태로 바꾸고, 이미 정지된 계정이면 IllegalStateException")
        void ban_service() {
            Member member = newMember();
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));

            memberAccountService.ban(1L);
            assertThat(member.isBanned()).isTrue();

            assertThatThrownBy(() -> memberAccountService.ban(1L))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("unban()은 정지 상태가 아니면 IllegalStateException")
        void unban_notBanned_throws() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(newMember()));

            assertThatThrownBy(() -> memberAccountService.unban(1L))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("activate()는 이미 활성 계정이면 IllegalStateException, deactivate() 후에는 성공")
        void activate_deactivate_service() {
            Member member = newMember();
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));

            assertThatThrownBy(() -> memberAccountService.activate(1L))
                    .isInstanceOf(IllegalStateException.class);

            memberAccountService.deactivate(1L);
            assertThat(member.isActive()).isFalse();

            memberAccountService.activate(1L);
            assertThat(member.isActive()).isTrue();
        }
    }

    // ========================================================
    // 비밀번호 변경
    // ========================================================
    @Nested
    @DisplayName("비밀번호 변경")
    class ResetPassword {

        @Test
        @DisplayName("현재 비밀번호가 맞고 새 비밀번호가 일치하면 암호화된 값으로 변경된다")
        void resetPassword_success() {
            Member member = Member.registerGeneral("test@example.com", "oldEncoded", "이름", "닉네임", "010-0000-0000", "uuid");
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(passwordEncoder.matches("oldPw", "oldEncoded")).willReturn(true);
            given(passwordEncoder.encode("newPw")).willReturn("newEncoded");

            memberAccountService.resetPassword(1L, "oldPw", "newPw", "newPw");

            assertThat(member.getPassword()).isEqualTo("newEncoded");
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 InvalidCredentialsException, 비밀번호는 바뀌지 않는다")
        void resetPassword_wrongCurrent_throws() {
            Member member = Member.registerGeneral("test@example.com", "oldEncoded", "이름", "닉네임", "010-0000-0000", "uuid");
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(passwordEncoder.matches("wrongPw", "oldEncoded")).willReturn(false);

            assertThatThrownBy(() -> memberAccountService.resetPassword(1L, "wrongPw", "newPw", "newPw"))
                    .isInstanceOf(InvalidCredentialsException.class);

            assertThat(member.getPassword()).isEqualTo("oldEncoded");
        }

        @Test
        @DisplayName("새 비밀번호와 확인 값이 다르면 InvalidCredentialsException")
        void resetPassword_confirmMismatch_throws() {
            Member member = Member.registerGeneral("test@example.com", "oldEncoded", "이름", "닉네임", "010-0000-0000", "uuid");
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(passwordEncoder.matches("oldPw", "oldEncoded")).willReturn(true);

            assertThatThrownBy(() -> memberAccountService.resetPassword(1L, "oldPw", "newPw", "otherPw"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    // ========================================================
    // 이메일 / 소셜 ID 조회
    // ========================================================
    @Nested
    @DisplayName("이메일·소셜 ID 조회")
    class FindMemberByEmailAndSocialId {

        @Test
        @DisplayName("없는 이메일 조회 시 MemberNotFoundException, Optional 조회는 빈 값")
        void findByEmail_notFound() {
            given(memberRepository.findByEmail("none@example.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberAccountService.findMemberByEmail("none@example.com"))
                    .isInstanceOf(MemberNotFoundException.class);
            assertThat(memberAccountService.findMemberByEmailOptional("none@example.com")).isEmpty();
        }

        @Test
        @DisplayName("없는 socialId 조회 시 MemberNotFoundException")
        void findBySocialId_notFound() {
            given(memberRepository.findBySocialId("kakao-1")).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberAccountService.findMemberBySocialId("kakao-1"))
                    .isInstanceOf(MemberNotFoundException.class);
        }
    }
}
