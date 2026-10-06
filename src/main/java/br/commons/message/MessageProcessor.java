package br.commons.message;

import br.commons.i18n.Errors;

import br.commons.Meta;
import org.jspecify.annotations.NullMarked;

import java.lang.invoke.MethodHandle;

@NullMarked
public record MessageProcessor (
        Object container,
        MethodHandle handle
) {

    public MessageResult process(Message message) {
        try {
            return (MessageResult) handle.invoke(container, message);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable throwable) {
            throw new RuntimeException(Errors.get().messageProcessingFailed(Meta.fqn(message), throwable).value(), throwable);
        }
    }

}
