package br.commons.persistence.jdbc;

import br.commons.i18n.Errors;

import br.commons.Result;
import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class Results {
    private Results(){}

    public static <T> Result<T, String> dataSourceNotFound(String name)  {
        return Result.failure(
                Errors.get().dataSourceNotFound(name).value()
        );
    }

    public static <T> Result<T, String> resourceIsClosed(String name)  {
        return Result.failure(
                Errors.get().resourceClosed(name).value()
        );
    }

    public static <T> Result<T, String> noDefaultDatasource()  {
        return Result.failure(
                Errors.get().noDefaultDataSource().value()
        );
    }

    public static <T> Result<T, String> poolExhausted(String datasource, long timeout)  {
        return Result.failure(
                Errors.get().poolTimeout(String.valueOf(timeout), datasource).value()
        );
    }

    public static <T> Result<T, String> error(String datasource, Throwable cause)  {
        return Result.failure(
                "%s: %s".formatted(datasource, cause)
        );
    }

    public static <T> Result<T, String> error(String text)  {
        return Result.failure(
                text
        );
    }

}
