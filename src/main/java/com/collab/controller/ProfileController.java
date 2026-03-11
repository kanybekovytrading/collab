package com.collab.controller;

import com.collab.common.dto.*;
import com.collab.common.enums.ContentCategory;
import com.collab.common.enums.UserRole;
import com.collab.dto.ProfileDto;
import com.collab.entity.User;
import com.collab.security.CurrentUser;
import com.collab.service.ProfileService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    // ---- Bloggers ----

    @GetMapping("/bloggers")
    @Tag(name = "Bloggers", description = "Поиск и просмотр профилей блогеров")
    @Operation(
        summary = "Поиск блогеров с расширенными фильтрами",
        description = """
            Публичный эндпоинт. Поддерживает 11 параметров фильтрации:
            
            | Параметр | Тип | Описание |
            |----------|-----|----------|
            | `country` | string | Страна (напр. `KG`, `RU`) |
            | `city` | string | Город |
            | `minAge` / `maxAge` | int | Возраст блогера |
            | `minRating` | double | Минимальный рейтинг (0.0–5.0) |
            | `worksWithBarter` | boolean | Принимает бартер |
            | `maxPrice` | decimal | Максимальная цена за контент |
            | `category` | enum | BEAUTY, FOOD, TRAVEL, IT... |
            | `role` | enum | BLOGGER или AI_CREATOR |
            | `search` | string | Поиск по имени / bio |
            | `verifiedOnly` | boolean | Только верифицированные |
            | `sortBy` | string | `rating` \\| `price_asc` \\| `price_desc` \\| `tasks` \\| `reviews` |
            """
    )
    public ResponseEntity<ApiResponse1<PageResponse<ProfileDto.BloggerResponse>>> getBloggers(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer minAge,
            @RequestParam(required = false) Integer maxAge,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Boolean worksWithBarter,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) ContentCategory category,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean verifiedOnly,
            @Parameter(description = "rating | price_asc | price_desc | tasks | reviews")
            @RequestParam(defaultValue = "rating") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse1.ok(
                profileService.getBloggers(country, city, minAge, maxAge,
                        minRating, worksWithBarter, maxPrice, category, role,
                        search, verifiedOnly, sortBy, page, size)));
    }

    @GetMapping("/bloggers/{userId}")
    @Tag(name = "Bloggers")
    @Operation(summary = "Профиль блогера по ID", description = "Публичный эндпоинт.")
    public ResponseEntity<ApiResponse1<ProfileDto.BloggerResponse>> getBlogger(
            @Parameter(description = "UUID пользователя") @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse1.ok(profileService.getBloggerById(userId)));
    }

    @PutMapping("/profile/blogger")
    @PreAuthorize("hasAnyRole('BLOGGER','AI_CREATOR')")
    @SecurityRequirement(name = "Bearer")
    @Tag(name = "Bloggers")
    @Operation(
        summary = "Обновить профиль блогера",
        description = "Обновляет bio, категории, цену, соцсети. Список `socialAccounts` заменяется целиком."
    )
    public ResponseEntity<ApiResponse1<ProfileDto.BloggerResponse>> updateBlogger(
            @RequestBody ProfileDto.UpdateBloggerRequest req, @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(profileService.updateBlogger(req, user)));
    }

    // ---- Brands ----

    @GetMapping("/brands")
    @Tag(name = "Brands", description = "Просмотр профилей брендов")
    @Operation(summary = "Список брендов", description = "Публичный. Сортировка по рейтингу.")
    public ResponseEntity<ApiResponse1<PageResponse<ProfileDto.BrandResponse>>> getBrands(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse1.ok(profileService.getBrands(page, size)));
    }

    @GetMapping("/brands/{userId}")
    @Tag(name = "Brands")
    @Operation(summary = "Профиль бренда по ID", description = "Публичный эндпоинт.")
    public ResponseEntity<ApiResponse1<ProfileDto.BrandResponse>> getBrand(
            @Parameter(description = "UUID пользователя") @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse1.ok(profileService.getBrandById(userId)));
    }

    @PutMapping("/profile/brand")
    @PreAuthorize("hasRole('BRAND')")
    @SecurityRequirement(name = "Bearer")
    @Tag(name = "Brands")
    @Operation(
        summary = "Обновить профиль бренда",
        description = "Обновляет название компании, описание, сайт, категорию, соцсети."
    )
    public ResponseEntity<ApiResponse1<ProfileDto.BrandResponse>> updateBrand(
            @RequestBody ProfileDto.UpdateBrandRequest req, @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(profileService.updateBrand(req, user)));
    }

    // ─── Roles ───────────────────────────────────────────────────────────────

    @PostMapping("/profile/role")
    @PreAuthorize("hasAnyRole('BLOGGER','AI_CREATOR')")
    @SecurityRequirement(name = "Bearer")
    @Tag(name = "Profile")
    @Operation(
        summary = "Добавить роль",
        description = """
            Позволяет блогеру / AI-креатору добавить себе вторую роль.

            **Разрешено:**
            - `BLOGGER` → добавить `AI_CREATOR`
            - `AI_CREATOR` → добавить `BLOGGER`

            **Запрещено:** добавлять `BRAND` или `ADMIN`.

            После добавления роли переключитесь на неё через `PUT /profile/role/switch`.
            """
    )
    public ResponseEntity<ApiResponse1<ProfileDto.RolesResponse>> addRole(
            @Valid @RequestBody ProfileDto.AddRoleRequest req,
            @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(profileService.addRole(req.getRole(), user)));
    }

    @PutMapping("/profile/role/switch")
    @PreAuthorize("hasAnyRole('BLOGGER','AI_CREATOR')")
    @SecurityRequirement(name = "Bearer")
    @Tag(name = "Profile")
    @Operation(
        summary = "Переключить активную роль",
        description = """
            Меняет `currentRole` пользователя на одну из уже добавленных ролей.
            Роль должна быть предварительно добавлена через `POST /profile/role`.

            После переключения токен обновлять не нужно — роль читается из БД при каждом запросе.
            """
    )
    public ResponseEntity<ApiResponse1<ProfileDto.RolesResponse>> switchRole(
            @Valid @RequestBody ProfileDto.SwitchRoleRequest req,
            @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(profileService.switchRole(req.getRole(), user)));
    }

    // ─── Portfolio ────────────────────────────────────────────────────────────

    @GetMapping("/bloggers/{userId}/portfolio")
    @Tag(name = "Portfolio", description = "Портфолио блогера")
    @Operation(summary = "Получить портфолио блогера", description = "Публичный эндпоинт.")
    public ResponseEntity<ApiResponse1<List<ProfileDto.PortfolioItemResponse>>> getPortfolio(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse1.ok(profileService.getPortfolio(userId)));
    }

    @PostMapping("/profile/portfolio")
    @PreAuthorize("hasAnyRole('BLOGGER','AI_CREATOR')")
    @SecurityRequirement(name = "Bearer")
    @Tag(name = "Portfolio")
    @Operation(
        summary = "Добавить элемент в портфолио",
        description = """
            Добавляет медиафайл в портфолио. Сначала загрузите файл через
            `POST /api/v1/media/upload?type=PORTFOLIO`, получите `url` и передайте его сюда.

            Максимум **20 элементов** в портфолио.
            """
    )
    public ResponseEntity<ApiResponse1<ProfileDto.PortfolioItemResponse>> addPortfolioItem(
            @Valid @RequestBody ProfileDto.PortfolioItemRequest req,
            @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(profileService.addPortfolioItem(req, user)));
    }

    @DeleteMapping("/profile/portfolio/{itemId}")
    @PreAuthorize("hasAnyRole('BLOGGER','AI_CREATOR')")
    @SecurityRequirement(name = "Bearer")
    @Tag(name = "Portfolio")
    @Operation(summary = "Удалить элемент из портфолио")
    public ResponseEntity<ApiResponse1<Void>> deletePortfolioItem(
            @PathVariable UUID itemId,
            @CurrentUser User user) {
        profileService.deletePortfolioItem(itemId, user);
        return ResponseEntity.ok(ApiResponse1.ok(null));
    }

    @PutMapping("/profile/portfolio/reorder")
    @PreAuthorize("hasAnyRole('BLOGGER','AI_CREATOR')")
    @SecurityRequirement(name = "Bearer")
    @Tag(name = "Portfolio")
    @Operation(
        summary = "Изменить порядок элементов портфолио",
        description = "Передайте список UUID в нужном порядке — индекс = новый sortOrder."
    )
    public ResponseEntity<ApiResponse1<List<ProfileDto.PortfolioItemResponse>>> reorderPortfolio(
            @RequestBody ProfileDto.ReorderRequest req,
            @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(profileService.reorderPortfolio(req, user)));
    }
}
