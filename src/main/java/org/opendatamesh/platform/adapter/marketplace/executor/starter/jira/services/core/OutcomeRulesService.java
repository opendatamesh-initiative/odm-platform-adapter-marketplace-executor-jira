package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

import jakarta.enterprise.context.ApplicationScoped;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcome;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@ApplicationScoped
public class OutcomeRulesService {

    private final JiraConfig config;
    private final ConcurrentMap<String, Pattern> patterns = new ConcurrentHashMap<>();

    public OutcomeRulesService(JiraConfig config) {
        this.config = config;
    }

    public Optional<TicketOutcome> evaluate(MarketplaceOperation operation, Map<String, String> fields) {
        return config.profiles().forOperation(operation).outcomeRules().stream()
                .filter(rule -> matches(rule, fields))
                .findFirst()
                .map(rule -> new TicketOutcome(rule.outcome(), rule.message().orElse(null)));
    }

    public void verifyRules() {
        for (MarketplaceOperation operation : MarketplaceOperation.values()) {
            verifyProfileRules(operation);
        }
    }

    private void verifyProfileRules(MarketplaceOperation operation) {
        List<JiraConfig.OutcomeRule> rules = config.profiles().forOperation(operation).outcomeRules();
        for (int i = 0; i < rules.size(); i++) {
            verifyRule(operation, i, rules.get(i));
        }
    }

    private void verifyRule(MarketplaceOperation operation, int index, JiraConfig.OutcomeRule rule) {
        for (Map.Entry<String, String> condition : rule.when().entrySet()) {
            try {
                Pattern.compile(condition.getValue());
            } catch (PatternSyntaxException e) {
                throw new InternalException("Invalid outcome rule regex in profile " + operation + ", rule " + index
                        + ", field " + condition.getKey() + ": " + e.getMessage(), e);
            }
        }
    }

    private boolean matches(JiraConfig.OutcomeRule rule, Map<String, String> fields) {
        return rule.when().entrySet().stream().allMatch(condition -> {
            String value = fields.get(condition.getKey());
            return value != null && pattern(condition.getValue()).matcher(value).matches();
        });
    }

    private Pattern pattern(String regex) {
        return patterns.computeIfAbsent(regex, Pattern::compile);
    }
}
