package com.zhoujie.sms.rs232;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pi4j.system.NetworkInfo;
import com.zhoujie.sms.mail.EmailServer;

public class CommController implements DataAvailableListener {

	private enum STATE {
		IDLE, AWAITING_RESPONSE
	};

	private final static Logger logger = Logger.getLogger(CommController.class.getName());
	private static Map<String, ATCommand> supportedCommands = new HashMap<String, ATCommand>();
	static {
		supportedCommands.put("test", new BaseATCommand("ATI"));
		supportedCommands.put("msg", new RetrieveAllMessagesCommand());
		supportedCommands.put("forwardReasons", new BaseATCommand("AT+CCFC=?"));
		supportedCommands.put("forwardStatusUnconditional", new BaseATCommand("AT+CCFC=0,2"));
		supportedCommands.put("forwardStatusBusy", new BaseATCommand("AT+CCFC=1,2"));
		supportedCommands.put("forwardStatusNoReply", new BaseATCommand("AT+CCFC=2,2"));
		supportedCommands.put("forwardStatusNotReachable", new BaseATCommand("AT+CCFC=3,2"));
//		supportedCommands.put("forwardStatusAll", new BaseATCommand("AT+CCFC=4,2"));
//		supportedCommands.put("forwardStatusAllConditional", new BaseATCommand("AT+CCFC=5,2"));
		supportedCommands.put("forwardUnconditional", new BaseATCommand("at+ccfc=0,3,\"+8618121305895\",145,1"));
		supportedCommands.put("forwardBusy", new BaseATCommand("at+ccfc=1,3,\"+8618121305895\",145,1"));
		supportedCommands.put("forwardNoReply", new BaseATCommand("at+ccfc=3,3,\"+8618121305895\",145,1"));
		supportedCommands.put("clearForwardUnconditional", new BaseATCommand("at+ccfc=0,0"));
		supportedCommands.put("clearForwardBusy", new BaseATCommand("at+ccfc=1,0"));
		supportedCommands.put("clearForwardNoReply", new BaseATCommand("at+ccfc=2,0"));
		supportedCommands.put("clearForwardNotReachable", new BaseATCommand("at+ccfc=3,0"));
	}

	private static CommController instance = null;
	private CommInterface commInterface;
	private STATE state = STATE.IDLE;
	private Queue<ATCommand> commandQueue = new LinkedList<ATCommand>();
	private Object lock = new Object();
	private StringBuilder buffer = new StringBuilder();
	private Map<String, NotificationHandler> notificationHandlers = new TreeMap<String, NotificationHandler>();


	private CommController() {
	};

	// singleton
	public static CommController getInstance() {
		if (null == instance) {
			instance = new CommController();
		}
		return instance;
	}


	// set strategy
	public void setCommInterface(CommInterface commInterface) {
		this.commInterface = commInterface;
	}

	public void registerNotificationHandler(String pattern, NotificationHandler handler) {
		if (notificationHandlers.containsKey(pattern)) {
			throw new RuntimeException("Priority conflicted!");
		}
		notificationHandlers.put(pattern, handler);
	}

	public void initializeComm(String portName) {
		commInterface.initialize(portName);
	}

	public void sendAT(ATCommand command) throws IOException, InterruptedException {
		synchronized (lock) {
			if (state == STATE.AWAITING_RESPONSE) {
				logger.info("*******Waiting for previous command");
				lock.wait();
				logger.info("*******Previous command finished");
			}
			logger.info("SendAT:" + Util.toHex(command.getCommand()));
			commInterface.sendAT(command);
			commandQueue.add(command);
			state = STATE.AWAITING_RESPONSE;
		}
	}

	public void closeComm() {
		commInterface.close();
	}

	private int searchForStringBefore0D0A(String s) {
		return buffer.lastIndexOf(s + '\r' + '\n');
	}

	private void callback(final String data, final Boolean success) {
		logger.info("Data:" + data + " success:" + success);
		synchronized (lock) {
			final ATCommand command = commandQueue.poll();
			(new Thread() {
				public void run() {
					command.callback(data, success);
				}
			}).start();
			state = STATE.IDLE;
			logger.info("***********Release lock");
			lock.notifyAll();
		}
	}

