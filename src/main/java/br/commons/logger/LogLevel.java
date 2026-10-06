package br.commons.logger;

import br.commons.Strings;
import br.commons.platform.terminal.TerminalColor;
import lombok.val;
import org.jspecify.annotations.NullMarked;

@NullMarked
public enum LogLevel {
    OFF    ("OFF", TerminalColor.RESET),
    VERBOSE("VERBOSE", TerminalColor.PURPLE_BOLD),
    TRACE  ("TRACE  ", TerminalColor.PURPLE_BOLD),
    DEBUG  ("DEBUG  ", TerminalColor.BLUE_BOLD),
    INFO   ("INFO   ", TerminalColor.GREEN_BOLD),
    WARN   ("WARN   ", TerminalColor.YELLOW_BOLD),
    ERROR  ("ERROR  ", TerminalColor.RED_BOLD),
    FATAL  ("FATAL  ", TerminalColor.RED_BOLD);

    public final TerminalColor color;
    public final String colored;
    public final int ordinal;

    @SuppressWarnings("EnumOrdinal")
    LogLevel(String message, TerminalColor desired) {
        this.color = desired;
        colored = "%s%s".formatted(desired.color, message);
        this.ordinal = this.ordinal();
    }


    public static LogLevel fromSlf4jLevel(String level) {
        val name = Strings.upper(level);
        if (name.equals("WARNING")) return WARN;
        for (val candidate : values()) {
            if (candidate.name().equals(name)) return candidate;
        }
        return INFO;
    }
}
