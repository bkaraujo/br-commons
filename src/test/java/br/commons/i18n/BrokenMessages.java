package br.commons.i18n;

import br.commons.locale.i18n.Text;

/** Bundle whose file is incomplete: a missing key, a wrong placeholder, an orphan and a wrong return type. */
public interface BrokenMessages {

    Text greeting(String name);

    Text missing();

    String notText();
}
