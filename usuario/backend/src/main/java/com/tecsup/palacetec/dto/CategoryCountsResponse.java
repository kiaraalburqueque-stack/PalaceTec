package com.tecsup.palacetec.dto;

import java.util.List;

public record CategoryCountsResponse(long total, List<Category> categories, long favorites) {

    public record Category(String slug, String label, long count) {
    }
}
