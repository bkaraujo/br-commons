package br.bundled.judas._2_infrastructure.persistence;

import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.module.Lifecycle;
import org.jspecify.annotations.NullMarked;

/** The repositories over the database; none yet, so it migrates and closes nothing (ARCH-07). */
@NullMarked
public final class PersistenceModule implements Lifecycle {

    @Override
    public Result<Void, BusinessError> initialize() { return Result.success(); }

    @Override
    public Result<Void, BusinessError> terminate() { return Result.success(); }
}
