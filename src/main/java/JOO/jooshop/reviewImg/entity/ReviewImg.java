package JOO.jooshop.reviewImg.entity;

import JOO.jooshop.review.entity.Review;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "review_Images")
public class ReviewImg {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_img_id")
    private Long reviewImgId;

    @Column(name = "review_Images_path")
    private String reviewImgPath;

    @ManyToOne
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    public ReviewImg(Review review, String reviewImgPath) {
        this.review = review;
        this.reviewImgPath = reviewImgPath;
    }

    public ReviewImg() {

    }
}
