package reactor.core.publisher;

import java.nio.channels.CompletionHandler;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;

import reactor.core.CoreSubscriber;
import reactor.core.Scannable;

/**
 * Wrapper for invoking methods that take a {@link CompletionHandler}.
 * <p>
 * Note that if Subscribers cancel their subscriptions, the execution is not
 * cancelled.
 *
 * @param <T> the value type
 * @param <A> the attachment type
 */
public class MonoCompletionHandler<T, A> extends Mono<T> implements Scannable {
	private static final String ATTACHMENT_KEY = "reactor.completionHandler.attachment";

	private final A attachment;
	private final BiConsumer<A, CompletionHandler<T, A>> execute;

	public MonoCompletionHandler(A attachment, BiConsumer<A, CompletionHandler<T, A>> execute) {
		this.attachment = attachment;
		this.execute = execute;
	}

	@Override
	public void subscribe(CoreSubscriber<? super T> actual) {
		Operators.MonoSubscriber<T, T> sub = new Operators.MonoSubscriber<>(actual);

		actual.onSubscribe(sub);
		if (sub.isCancelled()) {
			return;
		}

		CompletionHandler<T, A> handler = new CompletionHandler<T, A>() {
			@Override
			public void completed(T result, A attachment) {
				if (sub.isCancelled()) {
					Operators.onDiscard(result, sub.currentContext());
					return;
				}
				if (attachment != null) {
					sub.currentContext().put(ATTACHMENT_KEY, attachment);
				}
				sub.complete(result);
			}

			@Override
			public void failed(Throwable err, A attachment) {
				if (sub.isCancelled()) {
					// nobody is interested in the Mono anymore, don't risk dropping errors
					if (!(err instanceof CancellationException)) {
						Operators.onErrorDropped(err, sub.currentContext());
					}
					return;
				}
				if (attachment != null) {
					sub.currentContext().put(ATTACHMENT_KEY, attachment);
				}
				Operators.error(sub, err);
			}
		};

		execute.accept(attachment, handler);
	}

	@Override
	public Object scanUnsafe(Attr key) {
		return null; // no particular key to be represented, still useful in hooks
	}
}
