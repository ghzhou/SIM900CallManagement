package com.zhoujie.sms.rs232;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zhoujie.sms.data.ShortMessage;
import com.zhoujie.sms.mail.EmailServer;



public class RetrieveMessageCommand extends BaseATCommand {
	
	private int msgIndex;
	private final static Logger logger = Logger.getLogger(RetrieveMessageCommand.class.getName());
	private static ShortMessage sm = new ShortMessage();
	

	public RetrieveMessageCommand(int msgIndex) {
		super("AT+CMGR="+msgIndex);
		this.msgIndex = msgIndex;
	}
	
	@Override
	public void callback(String response, Boolean success) {
		if (success==null || !success){
			super.callback(response, success);
			return;
		}
		logger.info("parsing the msg: "+msgIndex);
		Pattern p=Pattern.compile("\\,(\\d+)\r\n(.*)\r\n");
		Matcher m=p.matcher(response);
		if(m.find()){
			logger.info("Message Len:"+m.group(1));
			logger.info("Message payload:"+m.group(2));
			int msgLength=Integer.parseInt(m.group(1));
			if (ShortMessage.parsePDU(sm, m.group(2),msgLength)) {
				EmailServer.getMailer("yahoo").send(sm.getBody(),"SMS from:" + sm.getSender() + " on " + sm.getTimeOfReceive(),"zhou_jack@live.com");
				//EmailServer.getHotmail().send(sm.getBody(),"SMS from:" + sm.getSender() + " on " + sm.getTimeOfReceive(),"zhou_jack@live.cn");
				sm = new ShortMessage();
			}
			// issue delete sms command
			new SendATCommandThread(new BaseATCommand("AT+CMGD="+msgIndex)).start();
		}
		else{
			logger.fine("PDU format error,no length or content");
			//throw new RuntimeException("PDU format error, no length or content");
		}
		
	}
}
