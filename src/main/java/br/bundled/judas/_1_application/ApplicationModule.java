package br.bundled.judas._1_application;

import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.module.Lifecycle;
import org.jspecify.annotations.NullMarked;

/** The use cases of the book; none yet, so it registers and opens nothing (ARCH-04). */
@NullMarked
public final class ApplicationModule implements Lifecycle {

    @Override
    public Result<Void, BusinessError> initialize() { return Result.success(); }

    @Override
    public Result<Void, BusinessError> terminate() { return Result.success(); }
}
