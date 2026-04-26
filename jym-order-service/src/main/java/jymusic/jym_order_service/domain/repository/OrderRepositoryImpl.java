package jymusic.jym_order_service.domain.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jymusic.jym_order_service.domain.entity.Order;
import jymusic.jym_order_service.domain.entity.OrderStatus;
import jymusic.jym_order_service.domain.entity.QOrder;
import jymusic.jym_order_service.domain.entity.QOrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Order> searchAdmin(AdminOrderSearchCriteria c, Pageable pageable) {
        QOrder order = QOrder.order;
        QOrderItem item = QOrderItem.orderItem;

        BooleanBuilder where = new BooleanBuilder()
                .and(memberIdIn(c.memberIds()))
                .and(statusEq(c.status()))
                .and(statusIn(c.statuses()))
                .and(createdBetween(c.startAt(), c.endAt()))
                .and(totalBetween(c.minAmount(), c.maxAmount()));

        JPAQuery<Order> contentQuery = queryFactory
                .selectFrom(order)
                .where(where);

        if (StringUtils.hasText(c.productTitle())) {
            String productTitle = c.productTitle().toLowerCase();
            contentQuery
                    .join(order.items, item)
                    .where(item.productTitle.lower().contains(productTitle))
                    .distinct();
        }

        List<Order> content = applySort(contentQuery, pageable, order)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(StringUtils.hasText(c.productTitle()) ? order.countDistinct() : order.count())
                .from(order)
                .where(where);

        if (StringUtils.hasText(c.productTitle())) {
            String productTitle = c.productTitle().toLowerCase();
            countQuery.join(order.items, item)
                    .where(item.productTitle.lower().contains(productTitle));
        }

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression memberIdIn(List<Long> ids) {
        return (ids == null || ids.isEmpty()) ? null : QOrder.order.memberId.in(ids);
    }

    private BooleanExpression statusEq(OrderStatus status) {
        return status == null ? null : QOrder.order.status.eq(status);
    }

    private BooleanExpression statusIn(List<OrderStatus> statuses) {
        return (statuses == null || statuses.isEmpty()) ? null : QOrder.order.status.in(statuses);
    }

    private BooleanExpression createdBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            return null;
        }

        DateTimePath<LocalDateTime> createdAt = QOrder.order.createdAt;
        if (from != null && to != null) {
            return createdAt.between(from, to);
        }
        if (from != null) {
            return createdAt.goe(from);
        }
        return createdAt.loe(to);
    }

    private BooleanExpression totalBetween(Long min, Long max) {
        if (min == null && max == null) {
            return null;
        }

        NumberPath<BigDecimal> total = QOrder.order.totalAmount;
        if (min != null && max != null) {
            return total.between(BigDecimal.valueOf(min), BigDecimal.valueOf(max));
        }
        if (min != null) {
            return total.goe(BigDecimal.valueOf(min));
        }
        return total.loe(BigDecimal.valueOf(max));
    }

    private JPAQuery<Order> applySort(JPAQuery<Order> query, Pageable pageable, QOrder order) {
        if (pageable.getSort().isUnsorted()) {
            return query.orderBy(order.createdAt.desc());
        }

        for (Sort.Order sort : pageable.getSort()) {
            com.querydsl.core.types.Order direction = sort.isAscending()
                    ? com.querydsl.core.types.Order.ASC
                    : com.querydsl.core.types.Order.DESC;

            switch (sort.getProperty()) {
                case "createdAt" -> query.orderBy(new OrderSpecifier<>(direction, order.createdAt));
                case "totalAmount" -> query.orderBy(new OrderSpecifier<>(direction, order.totalAmount));
                case "status" -> query.orderBy(new OrderSpecifier<>(direction, order.status));
                case "id" -> query.orderBy(new OrderSpecifier<>(direction, order.id));
                default -> {
                    // 화이트리스트에 없는 필드는 무시한다.
                }
            }
        }

        return query;
    }
}
