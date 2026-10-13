package metaheuristic.listener;

import metaheuristic.run.SearchListener;

import java.nio.file.Path;

/**
 * Factory methods for common listeners:
 * <pre>
 * Runner.of(sa).listener(Listeners.throttled(Listeners.console(), 1000))
 * </pre>
 */
public final class Listeners {

    private Listeners() {
    }

    public static SearchListener console() {
        return new ConsoleProgressListener();
    }

    public static SearchListener csv(Path file) {
        return new CsvProgressListener(file);
    }

    public static SearchListener throttled(SearchListener listener, long intervalMillis) {
        return new ThrottledListener(listener, intervalMillis);
    }

    // one per run: use with listenerPerRun
    public static AsyncListener async(SearchListener listener) {
        return new AsyncListener(listener);
    }
}
