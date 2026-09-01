package com.mockbank.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Describes the API surface at {@code /v3/api-docs}.
 * <p>
 * The one thing springdoc cannot work out on its own is the fake login:
 * {@link CustomerContext} reads {@code X-Customer-Id} off the raw request rather
 * than declaring it as a {@code @RequestHeader}, so no controller signature
 * mentions it and it would be missing from every operation. It is added here
 * instead — to everything except the two endpoints that genuinely do not need
 * it.
 */
@Configuration
public class OpenApiConfig {

    /**
     * The only paths that work without a customer header.
     *
     * PUBLIC because the routes manifest generator derives `required_headers`
     * from exactly this rule. {@link CustomerContext} reads the header off the
     * raw request rather than declaring it as a {@code @RequestHeader}, so no
     * controller signature mentions it and it cannot be found by reflection --
     * it has to come from the one place that states the rule. Two copies of
     * "which paths are open" would drift, and the manifest would then declare a
     * header the app does not want or omit one it enforces.
     */
    public static final Set<String> OPEN_PATHS = Set.of("/health", "/login");

    @Bean
    public OpenAPI mockBankOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Mock Bank API")
                .version("1.0")
                .description("""
                        Mock retail banking API. Every business failure returns \
                        422 {reason, message} with a specific reason code; 401 \
                        means no usable X-Customer-Id was sent."""));
    }

    @Bean
    public OpenApiCustomizer customerHeaderCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) -> {
                if (OPEN_PATHS.contains(path)) {
                    return;
                }

                pathItem.readOperations().forEach(operation -> {
                    boolean alreadyDocumented = operation.getParameters() != null
                            && operation.getParameters().stream()
                            .anyMatch(parameter -> CustomerContext.CUSTOMER_ID_HEADER.equals(parameter.getName()));

                    if (!alreadyDocumented) {
                        operation.addParametersItem(new HeaderParameter()
                                .name(CustomerContext.CUSTOMER_ID_HEADER)
                                .required(true)
                                .schema(new StringSchema())
                                .description("Id of the signed-in customer. Missing or unknown gives 401 NOT_AUTHENTICATED."));
                    }
                });
            });
        };
    }
}
