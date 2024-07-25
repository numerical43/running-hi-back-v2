package com.runninghi.runninghibackv2.domain.entity;

import com.runninghi.runninghibackv2.application.dto.post.request.CreatePostRequest;
import com.runninghi.runninghibackv2.domain.entity.vo.GpsDataVO;
import com.runninghi.runninghibackv2.domain.enumtype.Difficulty;
import com.runninghi.runninghibackv2.domain.enumtype.Role;
import com.runninghi.runninghibackv2.application.dto.post.request.UpdatePostRequest;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.locationtech.jts.geom.Point;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "TBL_POST")
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postNo;

    @ManyToOne
    @JoinColumn(name = "MEMBER_NO")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Comment("작성자 계정 아이디")
    private Member member;

    @Column
    @Comment("게시글 제목")
    private String postTitle;

    @Column
    @Nullable
    @Comment("게시글 내용")
    private String postContent;

    @Column
    @Comment("러닝코스 난이도")
    private Difficulty difficulty;

    @Column
    @Comment("신고 횟수")
    private int reportCnt;

    @Column
    @Comment("권한")
    private Role role;

    @Column
    @Comment("게시글 공유 여부")
    private Boolean status;

    @Embedded
    private GpsDataVO gpsDataVO;

    @Column
    @Comment("gps 파일 url")
    private String gpsUrl;

    @Column
    @Comment("메인페이지 표시 대표 데이터")
    private String mainData;

    @Builder
    public Post(Member member, @Nullable String postContent, Difficulty difficulty, Role role, String locationName,
                Point geometry, Boolean status, String gpxUrl, GpsDataVO gpsDataVO, String mainData, String postTitle) {
        this.member = member;
        this.postContent = postContent;
        this.difficulty = difficulty;
        this.reportCnt = 0;
        this.role = role;
        this.status = status;
        this.gpsUrl = gpxUrl;
        this.gpsDataVO = gpsDataVO;
        this.mainData = mainData;
        this.postTitle = postTitle;
    }

    public void shareToPost(CreatePostRequest request, String mainData) {
        this.postContent = request.postContent();
        this.difficulty = Difficulty.valueOf(request.difficulty());
        this.status = true;
        this.mainData = mainData;
    }

    public void update(UpdatePostRequest request, String mainData) {
        this.postContent = request.postContent();
        this.mainData = mainData;
    }

    public void addReportedCount() {
        this.reportCnt += 1;
    }

    public void resetReportedCount() {
        this.reportCnt = 0;
    }
}
