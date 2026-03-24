package com.yongsoo.youtubeatlasbackend.youtube;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoCategory;

@Component
public class CategoryCatalog {

    public static final String ALL_VIDEO_CATEGORY_ID = "0";
    private static final Set<String> EXCLUDED_CATEGORY_IDS = Set.of("27", "42");

    private static final AtlasVideoCategory ALL_VIDEO_CATEGORY = new AtlasVideoCategory(
        ALL_VIDEO_CATEGORY_ID,
        "전체",
        "카테고리 구분 없이 현재 국가 전체 인기 영상을 보여줍니다.",
        List.of()
    );

    private final Map<String, String> descriptionById = Map.ofEntries(
        Map.entry("2", "자동차 리뷰, 시승기, 모빌리티 중심의 인기 영상을 모아봅니다."),
        Map.entry("10", "뮤직비디오, 라이브, 음원 관련 인기 영상을 확인할 수 있습니다."),
        Map.entry("17", "경기 하이라이트와 스포츠 이슈 중심의 인기 영상을 확인할 수 있습니다."),
        Map.entry("20", "게임 방송, 리뷰, 신작 반응 등 게임 인기 영상을 확인할 수 있습니다."),
        Map.entry("25", "뉴스, 시사, 공공 이슈 중심의 인기 영상을 확인할 수 있습니다."),
        Map.entry("28", "과학 이슈, 기술 리뷰, IT 트렌드 중심의 인기 영상을 모아봅니다."),
        Map.entry("29", "공익 활동과 사회적 메시지를 담은 인기 영상을 모아봅니다.")
    );

    public AtlasVideoCategory allCategory() {
        return ALL_VIDEO_CATEGORY;
    }

    public boolean isExcluded(String categoryId) {
        return EXCLUDED_CATEGORY_IDS.contains(categoryId);
    }

    public AtlasVideoCategory toCategory(String sourceId, String title, boolean assignable) {
        if (!assignable || isExcluded(sourceId)) {
            return null;
        }

        return new AtlasVideoCategory(
            sourceId,
            title,
            descriptionById.getOrDefault(sourceId, title + " 카테고리 인기 영상을 확인할 수 있습니다."),
            List.of(sourceId)
        );
    }
}
