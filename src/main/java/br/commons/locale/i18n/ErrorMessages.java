package br.commons.locale.i18n;

import org.jspecify.annotations.NullMarked;

/**
 * Every error message of the library: one method per key of {@code br/commons/locale/l10n/errors_<locale>.properties}
 * (pt-BR and en-US). Obtain it with {@link Errors#get()}; placeholders are the parameter names.
 * Numbers that must not get thousands separators (port, year, position) are taken as {@code String}.
 */
@NullMarked
public interface ErrorMessages {

    // core
    Text resultIsFailure(Object error);
    Text noPublicConstructor(Object type, Object args);
    Text ambiguousConstructors(Object type, Object args);
    Text constructorFailed(Object type, Object cause);
    Text cannotCreate(Object type, Object cause);
    Text platformUnsupported(Object service, Object platform, Object osName);
    Text unsupportedPlatform();
    Text unexpectedLogLevel(Object level);
    Text noInstanceRegistered(Object type);
    Text messageProcessingFailed(Object message, Object cause);
    Text hexColorWithoutHash();
    Text sha256Unavailable();

    // persistence.json
    Text fieldReadFailed(Object field, Object file, Object cause);
    Text fieldWriteFailed(Object field, Object file, Object cause);
    Text listFilesFailed(Object directory);
    Text fileOutsideStorage(Object file);
    Text invalidFileName(Object file);

    // persistence.jdbc.migration
    Text unsupportedDatabase(Object product);
    Text migrationFailed(String version, Object cause);
    Text appliedMigrationMissing(String version);
    Text appliedMigrationChanged(String version);
    Text migrationRunFailed(Object cause);
    Text listMigrationsFailed(Object location, Object cause);
    Text duplicateMigrationVersion(String version);
    Text migrationNotReadable(Object resource);

    // persistence.jdbc
    Text transactionInitializerFailed(Object error);
    Text nullTransaction(Object name);
    Text commitFailed(Object error);
    Text saveFailed(Object table, Object error);
    Text dataSourceNotFound(Object name);
    Text resourceClosed(Object name);
    Text noDefaultDataSource();
    Text poolTimeout(String timeout, Object pool);
    Text poolInitFailed(Object cause);
    Text poolClosed();
    Text jdbcDriverNotFound(Object driver);
    Text connectionAlreadyReturned();

    // web
    Text routeNotFound();
    Text routerFrozen(Object method, Object template);
    Text optionsNotRegistrable(Object template);
    Text invalidPathParameter(Object name, Object template);
    Text repeatedPathParameter(Object name, Object template);
    Text noPathParam(Object name);
    Text noRequestBody(Object method, Object template);
    Text multipartOnlyAsMultipart(Object type);
    Text noBodyReader(Object consumes, Object method, Object template);
    Text invalidBody(Object type, Object cause);
    Text urlEncodedOnlyAsForm(Object type);
    Text malformedForm(Object cause);
    Text textPlainOnlyAsString(Object type);
    Text errorPagesFrozen();
    Text errorPageNotFound(Object resource);
    Text errorPageReadFailed(Object resource, Object cause);
    Text errorPageBackHome();

    // http
    Text invalidCookie(Object name);
    Text invalidHeader(Object name);
    Text notStreaming();
    Text noHttpRequest();
    Text noHttpSession(Object requestId);
    Text noSessionAttribute(Object key);
    Text sessionAttributeType(Object key, Object actual, Object expected);
    Text multipartNoBoundary();
    Text multipartMalformedDelimiter();
    Text multipartTruncated();
    Text multipartPartTooLarge(String cap);
    Text httpStatusOutOfRange(String code);
    Text httpServerStartFailed(String port, Object cause);
    Text malformedQuery(Object cause);

    // cache
    Text cachePopulateIncomplete(Object partition, Object errors);
    Text cacheNotFound(Object key);
    Text cachePopulateTimeout(Object partition);
    Text cacheReadInterrupted(Object partition);
    Text cacheWorkerNotTerminated(String seconds);
    Text cacheWorkerBusy(Object cause);
    Text cacheWorkerNotRunning();
    Text cacheRegionClosed(Object region);
    Text nativeCacheClosed();
    Text nativeCacheMiss();

    // html
    Text notAbsoluteUrl(Object value);
    Text leadingPlus(Object value);
    Text notAWeek(Object value);
    Text weekOutOfYear(String year, Object value);
    Text notAnEmail(Object value);
    Text notAColor(Object value);
    Text invalidValue(Object value, Object cause);
    Text rawContentClosingTag(Object tag);
    Text invalidAttributeName(Object name);
    Text invalidTagName(Object tag);
    Text fieldWithoutName();
    Text negativeLength(Object name);
    Text minLengthAboveMax(String min, String max, Object name);
    Text boundOnUnordered(Object value, Object type, Object name);
    Text stepNotApplicable(Object type, Object name);
    Text stepNotPositive(Object step);

    // serializer
    Text yamlRootNotMap(Object source, Object actual);
    Text yamlResource();
    Text unsupportedSerializer(Object type);
    Text keyMustBeText(Object key);
    Text cannotBindNull(Object type);
    Text unsupportedTargetType(Object type);
    Text buildFailed(Object type);
    Text serializeFailed(Object type);
    Text jsonUnexpectedContent(String pos);
    Text jsonExpectedCommaOrBrace(String pos);
    Text jsonExpectedCommaOrBracket(String pos);
    Text jsonInvalidEscape(Object escape);
    Text jsonInvalidLiteral(String pos);
    Text jsonExpectedChar(Object expected, String pos);
}
