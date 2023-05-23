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

import lombok.val;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedList;

/**
 * Container for a complete SMTP message - headers and message body.
 */
public class SmtpMessage {
	/** Headers: Map of List of String hashed on header name. */
	private Map<String, List<String>> headers;
	/** Message body. */
	private StringBuilder body;
	/** Recipients (read from envelope) */
	private List<String> recipients;

	/** Constructor. Initializes headers Map and body buffer. */
	public SmtpMessage() {
		headers = new LinkedHashMap<>(10);
		body = new StringBuilder();
		recipients = new LinkedList<>();
	}

	/**
	 * Update the headers or body depending on the SmtpResponse object and line of input.
	 *
     * @param request  SmtpRequest object
     * @param response SmtpResponse object
     */
	public void store(SmtpRequest request, SmtpResponse response) {
		String params = request.getParams();
		if (params != null) {
			// switch over next state
			if (SmtpState.DATA_HDR.equals(response.getNextState())) {
				int headerNameEnd = params.indexOf(':');
				if (headerNameEnd >= 0) {
					String name = params.substring(0, headerNameEnd).trim();
					String value = params.substring(headerNameEnd + 1).trim();
					addHeader(name, value);
				}
			} else if (SmtpState.DATA_BODY == response.getNextState()) {
				body.append(params);
			}

			// switch over action
			if (SmtpActionType.RCPT == request.getAction()) {
				recipients.add(params);
			}
		}
	}

	/**
	 * @return a List of the recipients of this message (from the SMTP envelope)
	 */
	public List<String> getRecipients() {
		return Collections.unmodifiableList(recipients);
	}

	public List<String> getToRecipients() {
		val toRecipientsFromHeader = getHeaderValues("To");
		return recipientEmailAddresses(toRecipientsFromHeader);
	}

	public List<String> getCcRecipients() {
		val toRecipientsFromHeader = getHeaderValues("Cc");
		return recipientEmailAddresses(toRecipientsFromHeader);
	}

	private List<String> recipientEmailAddresses(List<String> recipientsFromHeader) {
		val allRecipients = Collections.unmodifiableList(recipients);
		final List<String> toRecipients = new ArrayList<>();
		for (String recipient : recipientsFromHeader) {
			final String recipientFormat = emailToRecipientFormat(recipient);
			if (allRecipients.contains(recipientFormat)) {
				toRecipients.add(recipientFormat);
			}
		}
		return toRecipients;
	}

	public List<String> getBccRecipients() {
		val recipientsToReturn = new ArrayList<>(recipients);
		final List<String> toAndCcRecipients = new ArrayList<>();
		toAndCcRecipients.addAll(getToRecipients());
		toAndCcRecipients.addAll(getCcRecipients());
		recipientsToReturn.removeAll(toAndCcRecipients);
		return recipientsToReturn;
	}

	private String emailToRecipientFormat(final String email) {
		return "<" + email + ">";
	}

	/**
	 * Get an Iterator over the header names.
	 *
	 * @return an Iterator over the set of header names (String)
	 */
	public Set<String> getHeaderNames() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(headers.keySet()));
	}

	/**
	 * Get the value(s) associated with the given header name.
	 *
	 * @param name header name
	 * @return value(s) associated with the header name
	 */
	public List<String> getHeaderValues(String name) {
		List<String> values = headers.get(name);
		if (values == null || values.isEmpty()) {
			return Collections.emptyList();
		} else {
			return Collections.unmodifiableList(new ArrayList<>(values));
		}
	}

	/**
	 * Get the first values associated with a given header name.
	 *
	 * @param name header name
	 * @return first value associated with the header name
	 */
	public String getHeaderValue(String name) {
		List<String> values = headers.get(name);
		if (values == null) {
			return null;
		} else {
			return values.get(0);
		}
	}

	/**
	 * Get the message body.
	 *
	 * @return message body
	 */
	public String getBody() {
		return body.toString();
	}

	/**
	 * Adds a header to the Map.
	 *
	 * @param name  header name
	 * @param value header value
	 */
	private void addHeader(String name, String value) {
		List<String> valueList = headers.get(name);
		if (valueList == null) {
			valueList = new ArrayList<>(1);
			headers.put(name, valueList);
		}
		valueList.add(value);
	}

	/**
	 * String representation of the SmtpMessage.
	 *
	 * @return a String
	 */
	@Override
	public String toString() {
		StringBuilder msg = new StringBuilder();
		for (Map.Entry<String, List<String>> stringListEntry : headers.entrySet()) {
			for (String value : stringListEntry.getValue()) {
				msg.append(stringListEntry.getKey());
				msg.append(": ");
				msg.append(value);
				msg.append('\n');
			}
		}
		msg.append('\n');
		msg.append(body);
		msg.append('\n');
		return msg.toString();
	}
}
