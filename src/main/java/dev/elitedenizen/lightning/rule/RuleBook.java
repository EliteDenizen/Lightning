package dev.elitedenizen.lightning.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RuleBook {

    private List<Rule> rules = Collections.emptyList();

    public void setRules(List<Rule> rules) {
        this.rules = rules != null ? Collections.unmodifiableList(new ArrayList<>(rules)) : Collections.emptyList();
    }

    public List<Rule> getAll() {
        return rules;
    }

    public int size() {
        return rules.size();
    }

    public List<Rule> findMatching(String pluginName, String command) {
        List<Rule> matched = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule.matches(command, pluginName)) matched.add(rule);
        }
        return matched;
    }
}
