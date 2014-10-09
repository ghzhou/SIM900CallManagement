package com.zhoujie.sms.rs232;

import java.io.IOException;

public class SendATCommandThread extends Thread {
	
	private ATCommand command;

	public SendATCommandThread(ATCommand command) {
		super();
		this.command = command;
	}

	@Override
	public void run() {
		try {
			CommController.getInstance().sendAT(command);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
