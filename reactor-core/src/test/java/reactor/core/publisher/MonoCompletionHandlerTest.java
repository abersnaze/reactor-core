/*
 * Copyright (c) 2011-2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactor.core.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.channels.CompletionHandler;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;

import reactor.core.Scannable;
import reactor.test.StepVerifier;

public class MonoCompletionHandlerTest {
	@Test
	public void cancelThenFutureFails() {
		AtomicReference<Subscription> subRef = new AtomicReference<>();
		Consumer<CompletionHandler<Integer, Void>> api = (ch) -> {
			subRef.get().cancel();
			ch.failed(new IllegalStateException("boom"), null);
		};

		Mono<Integer> mono = Mono
				.fromCompletionHandler(api)
				.doOnSubscribe(subRef::set);

		StepVerifier.create(mono)
				.expectSubscription()
				// without the delay of zero the thenCancel causes the subscribe to abort before
				// the api is called.
				.expectNoEvent(Duration.ofMillis(0))
				.thenCancel()
				.verifyThenAssertThat()
				.hasDroppedErrorWithMessage("boom");
	}

	@Test
	public void cancelFutureImmediatelyCancelledLoop() {
		Consumer<CompletionHandler<Integer, Void>> api = (ch) -> {
		};
		for (int i = 0; i < 10000; i++) {
			CompletableFuture<Integer> future = new CompletableFuture<>();
			Mono<Integer> mono = Mono
					.fromCompletionHandler(api)
					.doFinally(sig -> {
						if (sig == SignalType.CANCEL)
							future.cancel(false);
					});

			StepVerifier.create(mono)
					.expectSubscription()
					.thenCancel()
					.verifyThenAssertThat()
					.hasNotDroppedErrors();

			assertThat(future).isCancelled();
		}
	}

	@Test
	public void fromCompletableFuture() {
		Consumer<CompletionHandler<String, Void>> api = (ch) -> {
			ch.completed("helloHandler", null);
		};

		assertThat(Mono.fromCompletionHandler(api)
				.block()).isEqualToIgnoringCase("helloHandler");
	}

	@Test
	public void lateFailureIsPropagatedDirectly() {
		Throwable expected = new IllegalStateException("boom");
		AtomicReference<CompletionHandler<Integer, Void>> handlerRef = new AtomicReference<>();
		Consumer<CompletionHandler<Integer, Void>> api = (ch) -> {
			handlerRef.set(ch);
		};

		Mono.fromCompletionHandler(api)
				.as(StepVerifier::create)
				.then(() -> handlerRef.get().failed(expected, null))
				.verifyErrorSatisfies(e -> assertThat(e).isSameAs(expected));
	}

	@Test
	public void actionFailureCompletionExceptionIsUnwrapped() {
		Consumer<CompletionHandler<Integer, Void>> api = (ch) -> {
			throw new IllegalStateException("boom");
		};

		Mono.fromCompletionHandler(api)
				.as(StepVerifier::create)
				.verifyError(IllegalStateException.class);
	}
}
