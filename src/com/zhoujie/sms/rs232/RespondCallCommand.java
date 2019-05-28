package com.zhoujie.sms.rs232;

public class RespondCallCommand extends BaseATCommand {

	private String number;
	public RespondCallCommand(String answer, String number) {// ATA = answer, ATH = hang up
		super(answer);
		this.number = number;
	}

	@Override
	public void callback(String response, Boolean success) {
		super.callback(response, success);
		IncomingCallNotificationHandler.removeNumberInProcess(number);
	}
}
