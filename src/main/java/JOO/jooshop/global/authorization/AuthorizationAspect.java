package JOO.jooshop.global.authorization;

import JOO.jooshop.members.entity.enums.MemberRole;

import org.springframework.beans.factory.annotation.Value;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect // 특정 지점에서 부가적인 처리를 수행할 수 있는 클래스
@Component
public class AuthorizationAspect {

    @Value("${custom.requires-role.enabled:true}") // 프로퍼티 값을 가져옴
    private boolean requiresRoleEnabled;

    // @RequiresRole이 붙은 메서드 실행 전 역할 검사 (설정으로 비활성화 가능)
    @Before("@annotation(requireRole)") // requireRole 실행 전, checkRole 메서드 실행
    public void checkRole(RequiresRole requireRole) {
        if (!requiresRoleEnabled) {
            // requiresRoleEnabled = false, 어노테이션 체크를 스킵 
            // @RequiresRole({MemberRole.ADMIN, MemberRole.SELLER}) 이 코드를 스킵
            return;
        }

        // 현재 로그인 사용자 역할 가져오기
        MemberRole userRole = MemberAuthorizationUtil.getLoginMemberRole();

        boolean hasRequiredRole = false;
        // 현재 로그인한 userRole 이 하나라도 일치하면 접근 가능
        for (MemberRole requiredRole : requireRole.value()) {
            if (userRole == requiredRole) {
                hasRequiredRole = true;
                break;
            }
        }

        // StringBuilder : 필수 권한 목록을 하나의 문자열로 결합, 예외 메시지에 포함
        StringBuilder requiredRolesBuilder = new StringBuilder();

        // role 목록을 "," 를 붙여 구분자로 사용, requiredRole.value() 가져온 값은 enum
        for (MemberRole requiredRole : requireRole.value()) {
            requiredRolesBuilder.append(requiredRole.toString()).append(", ");
        }

        String requiredRoles = requiredRolesBuilder.toString();
        // 마지막 콤마와 공백 제거, "ADMIN, USER, " -> "ADMIN, USER"
        if (requiredRoles.length() > 0) {
            requiredRoles = requiredRoles.substring(0, requiredRoles.length() - 2);
        }

        if (!hasRequiredRole) {
            throw new SecurityException("사용자 권한 없음\n - Required Roles : " + requiredRoles + ", Request User Role : " + userRole);
        }
    }
}
