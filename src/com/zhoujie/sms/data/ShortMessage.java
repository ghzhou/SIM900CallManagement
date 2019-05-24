package com.zhoujie.sms.data;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class ShortMessage {

	private final static Logger logger = Logger.getLogger(ShortMessage.class.getName());
	private static Map<String, ShortMessage> messagesHolder = new HashMap<>();
	
	private String sender;
	private String timeOfReceive;
	private Map<Integer, String> segments;

	
	private ShortMessage(String sender, String timeOfReceive) {
		this.sender = sender;
		this.timeOfReceive = timeOfReceive;
		segments = new HashMap<>();
	}

	public String getSender() {
		return sender;
	}

	public String getTimeOfReceive() {
		return timeOfReceive;
	}

	public String getBody() {
		return segments.keySet().stream().sorted()
				.map(key -> segments.get(key))
				.reduce((t, u)-> t + u)
				.get();
	}

	public void addSegment(int index, String segment) {
		segments.put(index, segment);
	}

	private boolean isAllSegmentsReady(int total) {
		return segments.size() == total;
	}
	
	private static Optional<ShortMessage> assembleMessage(String sender, String segment, String timestamp, int index, int total) {
		if (total == 1) {
			ShortMessage message = new ShortMessage(sender, timestamp);
			message.addSegment(1, segment);
			return Optional.of(message);
		}
		String key = sender + timestamp + total;
		ShortMessage message = messagesHolder.get(key);
		if(null == message) {
			message = new ShortMessage(sender, timestamp);
			message.addSegment(index, segment);
			messagesHolder.put(key, message);
			return Optional.empty();
		}
		message.addSegment(index, segment);
		if (message.isAllSegmentsReady(total)) {
			messagesHolder.remove(key);
			return Optional.of(message);
		}
		return Optional.empty();
	}


	public static Optional<ShortMessage> parsePDU(String pdu, int length) {
		logger.info("Start parsing a PDU");
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
		
		int index=1;
		int total=1;
		if (null!=UDH && (UDH.startsWith("00") || UDH.startsWith("08"))) {//IEI:00 or 08 means concat msg
			int udh = Integer.parseInt(UDH.substring(UDH.length() - 4, UDH.length()), 16);
			total = (udh & 255<<8)>>8;
			index = udh & 255;
			logger.info("Index/Total = " + index + "/" + total);
		}
		logger.info("End parsing a PDU");
		return assembleMessage(oaNumber, content, timestamp, index, total);
	}


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

}
