package com.zhoujie.sms.rs232;

import java.io.UnsupportedEncodingException;

public class Util {
	public static String toHex(String s) {
		char[] map = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
		byte[] b;
		try {
			b = s.getBytes("US-ASCII");
			StringBuilder hexPart = new StringBuilder("\n    ******HEX:");
			StringBuilder stringPart = new StringBuilder("\n    ***STRING: ");
			for (int i = 0; i < b.length ; i++) {
				if(b[i]==10){
					stringPart.append("<0A>");
				}
				else if(b[i]==13){
					stringPart.append("<0D>");
				}
				else{
					stringPart.append((char) b[i]);
				}
				hexPart.append(' ');
				short v = (short) (b[i] & 0xFF);
				hexPart.append(map[v / 16]);
				hexPart.append(map[v % 16]);
			}
			stringPart.append("<END>");
			hexPart.append("<END>");
			stringPart.append(hexPart);
			return stringPart.toString();
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}
}
