package com.tecsup.palacetec.dto;

import java.util.Locale;

/** Filtros category y search del catálogo, con las reglas y los mensajes de CatalogQuery de Django. */
public record CatalogFilter(String category, String search) {

    private static final int MAX_CATEGORY = 20;
    private static final int MAX_SEARCH = 80;

    /** Los textos ausentes quedan vacíos: no filtran. */
    public static CatalogFilter read(RequestFields query) {
        String category = query.text("category", TextRule.optional().allowBlank().max(MAX_CATEGORY));
        String slug = category == null ? "" : RequestFields.strip(category).toLowerCase(Locale.ROOT);
        if (!slug.isEmpty() && GameCategory.ofSlug(slug) == null) {
            query.error("category", "Categoría no válida. Usa ruleta, cartas, deportes u otros.");
        }
        String search = query.text("search", TextRule.optional().allowBlank().max(MAX_SEARCH));
        query.throwIfInvalid();
        return new CatalogFilter(slug, search == null ? "" : search);
    }
}
