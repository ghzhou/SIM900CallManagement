package com.zhoujie.sms.rs232;

import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zhoujie.sms.data.ShortMessage;
import com.zhoujie.sms.mail.EmailServer;

public class RetrieveAllMessagesCommand extends BaseATCommand {
	
	private final static Logger logger = Logger.getLogger(RetrieveAllMessagesCommand.class.getSimpleName());

	public RetrieveAllMessagesCommand() {
		super("AT+CMGL=4");
	}

	@Override
	public void callback(String response, Boolean success) {
		String[] msgs = response.split("\\+CMGL");
		StringBuilder sb = new StringBuilder();
		int numOfMsgs=0;
		for (String msg: msgs){
			Pattern p=Pattern.compile("\\,(\\d+)\r\n(.*)\r\n");
			Matcher m=p.matcher(msg);
			if(m.find()){
				logger.info("Message Len:"+m.group(1));
				logger.info("Message payload:"+m.group(2));
				int msgLength=Integer.parseInt(m.group(1));
				Optional<ShortMessage> messageOption = ShortMessage.parsePDU(m.group(2),msgLength);
				if (messageOption.isPresent()) {
					ShortMessage sm = messageOption.get();
					sb.append("Sender:");
					sb.append(sm.getSender());
					sb.append("\n");
					sb.append("Time:");
					sb.append(sm.getTimeOfReceive());
					sb.append("\n");
					sb.append("Body:");
					sb.append(sm.getBody());
					sb.append("\n");
					sb.append("\n");
					sb.append("\n");
					numOfMsgs++;
				};
			}
			else{
				logger.fine("PDU format error,no length or content");
				//throw new RuntimeException("PDU format error, no length or content");
			}
		}
		if (numOfMsgs>0){
			EmailServer.getMailer("yahoo").send(sb.toString(), "SMS archive # "+msgs.length, "zhou_jack@live.com");
			// issue delete all sms command
			new SendATCommandThread(new BaseATCommand("AT+CMGD=0,4")).start();
		}

	}
}
