package com.yongsoo.youtubeatlasbackend.youtube;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoCategory;

class CategoryCatalogTest {

    private final CategoryCatalog categoryCatalog = new CategoryCatalog();

    @Test
    void mergeCategoriesUsesMetadataPriorityWithinGroup() {
        List<AtlasVideoCategory> merged = categoryCatalog.mergeCategories(List.of(
            categoryCatalog.toCategory("1", "Film & Animation", true),
            categoryCatalog.toCategory("24", "Entertainment", true),
            categoryCatalog.toCategory("23", "Comedy", true)
        ));

        AtlasVideoCategory entertainment = merged.stream()
            .filter(category -> category.id().equals("entertainment"))
            .findFirst()
            .orElseThrow();

        assertThat(entertainment.sourceIds()).containsExactly("24", "23", "1");
    }

    @Test
    void toCategorySkipsExcludedCategories() {
        assertThat(categoryCatalog.toCategory("27", "Education", true)).isNull();
    }
}
