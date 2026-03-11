package com.collab.security;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Collab API")
                        .description("""
                                ## Collab Platform — REST API
                                
                                Маркетплейс для UGC-блогеров и брендов.
                                
                                ### Аутентификация
                                1. Зарегистрируйтесь: `POST /api/v1/auth/register`
                                2. Войдите: `POST /api/v1/auth/login` → получите `accessToken`
                                3. Вставьте токен в кнопку **Authorize** → `Bearer <token>`
                                
                                ### Роли
                                | Роль | Описание |
                                |------|----------|
                                | `BLOGGER` | UGC-блогер — откликается на задания |
                                | `AI_CREATOR` | AI-креатор — откликается на AI-задания |
                                | `BRAND` | Бренд/компания — создаёт задания |
                                | `ADMIN` | Администратор платформы |
                                
                                ### WebSocket (чат)
                                Подключение через STOMP/SockJS на `/ws`.  
                                Заголовок при CONNECT: `Authorization: Bearer <token>`  
                                Подписка: `/topic/chat/{applicationId}`  
                                Отправка: `/app/chat/{applicationId}`
                                """)
                        .version("3.0.0")
                        .contact(new Contact()
                                .name("Collab Team")
                                .email("dev@collab.app"))
                        .license(new License().name("Private")))
                .servers(List.of(
                        new Server().url("http://10.117.6.120:8080").description("Local")))
                                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .name("Bearer")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Вставьте JWT токен из /auth/login")));
    }
}
