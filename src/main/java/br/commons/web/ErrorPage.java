package br.commons.web;

import br.commons.http.HTTPStatus;
import br.commons.http.message.HTTPRequest;
import org.jspecify.annotations.NullMarked;

/**
 * HTML page shown to a browser in place of an error response of a given status. Returns only the markup:
 * status, headers and {@code Content-Type} are set by whoever renders it, so the page never changes the status.
 */
@NullMarked
@FunctionalInterface
public interface ErrorPage {

    String html(HTTPRequest request, HTTPStatus status);

}
