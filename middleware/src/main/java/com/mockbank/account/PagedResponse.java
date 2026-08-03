package com.mockbank.account;

import java.util.List;

/** Envelope for any paged list. */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
