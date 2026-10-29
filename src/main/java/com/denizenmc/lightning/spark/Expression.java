package com.denizenmc.lightning.spark;

// Parses formula expressions with arithmetic operators, powers, and parentheses.
final class Expression {

    // Formulas can contain placeholder values, so their size and nesting are limited
    // to keep unexpected input from exhausting the stack.
    private static final int MAX_LENGTH = 256;
    private static final int MAX_DEPTH = 32;

    private final String input;
    private int position;
    private int depth;

    private Expression(final String input) {
        this.input = input;
    }

    static double evaluate(final String text) {
        if (text.length() > MAX_LENGTH) throw new IllegalArgumentException("The formula is too long.");
        Expression expression = new Expression(text.replace(" ", ""));
        double result = expression.sum();
        if (expression.position != expression.input.length()) {
            throw new IllegalArgumentException("Unexpected '" + expression.input.charAt(expression.position) + "'");
        }
        return result;
    }

    private double sum() {
        double value = product();
        while (true) {
            if (take('+')) value += product();
            else if (take('-')) value -= product();
            else return value;
        }
    }

    private double product() {
        double value = power();
        while (true) {
            if (take('*')) value *= power();
            else if (take('/')) value /= power();
            else if (take('%')) value %= power();
            else return value;
        }
    }

    private double power() {
        double base = unary();
        return take('^') ? Math.pow(base, power()) : base;
    }

    private double unary() {
        if (take('-')) return -unary();
        if (take('+')) return unary();
        if (take('(')) {
            if (++depth > MAX_DEPTH) throw new IllegalArgumentException("The formula is nested too deeply.");
            double value = sum();
            depth--;
            if (!take(')')) throw new IllegalArgumentException("Missing ')'");
            return value;
        }
        int start = position;
        while (position < input.length() && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.')) position++;
        if (start == position) throw new IllegalArgumentException("Expected a number at " + start);
        return Double.parseDouble(input.substring(start, position));
    }

    private boolean take(final char c) {
        if (position < input.length() && input.charAt(position) == c) {
            position++;
            return true;
        }
        return false;
    }
}