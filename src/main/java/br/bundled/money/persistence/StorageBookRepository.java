package br.bundled.money.persistence;

import br.bundled.money.business._0_domain.model.Book;
import br.bundled.money.business._0_domain.repository.BookRepository;
import br.commons.Result;
import br.commons.cdi.Context;
import br.commons.persistence.Storage;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Optional;

/** The book's own data in {@value #FILE}, field {@value #KEY}: the closing date ({@code yyyy-MM-dd} or null). */
@NullMarked
public final class StorageBookRepository implements BookRepository {

    static final String FILE = "book.json";
    static final String KEY = "book";

    record BookRow(@Nullable String threshold) {}

    private final Storage storage = Context.get(Storage.class);
    private volatile Book book = Book.OPEN;

    public synchronized Result<Void, String> load() {
        book = Book.OPEN;
        val found = storage.read(FILE, KEY);
        if (found instanceof Result.Failure<Optional<byte[]>, String>(var error)) return Result.failure(error);
        if (found.getOrThrow().isEmpty()) return Result.success();
        val read = Records.read(storage, FILE, KEY, BookRow.class);
        if (read instanceof Result.Failure<BookRow, String>(var error)) return Result.failure(error);
        val threshold = read.getOrThrow().threshold();
        book = new Book(threshold == null ? null : LocalDate.parse(threshold));
        return Result.success();
    }

    @Override
    public Book find() {
        return book;
    }

    @Override
    public synchronized Result<Book, String> save(Book book) {
        val threshold = book.threshold();
        return Records.write(storage, FILE, KEY, new BookRow(threshold == null ? null : threshold.toString())).map(_ -> {
            this.book = book;
            return book;
        });
    }
}
