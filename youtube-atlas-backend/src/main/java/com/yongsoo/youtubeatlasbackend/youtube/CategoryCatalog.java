package com.yongsoo.youtubeatlasbackend.youtube;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

    private final Map<String, CategoryMetadata> metadataById = Map.ofEntries(
        Map.entry("1", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 영화, 애니메이션 등 대중적인 인기 영상을 모아봅니다.", 4)),
        Map.entry("2", new CategoryMetadata("automotive", "자동차", "자동차 리뷰, 시승기, 모빌리티 중심의 인기 영상을 모아봅니다.", 1)),
        Map.entry("10", new CategoryMetadata("music", "음악", "뮤직비디오, 라이브, 음원 관련 인기 영상을 확인할 수 있습니다.", 1)),
        Map.entry("15", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 18)),
        Map.entry("17", new CategoryMetadata("sports", "스포츠", "경기 하이라이트와 스포츠 이슈 중심의 인기 영상을 확인할 수 있습니다.", 1)),
        Map.entry("19", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 19)),
        Map.entry("20", new CategoryMetadata("gaming", "게임", "게임 방송, 리뷰, 신작 반응 등 게임 인기 영상을 확인할 수 있습니다.", 1)),
        Map.entry("22", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 20)),
        Map.entry("23", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 2)),
        Map.entry("24", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 1)),
        Map.entry("25", new CategoryMetadata("news", "뉴스/시사", "뉴스, 시사, 공공 이슈 중심의 인기 영상을 확인할 수 있습니다.", 1)),
        Map.entry("26", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 21)),
        Map.entry("28", new CategoryMetadata("technology", "테크", "과학 이슈, 기술 리뷰, IT 트렌드 중심의 인기 영상을 모아봅니다.", 1)),
        Map.entry("29", new CategoryMetadata("social", "사회/공익", "공익 활동과 사회적 메시지를 담은 인기 영상을 모아봅니다.", 1)),
        Map.entry("30", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 5)),
        Map.entry("31", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 6)),
        Map.entry("32", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 7)),
        Map.entry("33", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 8)),
        Map.entry("34", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 9)),
        Map.entry("35", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 10)),
        Map.entry("36", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 11)),
        Map.entry("37", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 12)),
        Map.entry("38", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 13)),
        Map.entry("39", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 14)),
        Map.entry("40", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 15)),
        Map.entry("41", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 16)),
        Map.entry("42", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 99)),
        Map.entry("43", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 3)),
        Map.entry("44", new CategoryMetadata("entertainment", "엔터테인먼트", "예능, 코미디, 라이프스타일, 여행, 영화 등 대중적인 인기 영상을 모아봅니다.", 17))
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

        CategoryMetadata metadata = metadataById.get(sourceId);
        String categoryId = metadata != null && metadata.groupId() != null ? metadata.groupId() : sourceId;
        String label = metadata != null ? metadata.label() : title;

        return new AtlasVideoCategory(
            categoryId,
            label,
            metadata != null ? metadata.description() : label + " 카테고리 인기 영상을 확인할 수 있습니다.",
            List.of(sourceId)
        );
    }

    public List<AtlasVideoCategory> mergeCategories(List<AtlasVideoCategory> categories) {
        Map<String, AtlasVideoCategory> merged = new LinkedHashMap<>();

        for (AtlasVideoCategory category : categories) {
            AtlasVideoCategory existing = merged.get(category.id());

            if (existing == null) {
                merged.put(category.id(), category);
                continue;
            }

            List<String> sourceIds = new ArrayList<>(existing.sourceIds());

            for (String sourceId : category.sourceIds()) {
                if (!sourceIds.contains(sourceId)) {
                    sourceIds.add(sourceId);
                }
            }

            sourceIds.sort(Comparator
                .comparingInt(this::priorityOf)
                .thenComparing(sourceId -> sourceId, Comparator.naturalOrder()));

            merged.put(category.id(), new AtlasVideoCategory(
                existing.id(),
                existing.label(),
                existing.description(),
                List.copyOf(sourceIds)
            ));
        }

        List<AtlasVideoCategory> mergedCategories = new ArrayList<>(merged.values());
        mergedCategories.sort(Comparator.comparing(AtlasVideoCategory::label, Comparator.naturalOrder()));
        return mergedCategories;
    }

    private int priorityOf(String sourceId) {
        CategoryMetadata metadata = metadataById.get(sourceId);
        return metadata != null ? metadata.priority() : Integer.MAX_VALUE;
    }

    private record CategoryMetadata(
        String groupId,
        String label,
        String description,
        int priority
    ) {
    }
}
