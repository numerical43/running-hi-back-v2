package com.runninghi.runninghibackv2.domain.entity.vo;

import com.runninghi.runninghibackv2.common.converter.IntegerListConverter;
import com.runninghi.runninghibackv2.domain.enumtype.Difficulty;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.locationtech.jts.geom.Point;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class GpsDataVO implements Serializable {

    @Column
    @Comment("지역명")
    private String locationName;

    @Column(columnDefinition = "POINT SRID 4326")
    private Point startPoint;

    @Column
    @Comment("러닝 시작 시각")
    private LocalDateTime runStartTime;

    @Column
    @Comment("뛴 거리")
    private float distance;

    @Column
    @Comment("뛴 시간(초)")
    private int time;

    @Column
    @Comment("소모 칼로리")
    private int kcal;

    @Column
    @Comment("평균 페이스 (초)")
    private int meanPace;

    @Column
    @Comment("구간별 페이스 (초)")
    @Convert(converter = IntegerListConverter.class)
    List<Integer> sectionPace;

    @Column
    @Comment("구간별 소모 칼로리")
    @Convert(converter = IntegerListConverter.class)
    List<Integer> sectionKcal;

    @Column
    @Comment("러닝코스 난이도")
    private Difficulty difficulty;

    @Builder
    public GpsDataVO(String locationName, Point startPoint, LocalDateTime runStartTime, float distance, int time, int kcal,
                     int meanPace, List<Integer> sectionPace, List<Integer> sectionKcal, Difficulty difficulty) {
        this.locationName = locationName;
        this.startPoint = startPoint;
        this.runStartTime = runStartTime;
        this.distance = distance;
        this.time = time;
        this.kcal = kcal;
        this.meanPace = meanPace;
        this.sectionPace = sectionPace;
        this.sectionKcal = sectionKcal;
        this.difficulty = difficulty;
    }

}
