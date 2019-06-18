package com.zhoujie.sms.mail;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailServer {
	private final static Logger logger = Logger.getLogger(EmailServer.class.getSimpleName());
	private static Map<String, EmailServer> cache = new HashMap<>();
	private Properties props;

	public static EmailServer getMailer(String provider) {
		if (!cache.containsKey(provider)) {
			cache.put(provider, new EmailServer(provider));
		}
		return cache.get(provider);
	}
	
	private EmailServer(String provider) {
		try {
			props = new Properties();
			props.load(new FileInputStream(provider + ".properties"));
			//props.load(new FileInputStream(provider + ".properties"));
		} catch(IOException e) {
			throw new RuntimeException("No properties file for mail service provider: " + provider);
		}
	}


	public void sendAsync(final String msg, final String title, final String recipient) {
		(new Thread() {
			public void run() {
				send(msg, title, recipient);
			}
		}).start();
	}

	public void send(String msg, String title, String recipient) {
		String credential = props.getProperty("mail.smtp.user");
		Session session = Session.getInstance(props, new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(credential, props.getProperty("mail.smtp.password"));
			}
		});

		try {
			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(credential));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
			message.setSubject(title);
			message.setText(msg);
			Transport.send(message);
		} catch (MessagingException e) {
			logger.severe(e.toString());
		}
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 4) {
			usage();
		}
		if (args[0].equals("gmail") || args[0].equals("hotmail") || args[0].equals("yahoo")) {
			new EmailServer(args[0]).send(args[3], args[2], args[1]);
		} else {
			usage();
		}
	}

	private static void usage() {
		System.out.println("java EmailServer [gmail|hotmail|yahoo] address subject content");
		System.exit(1);
	}

}
