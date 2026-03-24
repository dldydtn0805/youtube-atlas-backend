package com.yongsoo.youtubeatlasbackend.youtube;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CategoryCatalogTest {

    private final CategoryCatalog categoryCatalog = new CategoryCatalog();

    @Test
    void toCategoryKeepsOriginalSourceCategoryId() {
        var category = categoryCatalog.toCategory("24", "Entertainment", true);

        assertThat(category).isNotNull();
        assertThat(category.id()).isEqualTo("24");
        assertThat(category.label()).isEqualTo("Entertainment");
        assertThat(category.sourceIds()).containsExactly("24");
    }

    @Test
    void toCategorySkipsExcludedCategories() {
        assertThat(categoryCatalog.toCategory("27", "Education", true)).isNull();
    }
}
