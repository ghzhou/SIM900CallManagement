package com.zhoujie.sms.rs232;

import java.util.regex.Matcher;

public interface NotificationHandler {
	void handle(Matcher mather);
}
