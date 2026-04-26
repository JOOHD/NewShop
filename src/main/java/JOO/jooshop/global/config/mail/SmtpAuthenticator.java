package JOO.jooshop.global.config.mail;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class SmtpAuthenticator extends Authenticator {
    // SMTP 서버에 인증을 수행하는 역할, 보안적으로 보호된 메일 전송을 가능하게 함.
    // Authenticator : 인증정보(id, pw) 인증 처리
    public SmtpAuthenticator() {
        super();
    }

    @Override
    public PasswordAuthentication getPasswordAuthentication() {
        String username = "user";
        String password = "password";
        if (username.length() > 0 && password != null && password.length() > 0) {

            return new PasswordAuthentication(username, password.toCharArray());
        }

        return null;
    }
}
