package br.bundled.money.business._0_domain.repository;

import br.bundled.money.business._0_domain.model.Book;
import br.commons.Result;
import org.jspecify.annotations.NullMarked;

/** The book's own data ({@link Book}); {@link Book#OPEN} until something is saved. */
@NullMarked
public interface BookRepository {

    Book find();
    Result<Book, String> save(Book book);

}
