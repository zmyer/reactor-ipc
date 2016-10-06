/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.ipc.socket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.Channel;
import reactor.ipc.binder.Connector;

/**
 * @author Stephane Maldini
 */
public final class SimpleClient extends SimplePeer {

	static final Scheduler scheduler =
			Schedulers.fromExecutorService(Executors.newCachedThreadPool(r -> {
				Thread t = new Thread(r, "test-client-pool");
				t.setDaemon(true);
				return t;
			}));

	static public SimpleClient create(InetAddress bindAddress, int port) {
		Objects.requireNonNull(bindAddress, "bindAddress");
		return new SimpleClient(bindAddress, port);
	}

	final int         port;
	final InetAddress address;

	SimpleClient(InetAddress address, int port) {
		this.port = port;
		this.address = address;
	}

	@Override
	public Mono<Void> newHandler(Function<? super Channel<byte[], byte[]>, ? extends Publisher<Void>> channelHandler) {
		return Mono.create(sink -> {
			Socket socket;

			try {
				socket = new Socket(address, port);
				sink.setCancellation(() -> {
					try {
						socket.close();
					}
					catch (IOException ex) {
						sink.error(ex);
					}
				});

				Publisher<Void> closing = channelHandler.apply(new SimpleChannel(socket));
				Flux.from(closing)
				    .subscribe(null,
						    t -> tryClose(socket, sink),
						    () -> tryClose(socket, sink));
			}
			catch (Throwable e) {
				sink.error(e);
			}
		});
	}

	@Override
	public Scheduler scheduler() {
		return scheduler;
	}

	void tryClose(Socket socket, MonoSink<Void> sink) {
		try {
			socket.close();
			sink.success();
		}
		catch (IOException e) {
			//IGNORE
		}
	}
}