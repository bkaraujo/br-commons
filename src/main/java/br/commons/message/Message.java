package br.commons.message;

import br.commons.chrono.Time;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface Message {
    long timestamp = Time.millis();
}
