package com.zhoujie.sms.rs232;

public interface CommInterface {
	void sendAT(ATCommand command);
	void registerDataAvailableListener(DataAvailableListener listener);
	void initialize(String port);
	void close();
}
