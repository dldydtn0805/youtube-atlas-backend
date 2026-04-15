package com.yongsoo.youtubeatlasbackend.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminPositionUpdateRequest;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminUserPositionResponse;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.game.GamePosition;
import com.yongsoo.youtubeatlasbackend.game.GamePositionRepository;
import com.yongsoo.youtubeatlasbackend.game.GameWallet;
import com.yongsoo.youtubeatlasbackend.game.GameWalletRepository;
import com.yongsoo.youtubeatlasbackend.game.PositionStatus;
import com.yongsoo.youtubeatlasbackend.youtube.ResourceNotFoundException;

@Service
public class AdminPositionService {

    private static final int ORDER_QUANTITY_STEP = 100;

    private final AppUserRepository appUserRepository;
    private final GamePositionRepository gamePositionRepository;
    private final GameWalletRepository gameWalletRepository;
    private final Clock clock;

    public AdminPositionService(
        AppUserRepository appUserRepository,
        GamePositionRepository gamePositionRepository,
        GameWalletRepository gameWalletRepository,
        Clock clock
    ) {
        this.appUserRepository = appUserRepository;
        this.gamePositionRepository = gamePositionRepository;
        this.gameWalletRepository = gameWalletRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminUserPositionResponse> getOpenPositions(Long userId, Long seasonId) {
        requireUser(userId);
        return gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(
            seasonId,
            userId,
            PositionStatus.OPEN
        ).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public AdminUserPositionResponse updateOpenPosition(Long userId, Long positionId, AdminPositionUpdateRequest request) {
        requireUser(userId);
        validateRequest(request);

        GamePosition position = gamePositionRepository.findByIdAndUserIdForUpdate(positionId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("포지션을 찾을 수 없습니다."));
        if (position.getStatus() != PositionStatus.OPEN) {
            throw new IllegalArgumentException("보유 중인 OPEN 포지션만 수정할 수 있습니다.");
        }

        GameWallet wallet = gameWalletRepository.findBySeasonIdAndUserIdForUpdate(position.getSeason().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("지갑 정보를 찾을 수 없습니다."));

        long newStakePoints = request.stakePoints();
        int newQuantity = request.quantity();
        long previousStakePoints = position.getStakePoints();
        long stakeDelta = newStakePoints - previousStakePoints;

        long nextBalancePoints = wallet.getBalancePoints() - stakeDelta;
        long nextReservedPoints = wallet.getReservedPoints() + stakeDelta;

        if (nextBalancePoints < 0L) {
            throw new IllegalArgumentException("가용 포인트보다 큰 stakePoints로는 수정할 수 없습니다.");
        }

        if (nextReservedPoints < 0L) {
            throw new IllegalArgumentException("reservedPoints가 음수가 되도록 수정할 수 없습니다.");
        }

        position.setQuantity(newQuantity);
        position.setStakePoints(newStakePoints);
        gamePositionRepository.save(position);

        wallet.setBalancePoints(nextBalancePoints);
        wallet.setReservedPoints(nextReservedPoints);
        wallet.setUpdatedAt(Instant.now(clock));
        gameWalletRepository.save(wallet);

        return toResponse(position);
    }

    private void requireUser(Long userId) {
        if (!appUserRepository.existsById(userId)) {
            throw new ResourceNotFoundException("사용자를 찾을 수 없습니다.");
        }
    }

    private void validateRequest(AdminPositionUpdateRequest request) {
        if (request.quantity() % ORDER_QUANTITY_STEP != 0) {
            throw new IllegalArgumentException("quantity는 100 단위로만 수정할 수 있습니다.");
        }
    }

    private AdminUserPositionResponse toResponse(GamePosition position) {
        return new AdminUserPositionResponse(
            position.getId(),
            position.getSeason().getId(),
            position.getSeason().getName(),
            position.getRegionCode(),
            position.getCategoryId(),
            position.getVideoId(),
            position.getTitle(),
            position.getChannelTitle(),
            position.getThumbnailUrl(),
            position.getBuyRank(),
            position.getQuantity(),
            position.getStakePoints(),
            position.getStatus().name(),
            position.getBuyCapturedAt(),
            position.getCreatedAt(),
            position.getClosedAt()
        );
    }
}
