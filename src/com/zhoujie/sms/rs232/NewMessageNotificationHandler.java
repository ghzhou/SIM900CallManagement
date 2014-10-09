package com.zhoujie.sms.rs232;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NewMessageNotificationHandler extends BaseNotificationHandler {
	
	private final static Logger logger = Logger.getLogger(NewMessageNotificationHandler.class.getName());

	@Override
	public boolean handle(String data) {
		Pattern p = Pattern.compile("\\+CMTI\\: \"SM\",(\\d+)");
		Matcher m = p.matcher(data);
		if (m.find()) {
			int msgId = Integer.parseInt(m.group(1));
			logger.info("Got a new message, ID: "+msgId);
			new SendATCommandThread(new RetrieveMessageCommand(msgId)).start();
			return true;
		} else {
			return false;
		}
	}	

}
