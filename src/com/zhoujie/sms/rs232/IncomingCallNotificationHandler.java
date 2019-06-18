package com.zhoujie.sms.rs232;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.zhoujie.sms.mail.EmailServer;

public class IncomingCallNotificationHandler implements NotificationHandler {

	static class PhoneNumberInfo {
		public String tag;
		public String from;
		public String operator;
		public int spamValue;
	}

	private final static Logger logger = Logger.getLogger(IncomingCallNotificationHandler.class.getSimpleName());
	private static Set<String> numberInProcess = new HashSet<>();
	private static Map<String, PhoneNumberInfo> pniCache = new HashMap<String, PhoneNumberInfo>();
    private static Pattern pattern = Pattern.compile("'����ͨ�û����ݣ�(.*��.*)?',.*,.*,'(.*),(.*),(.*),.*'\\)",
    		Pattern.MULTILINE);
	
	static {
		try {
			Properties props = new Properties();
			props.load(new FileInputStream("whitelist.properties"));
			for(String number:  props.getProperty("whitelist").split(",")){
				PhoneNumberInfo pni = new PhoneNumberInfo();
				pni.spamValue = 0;
				pni.from = "White List";
				pni.tag = "White List";
				pniCache.put(number, pni);
			} 
		} catch(IOException e) {
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.zhoujie.sms.rs232.BaseNotificationHandler#handle(java.lang.String)
	 * +CLIP: "02150385222"
	 */

	public static synchronized void  addNumberInProcess(String number) {
		numberInProcess.add(number);
	}
	
	public static synchronized void removeNumberInProcess(String number) {
		numberInProcess.remove(number);
	}

	@Override
	public void handle(Matcher m) {
		String incomingCall = m.group(1);
		if (numberInProcess.contains(incomingCall)) {
			logger.info("Duplicated incomming call from " + incomingCall);
			return;
		}
		logger.info("Got incomming call from " + incomingCall);
		PhoneNumberInfo pni = getPhoneNumberInfo1(canonicalize(incomingCall));
		String mailSubject = null;
		if (1 <= pni.spamValue) {
			logger.info("Answer: " + incomingCall);
			new SendATCommandThread(new RespondCallCommand("ATA", incomingCall)).start();
			mailSubject = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime()) + " -Call received and answered: "
					+ incomingCall + " " + pni.tag + " " + pni.from;
		} else {
			// Hang up, so that auto-forward to my real number
			logger.info("Hang up: " + incomingCall);
			new SendATCommandThread(new RespondCallCommand("ATH", incomingCall)).start();
			mailSubject = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime()) + " -Call received and forwarded: "
					+ incomingCall + " " + pni.tag + " " + pni.from;
		}
		EmailServer.getMailer("yahoo").sendAsync("", mailSubject, "zhou_jack@live.com");
	}

	private String canonicalize(String phoneNumber) {
		if (phoneNumber.startsWith("21") && phoneNumber.length()==10 ){
			return "0"+phoneNumber;
		}
		return phoneNumber;
	}

	/*
	 * return -1: exception happened 0: not a spam (unknown) 1: a spam from out
	 * of Shanghai 2: a spam from Shanghai
	 */
	
	private PhoneNumberInfo  getPhoneNumberInfo1(String incomingCall){
		if (pniCache.get(incomingCall) != null) {
			logger.info("Cache hit for: " + incomingCall);
			return pniCache.get(incomingCall);
		} else {
			PhoneNumberInfo pni = new PhoneNumberInfo();
			pni.tag = "unknown";
			pni.from = "unknown";
			pni.spamValue = -1;
			try {
				CloseableHttpClient httpclient = HttpClients.custom()
						.setUserAgent("Mozilla/5.0 (Windows NT 6.3; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36")
						.build();
				HttpGet httpGet = new HttpGet("http://www.sogou.com/web?query=" + incomingCall);

				//-Dhttp.proxyHost=web-proxy.sgp.hp.com -Dhttp.proxyPort=8080
				String proxyHost = System.getProperty("http.proxyHost");
				String proxyPort = System.getProperty("http.proxyPort");
				if(proxyHost!=null && proxyPort!=null){
					HttpHost proxy = new HttpHost(proxyHost, Integer.parseInt(proxyPort), "http");
		            RequestConfig config = RequestConfig.custom()
		                    .setProxy(proxy)
		                    .build();
					httpGet.setConfig(config);
				}
				try (CloseableHttpResponse respone = httpclient.execute(httpGet)){
				    HttpEntity entity = respone.getEntity();
				    /*
				     *   '����ͨ�û����ݣ�ɧ�ŵ绰��2','0','5','�Ϻ�,�Ϻ�,�й�����,021')
				     *   '����ͨ�û����ݣ�','0','5','�Ϻ�,�Ϻ�,�й���ͨ,021')        
				     */
				    String html = EntityUtils.toString(entity, "UTF8");
				    Matcher m = pattern.matcher(html);
					if (m.find()) {
						if (m.group(1) != null) {
							pni.tag = m.group(1);
						}
						pni.from = m.group(2) + m.group(3);
						pni.operator=m.group(4);
						pni.spamValue = 0;
						if (pni.tag.contains("�н�")
								|| pni.tag.contains("����")
								|| pni.tag.contains("ɧ��")
								|| pni.tag.contains("թƭ")) {
							pni.spamValue++;
							if (pni.from.contains("�Ϻ�")) {
								pni.spamValue++;
							}
						}
					} else {
						pni.spamValue = 0;
					}
					pniCache.put(incomingCall, pni);// Exception won't enter into cache
				    EntityUtils.consume(entity);
				}
			} catch (IOException e) {
				logger.severe(e.toString());
			}
			logger.info("Number type: " + pni.tag + " from: " + pni.from  + " spamValue: " + pni.spamValue);
			return pni; 
		}
	}

	public static void main(String[] args) {
		new IncomingCallNotificationHandler().getPhoneNumberInfo1("15300918759");
		new IncomingCallNotificationHandler().getPhoneNumberInfo1("02161961691");
		new IncomingCallNotificationHandler().getPhoneNumberInfo1("13701832975");
		new IncomingCallNotificationHandler().getPhoneNumberInfo1("02151374173");
	}

}
