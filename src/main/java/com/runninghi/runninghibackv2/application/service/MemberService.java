package com.runninghi.runninghibackv2.application.service;

import com.runninghi.runninghibackv2.application.dto.member.request.AdminSignInRequest;
import com.runninghi.runninghibackv2.application.dto.member.request.AdminSignUpRequest;
import com.runninghi.runninghibackv2.application.dto.member.request.UpdateCurrentLocationRequest;
import com.runninghi.runninghibackv2.application.dto.member.request.UpdateMemberInfoRequest;
import com.runninghi.runninghibackv2.application.dto.member.response.*;
import com.runninghi.runninghibackv2.auth.jwt.JwtTokenProvider;
import com.runninghi.runninghibackv2.common.dto.AccessTokenInfo;
import com.runninghi.runninghibackv2.common.dto.RefreshTokenInfo;
import com.runninghi.runninghibackv2.common.exception.custom.*;
import com.runninghi.runninghibackv2.common.utils.PasswordUtils;
import com.runninghi.runninghibackv2.domain.entity.Member;
import com.runninghi.runninghibackv2.domain.enumtype.Role;
import com.runninghi.runninghibackv2.domain.repository.MemberRepository;
import com.runninghi.runninghibackv2.domain.service.MemberChecker;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberChecker memberChecker;
    private final ImageService imageService;

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${admin.invitation-code}")
    private String adminInvitationCode;

    @Value("${cloud.aws.s3.default-profile}")
    private String defaultProfileImageUrl;


    @Transactional
    public UpdateMemberInfoResponse updateMemberInfo(Long memberNo, UpdateMemberInfoRequest request) throws BadRequestException {
        log.info("회원 정보 업데이트 요청. 회원 번호: {}, 요청 정보: {}", memberNo, request);
        Member member = findMemberByNo(memberNo);

        try {
            memberChecker.checkNicknameValidation(request.nickname());
        } catch (IllegalArgumentException e) {
            log.error("닉네임 검증 실패: {}", e.getMessage());
            throw new MemberInvalidDataException();
        }

        member.updateNickname(request.nickname());
        Member updatedMember = memberRepository.save(member);

        log.info("회원 정보 업데이트 성공. 새로운 닉네임: {}", updatedMember.getNickname());
        return UpdateMemberInfoResponse.from(updatedMember.getNickname());
    }

    @Transactional(readOnly = true)
    public GetMemberResponse getMemberInfo(Long memberNo) {
        log.info("회원 정보 조회 요청. 회원 번호: {}", memberNo);
        Member member = findMemberByNo(memberNo);

        log.info("회원 정보 조회 성공. 회원 번호: {}", memberNo);
        return GetMemberResponse.from(member);
    }

    public void addReportedCount(Long memberNo) {
        log.info("신고 수 추가 요청. 회원 번호: {}", memberNo);
        Member member = findMemberByNo(memberNo);

        member.addReportedCount();
        log.info("신고 수 추가 완료. 회원 번호: {}", memberNo);
    }

    public void saveFCMToken(Long memberNo, String fcmToken, boolean alarmConsent) {
        log.info("FCM 토큰 저장 요청. 회원 번호: {}, FCM 토큰: {}, 알림 동의: {}", memberNo, fcmToken, alarmConsent);
        Member member = findMemberByNo(memberNo);

        member.updateFCMToken(fcmToken);
        member.updateAlarmConsent(alarmConsent);

        log.info("FCM 토큰 저장 완료. 회원 번호: {}", memberNo);
    }

    @Transactional
    public UpdateCurrentLocationResponse updateCurrentLocation(Long memberNo, UpdateCurrentLocationRequest request) {
        log.info("위치 정보 업데이트 요청. 회원 번호: {}, 좌표: ({}, {})", memberNo, request.latitude(), request.longitude());
        Member member = findMemberByNo(memberNo);

        GeometryFactory gf = new GeometryFactory();
        Point geometry = gf.createPoint(new Coordinate(request.longitude(), request.latitude())); // longitude 경도 == x, latitude 위도 == y
        geometry.setSRID(4326);

        member.updateGeometry(geometry);
        memberRepository.save(member);

        log.info("위치 정보 업데이트 완료. 회원 번호: {}, 좌표: ({}, {})", memberNo, geometry.getX(), geometry.getY());

        return UpdateCurrentLocationResponse.from(memberNo, geometry);
    }

    @Transactional
    public Map<String, String> signinAdmin(AdminSignInRequest request) throws RuntimeException {
        log.info("관리자 로그인 시도: 사용자 account = {}", request.account());

        Member member = memberRepository.findByAccount(request.account())
                .orElseThrow(() -> {
                    log.warn("관리자 로그인 실패: 잘못된 관리자 account = {}", request.account());
                    return new EntityNotFoundException();
                });

        if (!PasswordUtils.checkPassword(request.password(), member.getPassword())) {
            log.warn("관리자 로그인 실패: 잘못된 비밀번호. 사용자 account = {}", request.account());
            throw new AdminInvalidPasswordException();
        }

        if (member.getRole() != Role.ADMIN) {
            log.warn("관리자 로그인 실패: 권한 부족. 사용자 account = {}", request.account());
            throw new AdminUnauthorizedException();
        }

        String accessToken = jwtTokenProvider.createAccessToken(new AccessTokenInfo(member.getMemberNo(), member.getRole()));
        String refreshToken = jwtTokenProvider.createRefreshToken(new RefreshTokenInfo(member.getMemberNo().toString(), member.getRole()));

        log.info("관리자 로그인 성공: 사용자 account = {}", request.account());
        log.debug("발급된 액세스 토큰: {}", accessToken);
        log.debug("발급된 리프레시 토큰: {}", refreshToken);

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        tokens.put("refreshToken", refreshToken);

        return tokens;
    }

    @Transactional
    public void signupAdmin(AdminSignUpRequest request) {
        if (memberRepository.findByAccount(request.account()).isPresent()) {
            log.error("이미 가입된 관리자 계정입니다. 관리자 계정: {}", request.account());
            throw new AdminAccountDuplicatedException();
        }

        Role role;
        if (request.invitationCode() != null && request.invitationCode().equals(adminInvitationCode)) {
            role = Role.ADMIN;
        } else {
            log.error("잘못된 초대 코드입니다.: {}", request.invitationCode());
            throw new AdminInvalidInvitationCodeException();
        }

        Member newMember = Member.builder()
                .account(request.account())
                .password(PasswordUtils.hashPassword(request.password()))
                .role(role)
                .isActive(true)
                .build();

        memberRepository.save(newMember);
    }

    @Transactional(readOnly = true)
    public GetIsTermsAgreedResponse getTermsAgreement(Long memberNo) {
        log.info("약관 동의 여부 조회 시도. 회원 번호: {}", memberNo);
        Member member = findMemberByNo(memberNo);

        log.info("약관 동의 여부 조회 완료. 회원 번호: {}", memberNo);

        return GetIsTermsAgreedResponse.of(member.isTermsAgreed());
    }

    @Transactional
    public TermsAgreementResponse acceptTermsAgreement(Long memberNo) {
        log.info("약관 동의 처리 시도. 회원 번호: {}", memberNo);
        Member member = findMemberByNo(memberNo);

        member.updateTermsAgreed(true);

        log.info("약관 동의 처리 완료. 회원 번호: {}", memberNo);

        return TermsAgreementResponse.of(member.isTermsAgreed());
    }

    @Transactional(readOnly = true)
    public GetIsLocationResponse getLocationSetting(Long memberNo) {
        log.info("지역 설정 여부 조회 시도. 회원 번호: {}", memberNo);
        Member member = findMemberByNo(memberNo);

        boolean isLocation = member.getGeometry() != null;

        log.info("지역 설정 여부 조회 처리 완료. 회원 번호: {}", memberNo);

        return GetIsLocationResponse.of(isLocation);

    }

    @Transactional
    public UpdateProfileImageResponse updateProfileImage(Long memberNo, MultipartFile profileImage) throws IOException {
        log.info("프로필 사진 수정 시도. 회원 번호: {}", memberNo);
        Member member = findMemberByNo(memberNo);

        String currentProfileImageUrl = member.getProfileImageUrl();
        if (memberChecker.isCustomProfileImage(currentProfileImageUrl, defaultProfileImageUrl)) {
            try {
                // cloud starage의 이미지 삭제 로직
                imageService.deleteImageFromStorage(currentProfileImageUrl);
                log.info("기존 프로필 이미지 삭제 완료: {}", currentProfileImageUrl);
            } catch (Exception e) {
                log.warn("기존 프로필 이미지 삭제 중 오류 발생: {}", currentProfileImageUrl, e);
                throw new ImageException();
            }
        }

        String newProfileImageUrl;
        try {
            newProfileImageUrl = imageService.uploadImage(profileImage, memberNo, "profile/");
        } catch (IOException e) {
            log.error("이미지 업로드 중 오류 발생: {}", e.getMessage());
            throw new ImageException("프로필 이미지 업로드 중 오류가 발생했습니다.");
        }

        member.updateProfileImageUrl(newProfileImageUrl);

        return UpdateProfileImageResponse.of(member);
    }

    @Transactional
    public DeleteProfileImageResponse deleteProfileImage(Long memberNo) {
        log.info("프로필 사진 삭제 시도. 회원 번호: {}", memberNo);
        Member member = findMemberByNo(memberNo);

        String currentProfileImageUrl = member.getProfileImageUrl();
        if (memberChecker.isCustomProfileImage(currentProfileImageUrl, defaultProfileImageUrl)) {
            try {
                // cloud starage의 이미지 삭제 로직
                imageService.deleteImageFromStorage(currentProfileImageUrl);
                log.info("기존 프로필 이미지 삭제 완료. URL: {}", currentProfileImageUrl);
            } catch (Exception e) {
                log.error("프로필 이미지 삭제 중 오류 발생. URL: {}", currentProfileImageUrl, e);
                throw new ImageException();
            }
        } else {
            log.info("삭제할 프로필 이미지가 없거나 이미 기본 이미지입니다. 현재 URL: {}", currentProfileImageUrl);
        }

        member.updateProfileImageUrl(defaultProfileImageUrl);

        return DeleteProfileImageResponse.of(member);
    }

    private Member findMemberByNo(Long memberNo) {
        return memberRepository.findById(memberNo)
                .orElseThrow(() -> {
                    log.error("해당 번호의 회원을 찾을 수 없음. 회원 번호: {}", memberNo);
                    return new EntityNotFoundException();
                });
    }

}
