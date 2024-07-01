package com.runninghi.runninghibackv2.application.dto.challenge.response;

import com.runninghi.runninghibackv2.application.dto.memberchallenge.response.GetChallengeRankingResponse;
import com.runninghi.runninghibackv2.domain.entity.Challenge;
import com.runninghi.runninghibackv2.domain.enumtype.ChallengeCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record GetChallengeResponse(
        @Schema(description = "챌린지 Id", example = "1")
        Long challengeNo,
        @Schema(description = "챌린지명", example = "1개월 내로 100km 달리기")
        String title,
        @Schema(description = "챌린지 상세정보", example = "목표 거리만큼 달려보세요")
        String content,
        @Schema(description = "챌린지 타입", example = "DISTANCE")
        ChallengeCategory challengeCategory,
        @Schema(description = "챌린지 이미지", example = "test.jpg")
        String imageUrl,
        @Schema(description = "목표 수치", example = "100.0")
        String targetValue,
        @Schema(description = "챌린지 시작일자", example = "2024-06-01T00:00:00")
        LocalDateTime startDate,
        @Schema(description = "챌린지 종료일자", example = "2024-0.6-30T00:00:00")
        LocalDateTime endDate,
        @Schema(description = "챌린지 참여자 수", example = "4132")
        int participantsCount,
        @Schema(description = "챌린지 랭킹", example = "10")
        List<GetChallengeRankingResponse> ranking
) {
    public static GetChallengeResponse from(Challenge challenge, List<GetChallengeRankingResponse> ranking) {
        return new GetChallengeResponse(
                challenge.getChallengeNo(),
                challenge.getTitle(),
                challenge.getContent(),
                challenge.getChallengeCategory(),
                challenge.getImageUrl(),
                challenge.getTargetValue(),
                challenge.getStartDate(),
                challenge.getEndDate(),
                challenge.getParticipants().size(),
                ranking
        );
    }
}
