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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.*;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class SimpleSmtpServerTest {

	private SimpleSmtpServer server;

	@Before
	public void setUp() throws Exception {
		server = SimpleSmtpServer.start(SimpleSmtpServer.AUTO_SMTP_PORT);
	}

	@After
	public void tearDown() throws Exception {
		server.stop();
	}

	@Test
	public void testSend() throws MessagingException {
		sendMessage(server.getPort(), "sender@here.com", "Test", "Test Body\r\nNew sentence", "receiver@there.com");

		List<SmtpMessage> emails = server.getReceivedEmails();
		assertThat(emails, hasSize(1));
		SmtpMessage email = emails.get(0);
		assertThat(email.getHeaderValue("Subject"), is("Test"));
		assertThat(email.getBody(), is("Test Body\r\nNew sentence"));
		assertThat(email.getHeaderNames(), hasItem("Date"));
		assertThat(email.getHeaderNames(), hasItem("From"));
		assertThat(email.getHeaderNames(), hasItem("To"));
		assertThat(email.getHeaderNames(), hasItem("Subject"));
		assertThat(email.getHeaderValues("To"), contains("receiver@there.com"));
		assertThat(email.getHeaderValue("To"), is("receiver@there.com"));
	}

	@Test
	public void testSendAndReset() throws MessagingException {
		sendMessage(server.getPort(), "sender@here.com", "Test", "Test Body", "receiver@there.com");
		assertThat(server.getReceivedEmails(), hasSize(1));

		server.reset();
		assertThat(server.getReceivedEmails(), hasSize(0));

		sendMessage(server.getPort(), "sender@here.com", "Test", "Test Body", "receiver@there.com");
		assertThat(server.getReceivedEmails(), hasSize(1));
	}

	@Test
	public void testSendMessageWithCR() throws MessagingException {
		sendMessage(server.getPort(), "sender@hereagain.com", "CRTest", "\n\nKeep these pesky carriage returns\n\n", "receivingagain@there.com");

		List<SmtpMessage> emails = server.getReceivedEmails();
		assertThat(emails, hasSize(1));
		SmtpMessage email = emails.get(0);
		assertEquals("\r\n\r\nKeep these pesky carriage returns\r\n\r\n", email.getBody());
	}

	@Test
	public void testSendTwoMessagesSameConnection() throws MessagingException {
		MimeMessage[] mimeMessages = new MimeMessage[2];
		Properties mailProps = getMailProperties(server.getPort());
		Session session = Session.getInstance(mailProps, null);

		mimeMessages[0] = createMessage(session, "sender@whatever.com", "receiver@home.com", "Doodle1", "Bug1");
		mimeMessages[1] = createMessage(session, "sender@whatever.com", "receiver@home.com", "Doodle2", "Bug2");

		Transport transport = session.getTransport("smtp");
		transport.connect("localhost", server.getPort(), null, null);

		for (int i = 0; i < mimeMessages.length; i++) {
			MimeMessage mimeMessage = mimeMessages[i];
			transport.sendMessage(mimeMessage, mimeMessage.getAllRecipients());
		}

		transport.close();

		assertThat(server.getReceivedEmails(), hasSize(2));
	}

	@Test
	public void testSendTwoMsgsWithLogin() throws Exception {
		String serverHost = "localhost";
		String from = "sender@here.com";
		String to = "receiver@there.com";
		String subject = "Test";
		String body = "Test Body";

		Properties props = System.getProperties();

		if (serverHost != null) {
			props.setProperty("mail.smtp.host", serverHost);
		}

		Session session = Session.getDefaultInstance(props, null);
		Message msg = new MimeMessage(session);

		if (from != null) {
			msg.setFrom(new InternetAddress(from));
		} else {
			msg.setFrom();
		}

		InternetAddress.parse(to, false);
		msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
		msg.setSubject(subject);

		msg.setText(body);
		msg.setHeader("X-Mailer", "musala");
		msg.setSentDate(new Date());
		msg.saveChanges();

		Transport transport = null;

		try {
			transport = session.getTransport("smtp");
			transport.connect(serverHost, server.getPort(), "ddd", "ddd");
			transport.sendMessage(msg, InternetAddress.parse(to, false));
			transport.sendMessage(msg, InternetAddress.parse("dimiter.bakardjiev@musala.com", false));
		} finally {
			if (transport != null) {
				transport.close();
			}
		}

		List<SmtpMessage> emails = this.server.getReceivedEmails();
		assertThat(emails, hasSize(2));
		SmtpMessage email = emails.get(0);
		assertTrue(email.getHeaderValue("Subject").equals("Test"));
		assertTrue(email.getBody().equals("Test Body"));
	}

	private Properties getMailProperties(int port) {
		Properties mailProps = new Properties();
		mailProps.setProperty("mail.smtp.host", "localhost");
		mailProps.setProperty("mail.smtp.port", "" + port);
		mailProps.setProperty("mail.smtp.sendpartial", "true");
		return mailProps;
	}


	private void sendMessage(int port, String from, String subject, String body, String to) throws MessagingException {
		Properties mailProps = getMailProperties(port);
		Session session = Session.getInstance(mailProps, null);
		//session.setDebug(true);

		MimeMessage msg = createMessage(session, from, to, subject, body);
		Transport.send(msg);
	}

	private MimeMessage createMessage(
			Session session, String from, String to, String subject, String body) throws MessagingException {
		MimeMessage msg = new MimeMessage(session);
		msg.setFrom(new InternetAddress(from));
		msg.setSubject(subject);
		msg.setSentDate(new Date());
		msg.setText(body);
		msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
		return msg;
	}
}
