package com.denizenmc.lightning.custom;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

// Evaluates expressions using comparisons, permission checks, and "and"/"or" operators.
// Numeric values are compared numerically; other values are compared as text.
final class Condition {

    private static final String[] OPERATORS = {">=", "<=", "!=", "==", ">", "<"};

    private Condition() {
    }

    static boolean test(@NotNull final String expression, @NotNull final CommandSender sender) {
        for (String either : expression.split("(?i)\\s+or\\s+")) {
            boolean all = true;
            for (String part : either.split("(?i)\\s+and\\s+")) {
                if (!single(part.trim(), sender)) {
                    all = false;
                    break;
                }
            }
            if (all) return true;
        }
        return false;
    }

    private static boolean single(final String part, final CommandSender sender) {
        String lower = part.toLowerCase(Locale.ROOT);
        if (lower.startsWith("not ")) return !single(part.substring(4).trim(), sender);
        if (lower.startsWith("has ")) return sender.hasPermission(part.substring(4).trim());

        for (String operator : OPERATORS) {
            int at = part.indexOf(operator);
            if (at < 0) continue;
            return compare(clean(part.substring(0, at)), clean(part.substring(at + operator.length())), operator);
        }
        return !(lower.isEmpty() || lower.equals("false") || lower.equals("no") || lower.equals("0"));
    }

    private static boolean compare(final String left, final String right, final String operator) {
        Double a = number(left);
        Double b = number(right);
        if (a != null && b != null) {
            int order = Double.compare(a, b);
            return switch (operator) {
                case ">=" -> order >= 0;
                case "<=" -> order <= 0;
                case ">" -> order > 0;
                case "<" -> order < 0;
                case "==" -> order == 0;
                default -> order != 0;
            };
        }
        return switch (operator) {
            case "==" -> left.equalsIgnoreCase(right);
            case "!=" -> !left.equalsIgnoreCase(right);
            default -> false;
        };
    }

    private static String clean(final String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && (trimmed.startsWith("'") && trimmed.endsWith("'") || trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static Double number(final String value) {
        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}