package com.zhoujie.sms.data;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ShortMessage {

	private final static Logger logger = Logger.getLogger(ShortMessage.class.getName());

	String sender;
	String timeOfReceive;
	String body;
	Map<Integer, String> segments = new HashMap<>();

	private static String formatTimestamp(String ts) {
		StringBuilder sb = new StringBuilder(ts.length() + 3);
		sb.append(ts.substring(0, 6));
		sb.append(' ');
		sb.append(ts.substring(6, 8));
		sb.append(':');
		sb.append(ts.substring(8, 10));
		sb.append(':');
		sb.append(ts.substring(10, 12));
		sb.append('.');
		sb.append(ts.substring(12));
		return sb.toString();
	}

	private static String decode(String content) {
		int length = content.length();
		byte[] buffer = new byte[length / 2];
		for (int i = 0; i < length; i += 2) {
			buffer[i >> 1] = (byte) Integer.parseInt(content.substring(i, i + 2), 16);
		}
		try {
			return new String(buffer, "UTF-16BE");
		} catch (UnsupportedEncodingException e) {
			logger.severe(e.toString());
			return null;
		}
	}

	private static String reverseBytes(String data) {
		StringBuilder sb = new StringBuilder(data.length());
		for (int i = 0; i < data.length() - 2; i += 2) {
			sb.append(data.charAt(i + 1));
			sb.append(data.charAt(i));
		}
		sb.append(data.charAt(data.length() - 1));
		if ('F' != data.charAt(data.length() - 2)) {
			sb.append(data.charAt(data.length() - 2));
		}
		return sb.toString();
	}

	public static boolean parsePDU(ShortMessage sm, String pdu, int length) {
		int currentIndex = 0;
		int smscLength = Integer.parseInt(pdu.substring(currentIndex, currentIndex + 2), 16);
		currentIndex += 2;
		logger.info("smscLength:" + smscLength);

		String smscNumType = pdu.substring(currentIndex, currentIndex + 2);
		currentIndex += 2;
		logger.info("smsNumType:" + smscNumType);

		String smscNumber = pdu.substring(currentIndex, currentIndex + 2 * smscLength - 2);
		currentIndex += 2 * smscLength - 2;
		logger.info("smsc:" + reverseBytes(smscNumber));

		byte pduType = (byte) Integer.parseInt(pdu.substring(currentIndex, currentIndex + 2),16);
		currentIndex += 2;

		int oaLength = Integer.parseInt(pdu.substring(currentIndex, currentIndex + 2), 16);
		currentIndex += 2;
		logger.info("oaLength:" + oaLength);

		@SuppressWarnings("unused")
		String oaType = pdu.substring(currentIndex, currentIndex + 2);
		currentIndex += 2;

		String oaNumber = reverseBytes(pdu.substring(currentIndex, currentIndex + ((oaLength + 1) / 2) * 2));
		currentIndex = currentIndex + ((oaLength + 1) / 2) * 2;
		logger.info("Sender number:" + oaNumber);

		@SuppressWarnings("unused")
		String pid = pdu.substring(currentIndex, currentIndex + 2);
		currentIndex += 2;

		@SuppressWarnings("unused")
		String dcs = pdu.substring(currentIndex, currentIndex + 2);
		currentIndex += 2;

		String timestamp = formatTimestamp(reverseBytes(pdu.substring(currentIndex, currentIndex + 14)));
		currentIndex += 14;
		logger.info("Time stamp:" + timestamp);

		int udl = Integer.parseInt(pdu.substring(currentIndex, currentIndex + 2), 16);
		currentIndex += 2;
		logger.info("udl:" + udl);
		
		String UDH = null;
		if((pduType & 0x40) != 0){ // we have UDH here
			int lengthOfUDH = Integer.parseInt(pdu.substring(currentIndex, currentIndex + 2), 16);
			currentIndex += 2;
			logger.info("Len of UDH:" + lengthOfUDH);

			UDH = pdu.substring(currentIndex, currentIndex + lengthOfUDH * 2);
			currentIndex += lengthOfUDH * 2;
			logger.info("UDH:" + UDH);
		}


		logger.info("content before decode:" + pdu.substring(currentIndex));
		String content = decode(pdu.substring(currentIndex));
		logger.info("msgcontent:" + content);
		
		sm.setSender(oaNumber);
		sm.setTimeOfReceive(timestamp);
		if (null!=UDH && (UDH.startsWith("00") || UDH.startsWith("08"))) {//IEI:00 or 08 means concat msg
			int udh = Integer.parseInt(UDH.substring(UDH.length() - 4, UDH.length()), 16);
			int total = (udh & 255<<8)>>8;
			int index = udh & 255;
			logger.info("Index/Total = " + index + "/" + total);
			return total == sm.addSegment(index, content);
		} 
		sm.addSegment(0, content);
		return true; // only 1 segment, no more segment to come
	}

	public ShortMessage(String sender, String timeOfReceive) {
		this.sender = sender;
		this.timeOfReceive = timeOfReceive;
		this.body = "";
	}

	public ShortMessage() {
		this("", "");
	}
	public String getSender() {
		return sender;
	}

	public void setSender(String sender) {
		this.sender = sender;
	}

	public String getTimeOfReceive() {
		return timeOfReceive;
	}

	public void setTimeOfReceive(String timeOfReceive) {
		this.timeOfReceive = timeOfReceive;
	}

	public String getBody() {
		return segments.entrySet().stream().
				sorted((entry1, entry2) -> entry1.getKey() - entry2.getKey())
				.map((entry) ->entry.getValue())
				.reduce((t, u)-> t + u)
				.get();
	}

	public int addSegment(int index, String segment) {
		segments.put(index, segment);
		return segments.size();
	}
	
}
