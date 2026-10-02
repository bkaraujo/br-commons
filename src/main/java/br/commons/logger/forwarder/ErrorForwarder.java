package br.commons.logger.forwarder;

import br.commons.chrono.Time;
import br.commons.logger.LogLevel;
import br.commons.logger.LogRecord;
import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;

@NullMarked
public sealed class ErrorForwarder extends FatalForwarder permits WarnForwarder {

    @Override
    public final void error(Supplier<String> messageSupplier) {
        forward(new LogRecord(
                Time.millis(),
                LogLevel.ERROR,
                messageSupplier
        ));
    }

}
