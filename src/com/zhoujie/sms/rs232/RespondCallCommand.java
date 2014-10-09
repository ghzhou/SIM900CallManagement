package com.zhoujie.sms.rs232;

public class RespondCallCommand extends BaseATCommand {

	public RespondCallCommand(String answer) {// ATA = answer, ATH = hang up
		super(answer);
	}

	@Override
	public void callback(String response, Boolean success) {
		super.callback(response, success);
		IncomingCallNotificationHandler.setNumberInProcess("");
	}
}
