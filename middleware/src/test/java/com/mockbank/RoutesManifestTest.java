package com.mockbank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mockbank.account.AccountService;
import com.mockbank.billpay.BillPayService;
import com.mockbank.common.CustomerContext;
import com.mockbank.common.OpenApiConfig;
import com.mockbank.creditcard.CreditCardService;
import com.mockbank.persistence.CustomerRepository;
import com.mockbank.persistence.RecipientRepository;
import com.mockbank.transfer.ApprovalService;
import com.mockbank.transfer.TransferService;
import com.mockbank.withdraw.WithdrawService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generates {@code routes.json} at the repository root, and fails when the
 * committed file disagrees with what this application actually serves.
 *
 * <p><b>WHY THIS EXISTS.</b> A test pipeline can discover our surface from the
 * OpenAPI document we already publish, but OpenAPI names a HANDLER and never
 * says which FILE it lives in — it describes what an application serves, not
 * where its source is. Without a file, an automated repair has nothing to open,
 * and change detection cannot narrow a diff to the endpoints it touches.
 * Supplying the file is our obligation, because we are the only ones who know
 * it.
 *
 * <p><b>WHY IT IS A TEST AND NOT A SCRIPT.</b> A hand-written manifest goes
 * stale the moment somebody adds an endpoint and forgets, and a stale manifest
 * is worse than none: it produces a wrong impact set that nothing detects, so
 * the pipeline confidently tests the wrong surface. The same code that writes
 * the file verifies it, and this test fails on the commit that introduces the
 * drift rather than in somebody else's run a week later.
 *
 * <p><b>WHERE IT GETS THE ROUTES.</b> {@link RequestMappingHandlerMapping} —
 * Spring's own route table, the same object that dispatches a real request.
 * Not a parse of our annotations, and not a list maintained by hand. Adding a
 * {@code @GetMapping} anywhere is picked up with no edit here.
 *
 * <p>To regenerate after an intentional change:
 * <pre>mvn -q -B test -Droutes.write=true</pre>
 */
@WebMvcTest
class RoutesManifestTest {

    /**
     * Repo-root-relative prefix for a handler's source file. This module's
     * sources live under {@code middleware/}, and the manifest is read by
     * something that cloned the whole repository — so paths are relative to the
     * repository, not to this module.
     */
    private static final String SOURCE_ROOT = "middleware/src/main/java/";

    /**
     * The prefix this application is served under, as the pipeline reaches it.
     *
     * <p>EMPTY, deliberately. The browser reaches us through nginx at
     * {@code /api/**}, which strips the prefix before proxying — that is the
     * frontend's arrangement, not ours. Anything talking to this service
     * directly talks to it at the root, and declaring {@code /api} here would
     * send it to a path we do not serve.
     */
    private static final String ROUTE_PREFIX = "";

    /** The customer header {@link CustomerContext} reads off every request. */
    private static final String CUSTOMER_HEADER = CustomerContext.CUSTOMER_ID_HEADER;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    // The controllers' collaborators. Mocked because this test asks what we
    // SERVE, which is answered by the route table alone -- no database, no
    // scoring services, and therefore nothing to stand up before it can run.
    @MockBean private CustomerContext customerContext;
    @MockBean private AccountService accountService;
    @MockBean private BillPayService billPayService;
    @MockBean private CreditCardService creditCardService;
    @MockBean private ApprovalService approvalService;
    @MockBean private TransferService transferService;
    @MockBean private WithdrawService withdrawService;
    @MockBean private CustomerRepository customerRepository;
    @MockBean private RecipientRepository recipientRepository;

