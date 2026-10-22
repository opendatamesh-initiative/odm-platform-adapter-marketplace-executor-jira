package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@ApplicationScoped
public class RequestFilterService {

    private final JiraConfig.Filter filter;
    private final List<Criterion> criteria;

    public RequestFilterService(JiraConfig config) {
        this.filter = config.filter();
        this.criteria = compileCriteria();
    }

    public boolean accepts(MarketplaceOperation operation, MarketplaceRequest request) {
        return firstMismatch(operation, request).isEmpty();
    }

    /** Name of the first failing criterion, or an empty string when the request matches. */
    public String describeMismatch(MarketplaceOperation operation, MarketplaceRequest request) {
        return firstMismatch(operation, request).orElse("");
    }

    public void verifyPatterns() {
        compileCriteria();
    }

    private Optional<String> firstMismatch(MarketplaceOperation operation, MarketplaceRequest request) {
        return criteria.stream()
                .filter(criterion -> !criterion.matches(operation, request))
                .map(Criterion::name)
                .findFirst();
    }

    private List<Criterion> compileCriteria() {
        List<Criterion> compiled = new ArrayList<>();
        addValueCriterion(compiled, "operations", filter.operations(), (op, req) -> op == null ? null : op.name());
        addValueCriterion(compiled, "provider-data-product-fqns", filter.providerDataProductFqns(),
                (op, req) -> req.provider() == null ? null : req.provider().dataProductFqn());
        addPortsCriterion(compiled);
        addValueCriterion(compiled, "consumer-types", filter.consumerTypes(),
                (op, req) -> req.consumer() == null ? null : req.consumer().type());
        addValueCriterion(compiled, "consumer-identifiers", filter.consumerIdentifiers(),
                (op, req) -> req.consumer() == null ? null : req.consumer().identifier());
        addValueCriterion(compiled, "requester-identifiers", filter.requesterIdentifiers(),
                (op, req) -> req.requester() == null ? null : req.requester().identifier());
        addPropertyCriteria(compiled);
        return List.copyOf(compiled);
    }

    private void addValueCriterion(List<Criterion> compiled, String name, Optional<List<String>> regexes,
                                   ValueExtractor valueOf) {
        List<Pattern> patterns = patterns(name, regexes);
        if (patterns.isEmpty()) {
            return;
        }
        compiled.add(new Criterion(name, (op, req) -> matchesAny(patterns, valueOf.apply(op, req))));
    }

    private void addPortsCriterion(List<Criterion> compiled) {
        String name = "port-fqns";
        List<Pattern> patterns = patterns(name, filter.portFqns());
        if (patterns.isEmpty()) {
            return;
        }
        compiled.add(new Criterion(name, (op, req) -> {
            List<String> ports = req.provider() == null ? null : req.provider().dataProductPortsFqn();
            return ports != null && !ports.isEmpty() && ports.stream().allMatch(port -> matchesAny(patterns, port));
        }));
    }

    private void addPropertyCriteria(List<Criterion> compiled) {
        for (Map.Entry<String, String> entry : filter.properties().entrySet()) {
            String key = entry.getKey();
            Pattern pattern = compile("properties." + key, entry.getValue());
            compiled.add(new Criterion("properties." + key, (op, req) -> {
                Map<String, Object> properties = req.properties();
                return properties != null && properties.containsKey(key)
                        && pattern.matcher(String.valueOf(properties.get(key))).matches();
            }));
        }
    }

    private List<Pattern> patterns(String name, Optional<List<String>> regexes) {
        List<String> values = regexes.orElse(List.of());
        List<Pattern> patterns = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            patterns.add(compile(name + "[" + i + "]", values.get(i)));
        }
        return patterns;
    }

    private Pattern compile(String criterion, String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException | NullPointerException e) {
            throw new InternalException("Invalid filter regex for " + criterion + ": " + e.getMessage(), e);
        }
    }

    private boolean matchesAny(List<Pattern> patterns, String value) {
        return value != null && patterns.stream().anyMatch(pattern -> pattern.matcher(value).matches());
    }

    private interface ValueExtractor {
        String apply(MarketplaceOperation operation, MarketplaceRequest request);
    }

    private interface Check {
        boolean test(MarketplaceOperation operation, MarketplaceRequest request);
    }

    private record Criterion(String name, Check check) {

        boolean matches(MarketplaceOperation operation, MarketplaceRequest request) {
            return check.test(operation, request);
        }
    }
}
