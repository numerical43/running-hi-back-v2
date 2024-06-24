package com.runninghi.runninghibackv2.domain.repository;

import com.runninghi.runninghibackv2.application.dto.memberchallenge.response.GetChallengeRankingResponse;
import com.runninghi.runninghibackv2.domain.entity.Challenge;
import com.runninghi.runninghibackv2.domain.entity.Member;
import com.runninghi.runninghibackv2.domain.entity.MemberChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberChallengeRepository extends JpaRepository<MemberChallenge, Long> {
    Optional<MemberChallenge> findByMemberAndChallenge(Member member, Challenge challenge);

    List<MemberChallenge> findByMember(Member member);

    @Query("SELECT m FROM MemberChallenge m WHERE m.member = :member AND m.challenge.status = :status")
    List<MemberChallenge> findByMemberAndChallengeStatus(@Param("member") Member member, @Param("status") boolean status);

    @Query("SELECT m.memberChallengeId AS memberChallengeId, m.record AS record, m.member.nickname AS nickname," +
            "m.member.profileUrl AS profileUrl, RANK() OVER (ORDER BY m.record DESC) AS rank " +
            "FROM MemberChallenge m WHERE m.challenge.challengeNo = :challengeNo")
    List<GetChallengeRankingResponse> findChallengeRanking(@Param("challengeNo") Long challengeNo);

    @Query("SELECT m.memberChallengeId AS memberChallengeId, m.record AS record, m.member.nickname AS nickname, " +
            "m.member.profileUrl AS profileUrl, " +
            "(SELECT COUNT(*) + 1 FROM MemberChallenge m2 WHERE m2.challenge.challengeNo = :challengeNo AND m2.record > m.record) AS rank " +
            "FROM MemberChallenge m WHERE m.challenge.challengeNo = :challengeNo AND m.member.memberNo = :memberNo")
    GetChallengeRankingResponse findMemberRanking(@Param("challengeNo") Long challengeNo, @Param("memberNo") Long memberNo);
}
