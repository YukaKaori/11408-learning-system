package com.yuka.learning.common.api;

import java.util.List;

/**
 * One page of a longer list. {@code page} is 1-based, matching how the
 * frontend's pagination control counts.
 *
 * @param total the size of the whole filtered list, so the client can size its pager
 */
public record PageResponse<T>(List<T> items, long total, int page, int size) {
}
