package com.yuka.learning.common;

import com.yuka.learning.common.api.ErrorCode;
import com.yuka.learning.common.exception.BusinessException;

import java.util.function.Function;

/**
 * Central not-found / ownership check for user-scoped entities.
 * <p>
 * Every domain row carries a {@code userId} logical FK and services must never
 * return another user's data. Features keep their own error codes; this class
 * only owns the branching so the rule cannot drift between modules.
 */
public final class OwnershipGuard {

    private OwnershipGuard() {
    }

    /**
     * @param entity       the row loaded by primary key, possibly {@code null}
     * @param ownerId      extracts the owning user id from the entity
     * @param userId       the authenticated user
     * @param notFound     thrown when the row does not exist
     * @param accessDenied thrown when the row belongs to another user
     * @return the entity, guaranteed non-null and owned by {@code userId}
     */
    public static <T> T require(T entity, Function<T, Long> ownerId, Long userId,
                                ErrorCode notFound, ErrorCode accessDenied) {
        if (entity == null) {
            throw new BusinessException(notFound);
        }
        if (!userId.equals(ownerId.apply(entity))) {
            throw new BusinessException(accessDenied);
        }
        return entity;
    }
}
