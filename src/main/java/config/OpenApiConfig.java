package config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Squirret Backend API")
                        .version("2.0.0")
                        .description("Squirret 백엔드 API 명세 (게스트 모드)\n" +
                                "모든 API는 현재 공개적으로 접근 가능합니다."))
                .servers(List.of(
                        new Server().url("http://54.86.161.187:8080").description("Production server"),
                        new Server().url("http://localhost:8080").description("Local development server")
                ));
    }
}

