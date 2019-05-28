package com.zhoujie.sms.rs232;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NewMessageNotificationHandler implements NotificationHandler {
	
	private final static Logger logger = Logger.getLogger(NewMessageNotificationHandler.class.getName());

	@Override
	public void handle(Matcher m) {
		int msgId = Integer.parseInt(m.group(1));
		logger.info("Got a new message, ID: "+msgId);
		new SendATCommandThread(new RetrieveMessageCommand(msgId)).start();
	}	
}