    @Test
    void the_committed_manifest_matches_what_this_application_serves() throws IOException {
        String generated = render(collect());
        Path manifest = repositoryRoot().resolve("routes.json");

        if (Boolean.getBoolean("routes.write")) {
            Files.writeString(manifest, generated, StandardCharsets.UTF_8);
            System.out.println("routes.json written: " + manifest.toAbsolutePath());
            return;
        }

        assertTrue(
                Files.exists(manifest),
                "routes.json is missing at " + manifest.toAbsolutePath()
                        + ". It is generated from this application's own route table: "
                        + "run `mvn -B test -Droutes.write=true` and commit the result.");

        String committed = Files.readString(manifest, StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertEquals(
                generated,
                committed,
                "routes.json disagrees with the routes this application actually serves. "
                        + "A stale manifest is worse than none: whatever reads it derives test "
                        + "cases for endpoints that no longer exist and none for the ones that "
                        + "do. Regenerate with `mvn -B test -Droutes.write=true` and commit it "
                        + "in the same change that moved the routes.");
    }

    @Test
    void every_route_names_the_file_its_handler_lives_in() {
        // The whole reason this file exists. A manifest that omitted the file
        // would satisfy the shape and be useless for the thing it is for.
        for (ObjectNode route : collect()) {
            String path = route.get("path").asText();
            assertFalse(
                    route.get("handler_file").asText().isBlank(),
                    path + " has no handler_file");
            assertFalse(
                    route.get("handler_symbol").asText().isBlank(),
                    path + " has no handler_symbol");
        }
    }

    @Test
    void the_open_paths_are_the_only_ones_without_a_customer_header() {
        // Derived from OpenApiConfig.OPEN_PATHS rather than restated, so the
        // manifest and the published OpenAPI document cannot disagree about
        // which endpoints are reachable without logging in.
        for (ObjectNode route : collect()) {
            String path = route.get("path").asText();
            boolean declaresCustomerHeader = false;
            for (var header : route.withArray("required_headers")) {
                declaresCustomerHeader |= CUSTOMER_HEADER.equals(header.asText());
            }
            assertEquals(
                    !OpenApiConfig.OPEN_PATHS.contains(path),
                    declaresCustomerHeader,
                    path + ": required_headers disagrees with OpenApiConfig.OPEN_PATHS");
        }
    }

    // ------------------------------------------------------------------
    // reading the route table
    // ------------------------------------------------------------------

    private List<ObjectNode> collect() {
        List<ObjectNode> routes = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                : handlerMapping.getHandlerMethods().entrySet()) {

            HandlerMethod handler = entry.getValue();
            Class<?> declaring = handler.getBeanType();

            // OURS ONLY. The slice also carries framework-provided mappings --
            // the error controller, springdoc when it is on the classpath --
            // and those are not surface this application is responsible for.
            if (!declaring.getName().startsWith("com.mockbank.")) {
                continue;
            }

            Set<String> patterns = new TreeSet<>();
            if (entry.getKey().getPathPatternsCondition() != null) {
                entry.getKey().getPathPatternsCondition().getPatternValues()
                        .forEach(patterns::add);
            }

            Set<String> methods = new TreeSet<>();
            entry.getKey().getMethodsCondition().getMethods()
                    .forEach(m -> methods.add(m.name()));
            if (methods.isEmpty()) {
                // A mapping with no verb answers all of them. We declare none,
                // and this application has no such mapping -- if one appears,
                // fail rather than invent a verb for it.
                throw new IllegalStateException(
                        "mapping " + patterns + " declares no HTTP method; the manifest "
                                + "cannot say which verb to exercise it with.");
            }

            for (String pattern : patterns) {
                for (String method : methods) {
                    routes.add(describe(pattern, method, handler, declaring));
                }
            }
        }

        routes.sort(Comparator
                .comparing((ObjectNode r) -> r.get("path").asText())
                .thenComparing(r -> r.get("method").asText()));
        return routes;
    }

    private ObjectNode describe(String path, String method, HandlerMethod handler, Class<?> declaring) {
        ObjectNode route = MAPPER.createObjectNode();
        route.put("path", path);
        route.put("method", method);
        route.put("handler_file", SOURCE_ROOT + declaring.getName().replace('.', '/') + ".java");
        route.put("handler_symbol", handler.getMethod().getName());
        // API throughout: every handler here answers with data. Nothing in this
        // service renders a page -- the SPA is a separate service -- so a route
        // needing a browser to verify would be a new fact about the app, not a
        // detail of this generator.
        route.put("kind", "API");

        ArrayNode headers = route.putArray("required_headers");
        requiredHeaders(path, handler.getMethod()).forEach(headers::add);

        ArrayNode fields = route.putArray("body_fields");
        requiredBodyFields(handler.getMethod()).forEach(fields::add);

        route.put("route_prefix", ROUTE_PREFIX);
        return route;
    }

