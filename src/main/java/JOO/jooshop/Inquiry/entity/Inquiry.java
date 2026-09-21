package JOO.jooshop.Inquiry.entity;

import JOO.jooshop.Inquiry.validation.EnumConstraint;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.Inquiry.entity.enums.InquiryType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Entity
@Table(name = "inquiry_table")
public class Inquiry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long inquiryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "member_id", nullable = true)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "writer_name",nullable = false)
    private String name;

    @Column(name = "writer_email",nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "inquiry_type", nullable = false)
    @EnumConstraint
    private InquiryType inquiryType;

    @Column(name = "inquiry_title",nullable = false)
    private String inquiryTitle;

    @Column(name = "inquiry_content",nullable = false)
    private String inquiryContent;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "is_secret")
    private Boolean isSecret = true;

    @Column(name = "is_response")
    private Boolean isResponse = false;

    @OneToMany(mappedBy = "inquiry")
    private List<InquiryReply> replies;

    @Builder
    public Inquiry(Member member, Product product, String name, String email, InquiryType inquiryType, String inquiryTitle, String inquiryContent, String password) {
        this.member = member;
        this.product = product;
        this.name = name;
        this.email = email;
        this.inquiryType = inquiryType;
        this.inquiryTitle = inquiryTitle;
        this.inquiryContent = inquiryContent;
        this.password = password;
        this.createdAt = LocalDateTime.now();

    }

    public void createInquiryWriter(Member member, String name, String email) {
        this.member = member;
        this.name = name;
        this.email = email;
    }

    public void setResponse(Boolean response) {
        isResponse = response;
    }

}
