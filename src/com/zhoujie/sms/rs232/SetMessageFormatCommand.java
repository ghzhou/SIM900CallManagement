package com.zhoujie.sms.rs232;

public class SetMessageFormatCommand extends BaseATCommand {
	public SetMessageFormatCommand(int format) {
		super("AT+CMGF="+format);
	}
}
