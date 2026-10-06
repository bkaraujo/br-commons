package br.commons.annotation;

import br.commons.Result;
import br.commons.business.BusinessError;

public interface Lifecycle {

    default Result<Void, BusinessError> initialize() {
        return Result.success(null);
    }

    default Result<Void, BusinessError> terminate() {
        return Result.success(null);
    }
}
