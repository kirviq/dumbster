/*
 * Dumbster - a dummy SMTP server
 * Copyright 2016 Joachim Nicolay
 * Copyright 2004 Jason Paul Kitchen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dumbster.smtp;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

/** Dummy SMTP server for testing purposes. */
@Slf4j
public final class SimpleSmtpServer implements AutoCloseable {

	/** Default SMTP port is 25. */
	public static final int DEFAULT_SMTP_PORT = 25;

	/** pick any free port. */
	public static final int AUTO_SMTP_PORT = 0;

	/** When stopping wait this long for any still ongoing transmission */
	private static final int STOP_TIMEOUT = 20000;

	private static final Pattern CRLF = Pattern.compile("\r\n");

    /**
     * Store and offer received emails with a {@link Queue}.
     * Implementation uses a unbounded and thread-safe {@link ConcurrentLinkedQueue}
     */
	private final Queue<SmtpMessage> receivedMail = new ConcurrentLinkedQueue<>();

	/** The server socket this server listens to. */
	private final ServerSocket serverSocket;

	/** Thread that does the work. */
	private final Thread workerThread;

	/** Indicates the server thread that it should stop */
	private volatile boolean stopped = false;

	/**
	 * Creates an instance of a started SimpleSmtpServer.
	 *
	 * @param port port number the server should listen to
	 * @return a reference to the running SMTP server
	 * @throws IOException when listening on the socket causes one
	 */
	public static SimpleSmtpServer start(int port) throws IOException {
		return new SimpleSmtpServer(new ServerSocket(Math.max(port, 0)));
	}

	/**
	 * private constructor because factory method {@link #start(int)} better indicates that
	 * the created server is already running
	 * @param serverSocket socket to listen on
	 */
	private SimpleSmtpServer(ServerSocket serverSocket) {
		this.serverSocket = serverSocket;
		this.workerThread = new Thread(
				new Runnable() {
					@Override
					public void run() {
						performWork();
					}
				});
		this.workerThread.start();
	}

    /**
     * All received email stored in a thread-safe queue
     * @return all received email stored in a thread-safe queue
     */
    public Queue<SmtpMessage> getReceivedMail() {
        return receivedMail;
    }

    /**
	 * @return the port the server is listening on
	 */
	public int getPort() {
		return serverSocket.getLocalPort();
	}


	/**
	 * Stops the server. Server is shutdown after processing of the current request is complete.
	 */
	public void stop() {
		if (stopped) {
			return;
		}
		// Mark us closed
		stopped = true;
		try {
			// Kick the server accept loop
			serverSocket.close();
		} catch (IOException e) {
			log.warn("trouble closing the server socket", e);
		}
		// and block until worker is finished
		try {
			workerThread.join(STOP_TIMEOUT);
		} catch (InterruptedException e) {
			log.warn("interrupted when waiting for worker thread to finish", e);
		}
	}

	/**
	 * synonym for {@link #stop()}
	 */
	@Override
	public void close() {
		stop();
	}

	/**
	 * Main loop of the SMTP server.
	 */
	private void performWork() {
		try {
			// Server: loop until stopped
			while (!stopped) {
				// Start server socket and listen for client connections
				//noinspection resource
				try (Socket socket = serverSocket.accept();
				     Scanner input = new Scanner(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1)).useDelimiter(CRLF);
				     PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1));) {

						/*
						 * We synchronize over the handle method and the list update because the client call completes inside
						 * the handle method and we have to prevent the client from reading the list until we've updated it.
						 */
						receivedMail.addAll(handleTransaction(out, input));

				}
			}
		} catch (Exception e) {
			// SocketException expected when stopping the server
			if (!stopped) {
				log.error("hit exception when running server", e);
				try {
					serverSocket.close();
				} catch (IOException ex) {
					log.error("and one when closing the port", ex);
				}
			}
		}
	}

	/**
	 * Handle an SMTP transaction, i.e. all activity between initial connect and QUIT command.
	 *
	 * @param out   output stream
	 * @param input input stream
	 * @return List of SmtpMessage
	 * @throws IOException
	 */
	private static List<SmtpMessage> handleTransaction(PrintWriter out, Iterator<String> input) throws IOException {
		// Initialize the state machine
		SmtpState smtpState = SmtpState.CONNECT;
		SmtpRequest smtpRequest = new SmtpRequest(SmtpActionType.CONNECT, "", smtpState);

		// Execute the connection request
		SmtpResponse smtpResponse = smtpRequest.execute();

		// Send initial response
		sendResponse(out, smtpResponse);
		smtpState = smtpResponse.getNextState();

		List<SmtpMessage> msgList = new ArrayList<>();
		SmtpMessage msg = new SmtpMessage();

		while (smtpState != SmtpState.CONNECT) {
			String line = input.next();

			if (line == null) {
				break;
			}

			// Create request from client input and current state
			SmtpRequest request = SmtpRequest.createRequest(line, smtpState);
			// Execute request and create response object
			SmtpResponse response = request.execute();
			// Move to next internal state
			smtpState = response.getNextState();
			// Send response to client
			sendResponse(out, response);

			// Store input in message
			String params = request.params;
			msg.store(response, params);

			// If message reception is complete save it
			if (smtpState == SmtpState.QUIT) {
				msgList.add(msg);
				msg = new SmtpMessage();
			}
		}

		return msgList;
	}

	/**
	 * Send response to client.
	 *
	 * @param out          socket output stream
	 * @param smtpResponse response object
	 */
	private static void sendResponse(PrintWriter out, SmtpResponse smtpResponse) {
		if (smtpResponse.getCode() > 0) {
			int code = smtpResponse.getCode();
			String message = smtpResponse.getMessage();
			out.print(code + " " + message + "\r\n");
			out.flush();
		}
	}
}
