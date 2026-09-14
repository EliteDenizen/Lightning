package dev.elitedenizen.lightning.util.text;

public final class ArgumentParser {

    private final String[] args;

    public ArgumentParser(String[] args) {
        this.args = args != null ? args : new String[0];
    }

    public String replace(String text) {
        if (text == null || text.isEmpty()) return text;

        String result = text;

        if (this.args.length > 0) {
            result = result.replace("{args}", String.join(" ", this.args));
            result = result.replace("{argcount}", String.valueOf(this.args.length));
            for (int i = 0; i < this.args.length; i++) {
                result = result.replace("{arg" + (i + 1) + "}", this.args[i]);
            }
        } else {
            result = result.replace("{args}", "");
            result = result.replace("{argcount}", "0");
        }

        for (int i = this.args.length + 1; i <= 10; i++) {
            result = result.replace("{arg" + i + "}", "");
        }

        return result;
    }

    public String get(int index) {
        return (index >= 0 && index < args.length) ? args[index] : "";
    }

    public int count() { return args.length; }
    public String[] all() { return args; }
    public String joined() { return args.length > 0 ? String.join(" ", args) : ""; }
}
