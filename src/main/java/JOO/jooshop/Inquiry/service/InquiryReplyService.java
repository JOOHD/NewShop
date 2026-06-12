package JOO.jooshop.Inquiry.service;

import JOO.jooshop.Inquiry.entity.Inquiry;
import JOO.jooshop.Inquiry.entity.InquiryReply;
import JOO.jooshop.Inquiry.model.InquiryReplyDto;
import JOO.jooshop.Inquiry.repository.InquiryReplyRepository;
import JOO.jooshop.Inquiry.repository.InquiryRepository;
import JOO.jooshop.global.authorization.MemberAuthorizationUtil;
import JOO.jooshop.global.authorization.RequiresRole;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.members.service.MemberAccountService;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.util.NoSuchElementException;

import static JOO.jooshop.global.exception.ResponseMessageConstants.*;

@Service
@Transactional
@RequiredArgsConstructor
public class InquiryReplyService {

    private final InquiryReplyRepository inquiryReplyRepository;
    private final InquiryRepository inquiryRepository;
    private final MemberAccountService memberAccountService;
    private final JavaMailSender mailSender;

    /**
     * 문의 답변 등록 (ADMIN/SELLER 전용)
     */
    @RequiresRole({MemberRole.ADMIN, MemberRole.SELLER})
    public Long createReply(InquiryReplyDto replyRequest, Long inquiryId) throws Exception {
        Long memberId = MemberAuthorizationUtil.getLoginMemberId();
        Member member = memberAccountService.findMemberById(memberId);

        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new NoSuchElementException(WRITING_NOT_FOUND + " inquiryId: " + inquiryId));

        InquiryReply reply = new InquiryReply(inquiry, member, inquiry.getInquiryTitle(), replyRequest.getReplyContent());
        inquiryReplyRepository.save(reply);

        inquiry.setResponse(true);
        sendReplyNotice(inquiry, reply);

        return reply.getInquiryReplyId();
    }

    /** 문의 답변 삭제 (ADMIN/SELLER 전용) */
    @RequiresRole({MemberRole.ADMIN, MemberRole.SELLER})
    public void deleteReply(Long replyId) {
        InquiryReply existingReply = inquiryReplyRepository.findById(replyId)
                .orElseThrow(() -> new NoSuchElementException(WRITING_NOT_FOUND));
        inquiryReplyRepository.delete(existingReply);
    }

    public void sendReplyNotice(Inquiry inquiry, InquiryReply reply) throws MessagingException, UnsupportedEncodingException {
        String mailAddress = inquiry.getEmail();
        MimeMessage message = mailSender.createMimeMessage();

        message.addRecipients(Message.RecipientType.TO, mailAddress);
        message.setSubject("[JOOSHOP] 문의하신 상품에 대한 답변이 도착했습니다.");

        String body = "<div style='max-width: 1000px;'>"
                + "<div style='width: 85%; margin: 0 auto; color: rgb(7, 145, 163);'>"
                + "<h1 style='left:10vw'>JOOSHOP</h1>"
                + "<h1>문의주셔서 감사합니다.<br>아래 답변 내용을 확인해주세요.</h1>"
                + "<a href=''><b>문의내용 보러가기</b> / 추가 문의하기</a></div>"
                + "<div style='width: 85%; background-color: rgb(239, 247, 255); margin: 40px auto;'>"
                + "<p style='padding: 40px 50px; overflow: auto; width: auto; height: 100%; min-height: 180px; font-weight: bold;'>"
                + reply.getReplyContent()
                + "</p></div>"
                + "<div style='width: 85%; margin: 0 auto; margin-bottom: 150px;'>"
                + "<hr style='margin-bottom: 50px; border-style: dashed; border-width: 1px 0px 0px 0px; border-color: rgb(0,0,0);'>"
                + "[" + inquiry.getProduct().getProductName() + "] - " + inquiry.getInquiryTitle() + "<br>"
                + inquiry.getInquiryContent()
                + "</div></div>";

        message.setText(body, "utf-8", "html");
        message.setFrom(new InternetAddress("gokorea1214@naver.com", "PU_ADMIN"));
        mailSender.send(message);
    }
}
