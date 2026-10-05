package JOO.jooshop.Inquiry.entity;

import JOO.jooshop.members.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "inquiry_reply")
public class InquiryReply {
    @Id
    @SequenceGenerator(
            name = "inquiry_reply_sequence",
            sequenceName = "inquiry_reply_sequence",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "inquiry_reply_sequence"
    )
    @Column(name = "reply_id")
    private Long inquiryReplyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inquiry_id", nullable = false)
    private Inquiry inquiry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_by", nullable = false)
    private Member replyBy;

    @Column(name = "reply_title", nullable = false)
    private String replyTitle;

    @Column(name = "reply_content", nullable = false)
    private String replyContent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    // JPA용 기본 생성자 — 작성 시각을 현재로 설정
    public InquiryReply() {
        this.createdAt = LocalDateTime.now();

    }

    // 문의 답변 생성자 — 문의, 답변자, 제목/내용 설정
    public InquiryReply(Inquiry inquiry, Member replyBy, String replyTitle, String replyContent) {
        this.inquiry = inquiry;
        this.replyBy = replyBy;
        this.replyTitle = replyTitle;
        this.replyContent = replyContent;
        this.createdAt = LocalDateTime.now();

    }

}
