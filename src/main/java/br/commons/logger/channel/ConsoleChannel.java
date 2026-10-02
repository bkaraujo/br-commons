package br.commons.logger.channel;

import br.commons.logger.LogChannel;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ConsoleChannel implements LogChannel {

    @Override
    public void write(String message) {
        System.out.printf("%s%n", message);
    }

}
