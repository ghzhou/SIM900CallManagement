package com.zhoujie.sms.rs232;

import java.util.logging.Logger;



public class BaseNotificationHandler implements NotificationHandler {

	private final static Logger logger = Logger.getLogger(BaseNotificationHandler.class.getName());

	@Override
	public boolean handle(String buffer) {
		logger.info(Util.toHex(buffer));
		return true;
	}

}
