package com.collab.service;

import com.collab.common.dto.PageResponse;
import com.collab.common.enums.ContentCategory;
import com.collab.common.enums.UserRole;
import com.collab.dto.ProfileDto;
import com.collab.repository.BloggerSearchSpec;
import org.springframework.data.domain.Sort;
import com.collab.entity.*;
import com.collab.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final BloggerProfileRepository bloggerProfileRepository;
    private final BrandProfileRepository brandProfileRepository;
    private final UserRepository userRepository;
    private final SocialStatsService socialStatsService;
    private final PortfolioItemRepository portfolioItemRepository;
    private final MinioService minioService;

    private static final int MAX_PORTFOLIO_ITEMS = 15;

    @Transactional
    public ProfileDto.BloggerResponse updateBlogger(ProfileDto.UpdateBloggerRequest req, User user) {
        BloggerProfile profile = bloggerProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Blogger profile not found"));

        profile.setBio(req.getBio());
        profile.setCategories(req.getCategories());
        profile.setMinPrice(req.getMinPrice());
        profile.setWorksWithBarter(req.isWorksWithBarter());

        if (req.getSocialAccounts() != null) {
            profile.getSocialAccounts().clear();
            req.getSocialAccounts().forEach(s -> profile.getSocialAccounts().add(
                    SocialAccount.builder()
                            .bloggerProfile(profile)
                            .platform(s.getPlatform())
                            .username(s.getUsername())
                            .url(s.getUrl())
                            .followersCount(s.getFollowersCount() != null ? s.getFollowersCount() : 0L)
                            .build()));
        }

        BloggerProfile saved = bloggerProfileRepository.save(profile);
        saved.getSocialAccounts().forEach(socialStatsService::syncAccount);
        return toBloggerResponse(saved, 0);
    }

    @Transactional
    public ProfileDto.BrandResponse updateBrand(ProfileDto.UpdateBrandRequest req, User user) {
        BrandProfile profile = brandProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Brand profile not found"));

        profile.setCompanyName(req.getCompanyName());
        profile.setDescription(req.getDescription());
        profile.setWebsiteUrl(req.getWebsiteUrl());
        profile.setCategory(req.getCategory());

        if (req.getSocialAccounts() != null) {
            profile.getSocialAccounts().clear();
            req.getSocialAccounts().forEach(s -> profile.getSocialAccounts().add(
                    SocialAccount.builder()
                            .brandProfile(profile)
                            .platform(s.getPlatform())
                            .username(s.getUsername())
                            .url(s.getUrl())
                            .followersCount(s.getFollowersCount() != null ? s.getFollowersCount() : 0L)
                            .build()));
        }

        BrandProfile saved = brandProfileRepository.save(profile);
        saved.getSocialAccounts().forEach(socialStatsService::syncAccount);
        return toBrandResponse(saved);
    }

    // ─── Roles ───────────────────────────────────────────────────────────────

    @Transactional
    public ProfileDto.RolesResponse addRole(UserRole newRole, User user) {
        if (user.getCurrentRole() == UserRole.BRAND)
            throw new IllegalArgumentException("Бренды не могут добавлять роли блогера");
        if (newRole == UserRole.BRAND || newRole == UserRole.ADMIN)
            throw new IllegalArgumentException("Нельзя добавить роль: " + newRole);
        if (user.getRoles().contains(newRole))
            throw new IllegalArgumentException("Роль " + newRole + " уже добавлена");

        user.getRoles().add(newRole);
        userRepository.save(user);

        return buildRolesResponse(user);
    }

    @Transactional
    public ProfileDto.RolesResponse switchRole(UserRole role, User user) {
        if (!user.getRoles().contains(role))
            throw new IllegalArgumentException("У вас нет роли " + role + ". Сначала добавьте её.");
        if (user.getCurrentRole() == role)
            throw new IllegalArgumentException("Роль " + role + " уже активна");

        user.setCurrentRole(role);
        userRepository.save(user);

        return buildRolesResponse(user);
    }

    private ProfileDto.RolesResponse buildRolesResponse(User user) {
        ProfileDto.RolesResponse r = new ProfileDto.RolesResponse();
        r.setCurrentRole(user.getCurrentRole().name());
        r.setAllRoles(user.getRoles().stream()
                .map(UserRole::name)
                .collect(java.util.stream.Collectors.toSet()));
        return r;
    }

    // ─── Portfolio ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProfileDto.PortfolioItemResponse> getPortfolio(UUID userId) {
        return portfolioItemRepository
                .findByBloggerProfileUserIdOrderBySortOrderAsc(userId)
                .stream().map(this::toPortfolioResponse).toList();
    }

    @Transactional
    public ProfileDto.PortfolioItemResponse addPortfolioItem(ProfileDto.PortfolioItemRequest req, User user) {
        BloggerProfile profile = bloggerProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Blogger profile not found"));

        int count = portfolioItemRepository.countByBloggerProfileUserId(user.getId());
        if (count >= MAX_PORTFOLIO_ITEMS)
            throw new IllegalArgumentException("Portfolio limit reached: max " + MAX_PORTFOLIO_ITEMS + " items");

        PortfolioItem item = PortfolioItem.builder()
                .bloggerProfile(profile)
                .title(req.getTitle())
                .mediaUrl(req.getMediaUrl())
                .contentType(req.getContentType())
                .thumbnailUrl(req.getThumbnailUrl())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : count)
                .build();

        return toPortfolioResponse(portfolioItemRepository.save(item));
    }

    @Transactional
    public void deletePortfolioItem(UUID itemId, User user) {
        PortfolioItem item = portfolioItemRepository
                .findByIdAndBloggerProfileUserId(itemId, user.getId())
                .orElseThrow(() -> new RuntimeException("Portfolio item not found"));
        portfolioItemRepository.delete(item);
    }

    @Transactional
    public List<ProfileDto.PortfolioItemResponse> reorderPortfolio(ProfileDto.ReorderRequest req, User user) {
        AtomicInteger order = new AtomicInteger(0);
        req.getOrderedIds().forEach(id -> {
            portfolioItemRepository.findByIdAndBloggerProfileUserId(id, user.getId())
                    .ifPresent(item -> {
                        item.setSortOrder(order.getAndIncrement());
                        portfolioItemRepository.save(item);
                    });
        });
        return getPortfolio(user.getId());
    }

    private ProfileDto.PortfolioItemResponse toPortfolioResponse(PortfolioItem item) {
        ProfileDto.PortfolioItemResponse r = new ProfileDto.PortfolioItemResponse();
        r.setId(item.getId());
        r.setMediaUrl(minioService.resolveUrl(item.getMediaUrl()));
        r.setTitle(item.getTitle());
        r.setContentType(item.getContentType());
        r.setThumbnailUrl(minioService.resolveUrl(item.getThumbnailUrl()));
        r.setSortOrder(item.getSortOrder());
        return r;
    }

    // ─── Bloggers / Brands ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<ProfileDto.BloggerResponse> getBloggers(
            String country, String city, Integer minAge, Integer maxAge,
            Double minRating, Boolean worksWithBarter, java.math.BigDecimal maxPrice,
            ContentCategory category, UserRole role, String search,
            Boolean verifiedOnly, String sortBy, int page, int size) {

        var filter = new BloggerSearchSpec.BloggerFilter(
                country, city, minAge, maxAge, minRating,
                worksWithBarter, maxPrice, category, role, search, verifiedOnly);

        Sort sort = switch (sortBy != null ? sortBy : "rating") {
            case "price_asc"   -> Sort.by("minPrice").ascending();
            case "price_desc"  -> Sort.by("minPrice").descending();
            case "tasks"       -> Sort.by("completedTasksCount").descending();
            case "reviews"     -> Sort.by("reviewsCount").descending();
            default            -> Sort.by("rating").descending();
        };

        var pageable = PageRequest.of(page, size, sort);
        var pg = bloggerProfileRepository.findAll(BloggerSearchSpec.build(filter), pageable);
        int[] rank = {page * size + 1};
        return PageResponse.from(pg.map(b -> toBloggerResponse(b, rank[0]++)));
    }

    @Transactional(readOnly = true)
    public ProfileDto.BloggerResponse getBloggerById(UUID userId) {
        BloggerProfile profile = bloggerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Blogger not found"));
        return toBloggerResponse(profile, 0);
    }

    @Transactional(readOnly = true)
    public ProfileDto.BrandResponse getBrandById(UUID userId) {
        BrandProfile profile = brandProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Brand not found"));
        return toBrandResponse(profile);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileDto.BrandResponse> getBrands(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("rating").descending());
        return PageResponse.from(brandProfileRepository.findAll(pageable).map(this::toBrandResponse));
    }

    private ProfileDto.BloggerResponse toBloggerResponse(BloggerProfile p, int rank) {
        ProfileDto.BloggerResponse r = new ProfileDto.BloggerResponse();
        r.setId(p.getUser().getId());
        r.setFullName(p.getUser().getFullName());
        r.setAvatarUrl(minioService.resolveUrl(p.getUser().getAvatarUrl()));
        r.setCity(p.getUser().getCity());
        r.setCountry(p.getUser().getCountry());
        r.setAge(p.getUser().getAge());
        r.setVerified(p.getUser().isVerified());
        r.setBio(p.getBio());
        r.setCategories(p.getCategories());
        r.setMinPrice(p.getMinPrice());
        r.setWorksWithBarter(p.isWorksWithBarter());
        r.setRating(p.getRating());
        r.setCompletedTasksCount(p.getCompletedTasksCount());
        r.setReviewsCount(p.getReviewsCount());
        r.setSocialAccounts(toSocialInfoList(p.getSocialAccounts()));
        r.setPortfolioItems(p.getPortfolioItems().stream()
                .sorted(java.util.Comparator.comparingInt(PortfolioItem::getSortOrder))
                .map(this::toPortfolioResponse).toList());
        r.setRank(rank);
        return r;
    }

    private ProfileDto.BrandResponse toBrandResponse(BrandProfile p) {
        ProfileDto.BrandResponse r = new ProfileDto.BrandResponse();
        r.setId(p.getUser().getId());
        r.setFullName(p.getUser().getFullName());
        r.setCompanyName(p.getCompanyName());
        r.setAvatarUrl(minioService.resolveUrl(p.getUser().getAvatarUrl()));
        r.setCity(p.getUser().getCity());
        r.setVerified(p.getUser().isVerified());
        r.setDescription(p.getDescription());
        r.setWebsiteUrl(p.getWebsiteUrl());
        r.setCategory(p.getCategory());
        r.setRating(p.getRating());
        r.setTasksCount(p.getTasksCount());
        r.setReviewsCount(p.getReviewsCount());
        r.setSocialAccounts(toSocialInfoList(p.getSocialAccounts()));
        return r;
    }

    private List<ProfileDto.SocialInfo> toSocialInfoList(List<SocialAccount> accounts) {
        return accounts.stream().map(s -> {
            ProfileDto.SocialInfo info = new ProfileDto.SocialInfo();
            info.setPlatform(s.getPlatform());
            info.setUsername(s.getUsername());
            info.setUrl(s.getUrl());
            info.setFollowersCount(s.getFollowersCount());
            return info;
        }).toList();
    }
}
