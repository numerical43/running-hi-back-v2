package com.runninghi.runninghibackv2.infrastructure.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.runninghi.runninghibackv2.application.dto.reply.GetReplyList;
import com.runninghi.runninghibackv2.application.dto.reply.request.GetReplyListByMemberRequest;
import com.runninghi.runninghibackv2.application.dto.reply.request.GetReplyListRequest;
import com.runninghi.runninghibackv2.application.dto.reply.request.GetReportedReplyRequest;
import com.runninghi.runninghibackv2.application.dto.reply.response.GetReportedReplyResponse;
import com.runninghi.runninghibackv2.common.response.PageResultData;
import com.runninghi.runninghibackv2.domain.enumtype.ProcessingStatus;
import com.runninghi.runninghibackv2.domain.repository.ReplyQueryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.runninghi.runninghibackv2.domain.entity.QMember.member;
import static com.runninghi.runninghibackv2.domain.entity.QReply.reply;
import static com.runninghi.runninghibackv2.domain.entity.QReplyReport.replyReport;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ReplyQueryRepositoryImpl implements ReplyQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private static final int REPORTED_COUNT = 1;

    @Override
    public List<GetReplyList> findAllByPostNo(GetReplyListRequest request) {
        return jpaQueryFactory
                .select(Projections.constructor(GetReplyList.class,
                        reply.replyNo,
                        reply.member.memberNo,
                        reply.member.nickname,
                        reply.member.profileImageUrl,
                        reply.post.postNo,
                        reply.replyContent,
                        reply.reportedCount,
                        reply.isDeleted,
                        reply.createDate,
                        reply.updateDate))
                .from(reply)
                .where(reply.post.postNo.eq(request.getPostNo()),
                        reply.isDeleted.eq(false))
                .orderBy(
                        getOrderSpecifierList(request.getSort())
                                .toArray(OrderSpecifier[]::new))
                .fetch();
    }

    @Override
    public List<GetReplyList> findAllByMemberNo(Long memberNo, Sort sort) {
        return jpaQueryFactory
                .select(Projections.constructor(GetReplyList.class,
                        reply.replyNo,
                        reply.member.memberNo,
                        reply.member.nickname,
                        reply.post.postNo,
                        reply.replyContent,
                        reply.reportedCount,
                        reply.isDeleted,
                        reply.createDate,
                        reply.updateDate
                        ))
                .from(reply)
                .where(reply.member.memberNo.eq(memberNo),
                        reply.isDeleted.eq(false))
                .orderBy(
                getOrderSpecifierList(sort)
                        .toArray(OrderSpecifier[]::new))
                .fetch();
    }

    @Override
    public PageResultData<GetReportedReplyResponse> findAllReportedByPageableAndSearch(GetReportedReplyRequest request) {

        Long count = getReportedCount(request);
        if (count < 1) return null;
        if (request.pageable().getPageNumber() > 1) checkReplyCountWithPaging(count, request.pageable().getPageNumber(), request.pageable().getPageSize());
        List<GetReportedReplyResponse> content = getReportedReplyList(request);

        return new PageResultData<>(content, request.pageable(), count);
    }


    private void checkReplyCountWithPaging(Long count, int page, int size) {
        if (page > Math.ceil((double)count / size)) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public PageResultData<GetReplyList> findAllByMemberNoWithPaging(GetReplyListByMemberRequest request) {

        Long count = getCountByMemberNo(request);
        if (count < 1) throw new EntityNotFoundException();
        if (request.getPage() > 1) checkReplyCountWithPaging(count, request.getPage(), request.getSize());
        List<GetReplyList> content = getReplyListByMemberNo(request);

        return new PageResultData<>(content, request.getPageable(), count);
    }

    private Long getCountByMemberNo(GetReplyListByMemberRequest request) {
        return jpaQueryFactory
                .select(reply.replyNo.count())
                .from(reply)
                .where(
                        reply.member.memberNo.eq(request.getMemberNo()),
                        reply.isDeleted.eq(false))
                .fetchOne();
    }

    private Long getCountByPostNo(GetReplyListRequest request) {

        return jpaQueryFactory
                .select(reply.replyNo.count())
                .from(reply)
                .where(
                        reply.post.postNo.eq(request.getPostNo()),
                        reply.isDeleted.eq(false))
                .fetchOne();

    }

    private Long getReportedCount(GetReportedReplyRequest request) {
        return jpaQueryFactory
                .select(reply.replyNo.count())
                .from(reply)
                .join(reply.member, member)
                .join(reply.reportList, replyReport)
                .where(
                        likeNickname(request.search()),
                        eqReportStatus(request.reportStatus()),
                        reply.reportedCount.goe(REPORTED_COUNT))
                .fetchOne();

    }

    private List<GetReportedReplyResponse> getReportedReplyList (GetReportedReplyRequest request) {
        // request.reportStatus로 정렬
        return jpaQueryFactory
                .select(Projections.constructor(GetReportedReplyResponse.class,
                        reply.replyNo,
                        member.nickname,
                        reply.post.postNo,
                        reply.replyContent,
                        reply.reportedCount,
                        reply.isDeleted,
                        reply.createDate,
                        reply.updateDate
                ))
                .from(reply)
                .join(reply.member, member)
                .join(reply.reportList, replyReport)
                .where(likeNickname(request.search()),
                        eqReportStatus(request.reportStatus()),
                        reply.reportedCount.goe(REPORTED_COUNT))
                .orderBy(
                        getOrderSpecifierList(request.pageable().getSort())
                                .toArray(OrderSpecifier[]::new))
                .offset(request.pageable().getOffset())
                .limit( request.pageable().getPageSize())
                .fetch();

    }

    private List<GetReplyList> getReplyListByMemberNo(GetReplyListByMemberRequest request) {
        return jpaQueryFactory
                .select(Projections.constructor(GetReplyList.class,
                        reply.replyNo,
                        member.memberNo,
                        member.nickname,
                        reply.post.postNo,
                        reply.replyContent,
                        reply.reportedCount,
                        reply.isDeleted,
                        reply.createDate,
                        reply.updateDate))
                .from(reply)
                .join(reply.member, member)
                .where(
                        reply.member.memberNo.eq(request.getMemberNo()),
                        reply.isDeleted.eq(false))
                .orderBy(reply.replyNo.desc())
                .offset(request.getPageable().getOffset())
                .limit(request.getPageable().getPageSize())
                .fetch();
    }

    private BooleanExpression eqReplyNo(Long replyNo) {
        if (replyNo == null) return null;
        return reply.replyNo.loe(replyNo);
    }


    private BooleanExpression likeNickname (String search) {
        if (!StringUtils.hasText(search)) return null;   // space bar까지 막아줌
        return member.nickname.like("%" + search + "%");
    }

    private BooleanExpression eqReportStatus (ProcessingStatus reportStatus) {

        if (reportStatus == null || !StringUtils.hasText(reportStatus.name())) return  null;

        return replyReport.status.eq(reportStatus);
    }


    private List<OrderSpecifier<?>> getOrderSpecifierList (Sort sort) {

        List<OrderSpecifier<?>> orderList = new ArrayList<>();
        sort.stream().forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            String property = order.getProperty();
            Path<Object> target = Expressions.path(Object.class, reply,  property);
            orderList.add(new OrderSpecifier(direction, target));
        });

        return orderList;
    }

}