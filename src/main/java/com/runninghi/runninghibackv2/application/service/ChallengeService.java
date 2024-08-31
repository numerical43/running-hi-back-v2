package com.runninghi.runninghibackv2.application.service;

import com.runninghi.runninghibackv2.application.dto.challenge.request.CreateChallengeRequest;
import com.runninghi.runninghibackv2.application.dto.challenge.request.UpdateChallengeRequest;
import com.runninghi.runninghibackv2.application.dto.challenge.response.*;
import com.runninghi.runninghibackv2.application.dto.memberchallenge.response.ChallengeRankResponse;
import com.runninghi.runninghibackv2.application.dto.memberchallenge.response.GetChallengeRankingResponse;
import com.runninghi.runninghibackv2.domain.entity.Challenge;
import com.runninghi.runninghibackv2.domain.enumtype.ChallengeStatus;
import com.runninghi.runninghibackv2.domain.repository.ChallengeQueryRepository;
import com.runninghi.runninghibackv2.domain.repository.ChallengeRepository;
import com.runninghi.runninghibackv2.domain.repository.MemberChallengeRepository;
import com.runninghi.runninghibackv2.domain.repository.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final MemberChallengeRepository memberChallengeRepository;
    private final MemberRepository memberRepository;
    private final ChallengeQueryRepository challengeQueryRepository;

    @Transactional
    public CreateChallengeResponse createChallenge(CreateChallengeRequest request) {

        Challenge challenge = Challenge.builder()
                .title(request.title())
                .content(request.content())
                .challengeCategory(request.challengeCategory())
                .imageUrl(request.imageUrl())
                .goal(request.goal())
                .goalDetail(request.goalDetail())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(ChallengeStatus.SCHEDULED)
                .build();

        challengeRepository.save(challenge);

        return CreateChallengeResponse.from(challenge);
    }

    @Transactional(readOnly = true)
    public GetAllChallengeResponse getAllChallengesByStatus(ChallengeStatus status, Long memberNo) {

        List<ChallengeListResponse> challengeList =
                challengeQueryRepository.findChallengesByStatusAndMember(status, memberNo).stream()
                .map(ChallengeListResponse::from)
                .toList();
        int challengeCount = challengeList.size();

        return new GetAllChallengeResponse(challengeList, challengeCount);
    }

    @Transactional(readOnly = true)
    public GetChallengeResponse getChallengeById(Long challengeNo) {

        Challenge challenge = challengeRepository.findById(challengeNo)
                .orElseThrow(EntityNotFoundException::new);

        List<ChallengeRankResponse> challengeRanking =
                challengeQueryRepository.findTop100ByChallengeNo(challenge.getChallengeNo());

        return GetChallengeResponse.from(challenge, challengeRanking);
    }

    @Transactional
    public UpdateChallengeResponse updateChallenge(Long challengeNo, UpdateChallengeRequest request) {

        Challenge challenge = challengeRepository.findById(challengeNo)
                .orElseThrow(EntityNotFoundException::new);

        challenge.update(request);

        return UpdateChallengeResponse.from(challenge);
    }

    @Transactional
    public DeleteChallengeResponse deleteChallenge(Long challengeNo) {

        challengeRepository.deleteById(challengeNo);

        return DeleteChallengeResponse.from(challengeNo);
    }
}
