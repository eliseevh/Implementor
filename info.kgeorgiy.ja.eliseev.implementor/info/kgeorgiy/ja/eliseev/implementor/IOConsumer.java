package info.kgeorgiy.ja.eliseev.implementor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

/**
 * Analog of {@link Consumer}, but throws {@link IOException}.
 *
 * @param <T> the type of the input to the operation
 * @author Aleksandr Eliseev
 * @see Consumer
 */
@FunctionalInterface
public interface IOConsumer<T> {
    /**
     * Performs {@code consumer} action, and wraps {@link IOException} in {@link UncheckedIOException} if occurs.
     *
     * @param consumer action to perform
     * @param x        argument to {@code consumer.accept}
     * @param <T>      the type of argument
     * @throws UncheckedIOException if {@link IOException} occurs
     */
    static <T> void unchecked(final IOConsumer<? super T> consumer, final T x) {
        try {
            consumer.accept(x);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Gets {@link Consumer} corresponding to {@code consumer}.
     * Returned {@link Consumer#accept(Object)} would perform {@code consumer} action,
     * and wrap {@link IOException} in {@link UncheckedIOException} if occurs.
     *
     * @param consumer action to wrap
     * @param <T>      the type of the input to the operation
     * @return {@link Consumer} that wraps {@link IOException} in {@link UncheckedIOException}.
     */
    static <T> Consumer<T> makeUnchecked(final IOConsumer<? super T> consumer) {
        return x -> unchecked(consumer, x);
    }

    /**
     * Performs this operation on the given argument.
     *
     * @param x the input argument
     * @throws IOException if an I/O error occurs
     */
    void accept(final T x) throws IOException;
}
