package com.gimp.requests;

import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.Polling;
import io.socket.engineio.client.transports.WebSocket;
import java.net.*;
import io.socket.client.IO;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@Slf4j
public class GIMPSocketClient extends GIMPRequestClient
{
	@Getter(AccessLevel.PACKAGE)
	public Socket client;

	/**
	 * Connects the socket to the server at the base URL, using default config
	 * for the connection. On connection, sets up socket listeners for socket
	 * lifecycle events, e.g. connect, disconnect, connect_error.
	 */
	public void connect()
	{
		URI uri = URI.create(getBaseUrl());
		IO.Options options = IO.Options.builder()
			// IO factory options
			.setForceNew(false)
			.setMultiplex(true)

			// low-level engine options
			.setTransports(new String[]{Polling.NAME, WebSocket.NAME})
			.setUpgrade(true)
			.setRememberUpgrade(false)
			.setPath("/socket.io/")
			.setQuery(null)
			.setExtraHeaders(null)

			// Manager options
			.setReconnection(true)
			.setReconnectionAttempts(Integer.MAX_VALUE)
			.setReconnectionDelay(1_000)
			.setReconnectionDelayMax(5_000)
			.setRandomizationFactor(0.5)
			.setTimeout(20_000)

			// Socket options
			.setAuth(null)
			.build();
		client = IO.socket(uri, options);
		client.connect();

		client.on(Socket.EVENT_CONNECT, new Emitter.Listener()
		{
			@Override
			public void call(Object... args)
			{
				log.debug(client.id() + " connected");
			}
		});

		client.on(Socket.EVENT_DISCONNECT, new Emitter.Listener()
		{
			@Override
			public void call(Object... args)
			{
				log.debug("Socket disconnected");
			}
		});

		client.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener()
		{
			@Override
			public void call(Object... args)
			{
				log.warn("Failed to connect to socket server, closing");
				client.close();
			}
		});
	}

	/**
	 * Disconnects the client from the socket server.
	 */
	public void disconnect()
	{
		client.disconnect();
	}

	/**
	 * Checks if the client is connected to a socket server.
	 *
	 * @return whether socket is connected
	 */
	public boolean isConnected()
	{
		return client.connected();
	}

	/**
	 * Sends a socket message to the ping listener. Excepts an acknowledgement
	 * from the server, and returns the JSON data in that acknowledgement. Times
	 * out after seconds.
	 *
	 * @return acknowledgement data in JSON
	 * @throws ExecutionException   for unexpected socket error
	 * @throws InterruptedException if acknowledgement is interrupted
	 * @throws TimeoutException     if acknowledgement times out
	 */
	public String ping() throws ExecutionException, InterruptedException, TimeoutException
	{
		String EVENT_PING = "ping";
		CompletableFuture<String> socketResponse = new CompletableFuture<>();
		client.emit(EVENT_PING, new Ack()
		{
			@Override
			public void call(Object... args)
			{
				JSONObject data = (JSONObject) args[0];
				socketResponse.complete(data.toString());
			}
		});
		String data = socketResponse.get(5, TimeUnit.SECONDS);
		log.debug(data);
		return data;
	}

	/**
	 * Sends a socket message to the broadcast listener. Passes the JSON data
	 * as the data parameter and expects an acknowledgement from the server.
	 *
	 * @param dataJson emit data in JSON
	 * @throws ExecutionException   for unexpected socket error
	 * @throws InterruptedException if acknowledgement is interrupted
	 * @throws TimeoutException     if acknowledgement times out
	 */
	public void broadcast(String dataJson) throws ExecutionException, InterruptedException, TimeoutException
	{
		String EVENT_BROADCAST = "broadcast";
		CompletableFuture<String> socketResponse = new CompletableFuture<>();
		client.emit(EVENT_BROADCAST, dataJson, new Ack()
		{
			@Override
			public void call(Object... args)
			{
				JSONObject data = (JSONObject) args[0];
				socketResponse.complete(data.toString());
			}
		});
		String data = socketResponse.get(5, TimeUnit.SECONDS);
		log.debug(data);
	}
}