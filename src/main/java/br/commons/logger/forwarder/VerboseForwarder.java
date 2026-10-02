package br.commons.logger.forwarder;

import br.commons.chrono.Time;
import br.commons.logger.LogLevel;
import br.commons.logger.LogRecord;
import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;

@NullMarked
public final class VerboseForwarder extends TraceForwarder {

    @Override
    public void verbose(Supplier<String> messageSupplier) {
        forward(new LogRecord(
                Time.millis(),
                LogLevel.VERBOSE,
                messageSupplier
        ));
    }

}
