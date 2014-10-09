package com.zhoujie.sms.rs232;

import java.util.logging.Logger;

public class BaseATCommand implements ATCommand {
	
	protected String command;
	private final static Logger logger = Logger.getLogger(BaseATCommand.class.getName());

	public BaseATCommand(String command) {
		super();
		this.command = command;
	}

	@Override
	public void callback(String response, Boolean success) {
		logger.info(Util.toHex(response));
	}

	@Override
	public String getCommand() {
		return command;
	}

}