	private void handleData(String data) throws UnsupportedEncodingException {
		buffer.append(data);
		if (state == STATE.AWAITING_RESPONSE) {
			// ERROR present
			int errorIndex = searchForStringBefore0D0A("ERROR");
			if (errorIndex != -1) {
				callback(buffer.substring(0, errorIndex), false);
				buffer.delete(0,errorIndex + 7);
			} else {
				// OK Present
				int okIndex = searchForStringBefore0D0A("OK");
				if (okIndex != -1) {
					callback(buffer.substring(0, okIndex), true);
					buffer.delete(0, okIndex + 4);
				}
				// else{
				// // Echo of command
				// int
				// echoLineIndex=searchForStringBefore0D0A(commandQueue.peek().getCommand());
				// if( -1 != echoLineIndex){
				// logger.info(Util.toHex(buffer.substring(0, echoLineIndex)));
				// String dd=buffer.substring(0, echoLineIndex);
				// deBuffer(echoLineIndex+commandQueue.peek().getCommand().length()+2);
				// callback(dd,true);
				// }
				// }
			}
		} else {
			// we are idle, data available means a notification
			int l = searchForStringBefore0D0A("");
			if (l != -1) {
				final String copy = buffer.substring(0, l);
				logger.info("Notification buffer:" + Util.toHex(buffer.substring(0, l)));
				notificationHandlers.entrySet().forEach(entry -> {
					Matcher m = Pattern.compile(entry.getKey()).matcher(copy);
					if (m.find()) {
						// the handler must NOT be blocked.
						entry.getValue().handle(m);
					}
				});
				buffer.delete(0, l+2);
			}
		}
	}

	@Override
	public void available(String data) {
		try {
			handleData(data);
		} catch (UnsupportedEncodingException e) {
			logger.severe(e.toString());
		}
	}

	public static void main(String[] args) {
		boolean consoleMode = false;
		if (args.length != 2 && args.length != 3) {
			usage();
		}
		if (args.length == 3) {
			if(args[2].equals("console")){
			consoleMode = true;
			} else {
				usage();
			}
		}
		CommController cc = CommController.getInstance();
		CommInterface ci = null;
		if (args[0].equals("rxtx")) {
			ci = new RxtxCommImpl();
		} else if (args[0].equals("pi4j")) {
			ci = new Pi4jCommImpl();
			StringBuilder msg = new StringBuilder();
			try {
				for (String ipAddress : NetworkInfo.getIPAddresses()) {
				    msg.append(ipAddress);
				    msg.append(';');
				}
				if (!consoleMode) {
					EmailServer.getMailer("yahoo").sendAsync(msg.toString(), "sms started", "zhou_jack@live.com");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			usage();
		}
		ci.registerDataAvailableListener(cc);
		cc.setCommInterface(ci);
		try {
			cc.initializeComm(args[1]);
			cc.sendAT(new BaseATCommand("ATE0")); // TURN OFF Echo
			cc.sendAT(new BaseATCommand("AT+IFC=1,1"));
			cc.sendAT(new SetMessageFormatCommand(0));// Enter into SMS PDU mode
			cc.registerNotificationHandler("\\+CLIP\\: \"(\\d+)\"", new IncomingCallNotificationHandler());
			cc.registerNotificationHandler("\\+CMTI\\: \"SM\",(\\d+)", new NewMessageNotificationHandler());

			// process AT command from console
			if (consoleMode) {
				InputStreamReader in = new InputStreamReader(System.in);
				BufferedReader bin = new BufferedReader(in);
				while (true) {
					String text = bin.readLine().trim();
					if (text.length() == 0) {
						continue;
					}
					if (text.equals("exit")) {
						System.exit(0);
					}
					if (supportedCommands.containsKey(text)) {
						new SendATCommandThread(supportedCommands.get(text)).start();
					} else {
						new SendATCommandThread(new BaseATCommand(text)).start();
					}
				}
			}
			else{
				//make sure main thread not quit
				Object obj=new Object();
				synchronized(obj) {
				    while (true) {
				        obj.wait();
				    }
				}
			}
		} catch (IOException e) {
			logger.severe(e.toString());
		} catch(InterruptedException e){
			logger.severe(e.toString());
		}finally {
			cc.closeComm();
		}
	}

	private static void usage() {
		System.out.println("Usage: java CommController <rxtx|pi4j> <port> [console]");
		System.out.println(supportedCommands.keySet());
		System.exit(1);
	}
}
