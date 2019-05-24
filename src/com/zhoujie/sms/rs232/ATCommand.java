package com.zhoujie.sms.rs232;

public interface ATCommand {
	void callback(String response, Boolean success);
	String getCommand();
}
