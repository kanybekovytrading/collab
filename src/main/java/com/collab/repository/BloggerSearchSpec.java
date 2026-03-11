package com.collab.repository;

import com.collab.common.enums.ContentCategory;
import com.collab.common.enums.UserRole;
import com.collab.entity.BloggerProfile;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BloggerSearchSpec {

    public static Specification<BloggerProfile> build(BloggerFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Только активные пользователи
            predicates.add(cb.isTrue(root.get("user").get("active")));

            // Страна
            if (f.country() != null && !f.country().isBlank())
                predicates.add(cb.equal(
                        cb.lower(root.get("user").get("country")), f.country().toLowerCase()));

            // Город
            if (f.city() != null && !f.city().isBlank())
                predicates.add(cb.equal(
                        cb.lower(root.get("user").get("city")), f.city().toLowerCase()));

            // Возраст
            if (f.minAge() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("user").get("age"), f.minAge()));
            if (f.maxAge() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("user").get("age"), f.maxAge()));

            // Минимальный рейтинг
            if (f.minRating() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), f.minRating()));

            // Работает с бартером
            if (Boolean.TRUE.equals(f.worksWithBarter()))
                predicates.add(cb.isTrue(root.get("worksWithBarter")));

            // Максимальная цена
            if (f.maxPrice() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("minPrice"), f.maxPrice()));

            // Категория контента (хотя бы одна совпадает)
            if (f.category() != null) {
                Join<Object, Object> catJoin = root.join("categories", JoinType.INNER);
                predicates.add(cb.equal(catJoin, f.category()));
                if (query != null) query.distinct(true);
            }

            // Тип аккаунта: UGC или AI
            if (f.role() != null) {
                Join<Object, Object> rolesJoin = root.join("user", JoinType.INNER)
                        .join("roles", JoinType.INNER);
                predicates.add(cb.equal(rolesJoin, f.role()));
                if (query != null) query.distinct(true);
            }

            // Полнотекстовый поиск по имени
            if (f.search() != null && !f.search().isBlank()) {
                String pattern = "%" + f.search().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("user").get("fullName")), pattern),
                        cb.like(cb.lower(root.get("bio")), pattern)
                ));
            }

            // Верифицированные
            if (Boolean.TRUE.equals(f.verifiedOnly()))
                predicates.add(cb.isTrue(root.get("user").get("verified")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Фильтры поиска блогеров
    public record BloggerFilter(
            String country,
            String city,
            Integer minAge,
            Integer maxAge,
            Double minRating,
            Boolean worksWithBarter,
            BigDecimal maxPrice,
            ContentCategory category,
            UserRole role,          // BLOGGER или AI_CREATOR
            String search,          // поиск по имени/bio
            Boolean verifiedOnly
    ) {}
}