    /**
     * Headers a request MUST carry, in the names this application reads them by.
     *
     * <p>Two sources, and both are needed. {@code @RequestHeader} parameters are
     * declared on the method and found by reflection. The customer header is
     * NOT: {@link CustomerContext} reads it off the raw request, so it appears
     * in no signature, and the only statement of which paths need it is
     * {@link OpenApiConfig#OPEN_PATHS}.
     *
     * <p>A header with a default value is not required — it has an answer when
     * the caller says nothing, which is the definition of optional.
     */
    private Set<String> requiredHeaders(String path, Method method) {
        Set<String> headers = new LinkedHashSet<>();

        if (!OpenApiConfig.OPEN_PATHS.contains(path)) {
            headers.add(CUSTOMER_HEADER);
        }

        for (Parameter parameter : method.getParameters()) {
            RequestHeader annotation = parameter.getAnnotation(RequestHeader.class);
            if (annotation == null || !annotation.required()) {
                continue;
            }
            if (!ValueConstants_DEFAULT_NONE.equals(annotation.defaultValue())) {
                continue;  // has a default, so a caller may omit it
            }
            String name = annotation.value().isEmpty() ? annotation.name() : annotation.value();
            headers.add(name.isEmpty() ? parameter.getName() : name);
        }

        List<String> sorted = new ArrayList<>(headers);
        sorted.sort(Comparator.naturalOrder());
        return new LinkedHashSet<>(sorted);
    }

    /** Spring's own sentinel for "no default was given". */
    private static final String ValueConstants_DEFAULT_NONE =
            org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE;

    /**
     * Body fields a request MUST carry.
     *
     * <p>Only what the request type ANNOTATES as required — {@code @NotNull} or
     * {@code @NotBlank}. A field the service rejects later without declaring it
     * here is not derivable from the type, and guessing would put a field in the
     * manifest that nothing in the code demands.
     */
    private Set<String> requiredBodyFields(Method method) {
        Set<String> fields = new TreeSet<>();

        for (Parameter parameter : method.getParameters()) {
            if (parameter.getAnnotation(RequestBody.class) == null) {
                continue;
            }
            Class<?> type = parameter.getType();
            if (!type.isRecord()) {
                continue;
            }
            for (RecordComponent component : type.getRecordComponents()) {
                if (isRequired(component, type)) {
                    fields.add(component.getName());
                }
            }
        }
        return fields;
    }

    private boolean isRequired(RecordComponent component, Class<?> owner) {
        // A record component's annotations land on the component, the backing
        // field, or the accessor depending on the annotation's @Target, so all
        // three are checked rather than assuming one.
        if (hasRequiredAnnotation(component.getAnnotations())
                || hasRequiredAnnotation(component.getAccessor().getAnnotations())) {
            return true;
        }
        try {
            Field field = owner.getDeclaredField(component.getName());
            return hasRequiredAnnotation(field.getAnnotations());
        } catch (NoSuchFieldException ignored) {
            return false;
        }
    }

    private boolean hasRequiredAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (NotNull.class.equals(type) || NotBlank.class.equals(type)) {
                return true;
            }
            if (AnnotatedElementUtils.isAnnotated(type, NotNull.class.getName())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // writing it
    // ------------------------------------------------------------------

    private String render(List<ObjectNode> routes) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode array = root.putArray("routes");
        routes.forEach(array::add);
        // Pretty-printed with LF endings and a trailing newline, so the file is
        // reviewable in a diff and byte-identical on every platform.
        return MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(root)
                .replace("\r\n", "\n") + "\n";
    }

    /** The repository root. Maven runs this with {@code middleware/} as its basedir. */
    private Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().getParent();
    }
}
