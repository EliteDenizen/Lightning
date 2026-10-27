package com.denizenmc.lightning.spark;

// Parses formula expressions with arithmetic operators, powers, and parentheses.
final class Expression {

    private final String input;
    private int position;

    private Expression(final String input) {
        this.input = input;
    }

    static double evaluate(final String text) {
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
            double value = sum();
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
