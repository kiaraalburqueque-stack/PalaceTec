package com.tecsup.palacetec.dto;

import java.util.List;

/** Paginación de Django REST Framework. */
public record PageResponse<T>(long count, String next, String previous, List<T> results) {
}
