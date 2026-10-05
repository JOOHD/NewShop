package JOO.jooshop.global.config.mail;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class SmtpAuthenticator extends Authenticator {
    // SMTP 인증기 — 메일 서버에 보낼 인증 정보(아이디/비밀번호)를 제공하는 Authenticator
    public SmtpAuthenticator() {
        super();
    }

    // SMTP 인증 정보(아이디/비밀번호) 제공
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
