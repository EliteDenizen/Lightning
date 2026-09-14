package dev.elitedenizen.lightning.command;

import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CommandArgument {

    public enum Suggestions { STATIC, PLAYERS, WORLDS, NONE }

    private final String name;
    private final boolean required;
    private final List<String> staticSuggestions;
    private final Suggestions suggestionType;
    private final Pattern pattern;
    private final String patternMessage;

    private CommandArgument(Builder b) {
        this.name = b.name != null ? b.name : "arg";
        this.required = b.required;
        this.staticSuggestions = Collections.unmodifiableList(new ArrayList<>(b.suggestions));
        this.suggestionType = b.suggestionType;
        this.pattern = b.pattern;
        this.patternMessage = b.patternMessage;
    }

    public String getName() { return name; }
    public boolean isRequired() { return required; }
    public List<String> getStaticSuggestions() { return staticSuggestions; }
    public Suggestions getSuggestionType() { return suggestionType; }
    public boolean hasPattern() { return pattern != null; }
    public String getPatternMessage() { return patternMessage; }

    public boolean matchesPattern(String value) {
        return pattern == null || pattern.matcher(value).matches();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private boolean required;
        private final List<String> suggestions = new ArrayList<>();
        private Suggestions suggestionType = Suggestions.NONE;
        private Pattern pattern;
        private String patternMessage;

        private Builder(String name) {
            this.name = name;
        }

        public Builder required(boolean r) { this.required = r; return this; }

        public Builder staticSuggestions(List<String> list) {
            if (list != null && !list.isEmpty()) {
                suggestions.addAll(list);
                suggestionType = Suggestions.STATIC;
            }
            return this;
        }

        public Builder suggestionKeyword(String keyword) {
            if (keyword == null || suggestionType == Suggestions.STATIC) return this;
            switch (keyword.trim().toLowerCase()) {
                case "players": suggestionType = Suggestions.PLAYERS; break;
                case "worlds": suggestionType = Suggestions.WORLDS; break;
                default: suggestionType = Suggestions.NONE; break;
            }
            return this;
        }

        public Builder pattern(String regex, String message) {
            if (regex == null || regex.trim().isEmpty()) return this;
            try {
                this.pattern = Pattern.compile(regex);
                this.patternMessage = message;
            } catch (PatternSyntaxException e) {
                PluginLogger.warn("Invalid regex pattern for argument '{0}': {1}", name, e.getMessage());
            }
            return this;
        }

        public CommandArgument build() {
            return new CommandArgument(this);
        }
    }
}
