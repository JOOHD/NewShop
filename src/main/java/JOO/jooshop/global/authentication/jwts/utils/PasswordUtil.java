package JOO.jooshop.global.authentication.jwts.utils;

import java.security.SecureRandom;

/**
 * 임시 비밀번호 생성 유틸 클래스.
 */
public final class PasswordUtil {

    private static final int DEFAULT_PASSWORD_LENGTH = 8;
    private static final char[] CHAR_SET = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
    };

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String generateRandomPassword() {
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < DEFAULT_PASSWORD_LENGTH; i++) {
            int index = RANDOM.nextInt(CHAR_SET.length);
            password.append(CHAR_SET[index]);
        }

        return password.toString();
    }
}