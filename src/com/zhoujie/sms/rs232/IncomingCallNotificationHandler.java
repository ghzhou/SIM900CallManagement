package com.zhoujie.sms.rs232;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
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

public class IncomingCallNotificationHandler extends BaseNotificationHandler {

	static class PhoneNumberInfo {
		public String tag;
		public String from;
		public String operator;
		public int spamValue;
	}

	private final static Logger logger = Logger.getLogger(IncomingCallNotificationHandler.class.getName());
	private static String numberInProcess = "";
	private static Map<String, PhoneNumberInfo> pniCache = new HashMap<String, PhoneNumberInfo>();
	
	static {
		try {
			Properties props = new Properties();
			props.load(new FileInputStream("whitelist.properties"));
			for(String number:  props.getProperty("whitelist").split(",")){
				PhoneNumberInfo pni = new PhoneNumberInfo();
				pni.spamValue = 0;
				pni.from = "White List";
				pni.operator = "White List";
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

	public static synchronized void setNumberInProcess(String numberInProcess) {
		IncomingCallNotificationHandler.numberInProcess = numberInProcess;
	}

	@Override
	public boolean handle(String data) {
		Pattern p = Pattern.compile("\\+CLIP\\: \"(\\d+)\"");
		Matcher m = p.matcher(data);
		if (m.find()) {
			String incommingCall = m.group(1);
			if (incommingCall.equals(numberInProcess)) {
				return false;
			}
			setNumberInProcess(incommingCall);
			logger.info("Got incomming call from " + incommingCall);
			PhoneNumberInfo pni = getPhoneNumberInfo1(canonicalize(incommingCall));
			String mailSubject = null;
			if (2 == pni.spamValue) { // spam from SH, answer it
				logger.info("Answer: " + incommingCall);
				new SendATCommandThread(new RespondCallCommand("ATA")).start();
				mailSubject = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime()) + " -Call received and answered: "
						+ incommingCall + " " + pni.tag + " " + pni.from +" "+pni.operator;
			} else if (1 == pni.spamValue) { // spam from out of SH, let it ring
//				logger.info("Let it ring: " + incommingCall);
				logger.info("Answer: " + incommingCall);
				new SendATCommandThread(new RespondCallCommand("ATA")).start();
				mailSubject = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime()) + " -Call received and answered: "
						+ incommingCall + " " + pni.tag + " " + pni.from+" "+pni.operator;
			} else {
				// Hang up, so that auto-forward to my real number
				logger.info("Hang up: " + incommingCall);
				new SendATCommandThread(new RespondCallCommand("ATH")).start();
				mailSubject = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime()) + " -Call received and forwarded: "
						+ incommingCall + " " + pni.tag + " " + pni.from+" "+pni.operator;
			}
			EmailServer.getMailer("yahoo").sendAsync("", mailSubject, "zhou_jack@live.com");
		}
		return false;
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
			pni.operator = "unknown";
			pni.spamValue = -1;
			try {
				CloseableHttpClient httpclient = HttpClients.createDefault();
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
				CloseableHttpResponse response1 = httpclient.execute(httpGet);
//				if (!response1.getStatusLine().equals("HTTP/1.1 200 OK")) {
//					return pni;
//				}
				try {
				    HttpEntity entity1 = response1.getEntity();
				    String result = EntityUtils.toString(entity1);
				    //System.out.println(result);
				    
				    /* sample data
var queryphoneinfo = '号码通用户数据：房产中介'.replace('：', ':');
var amount = '119';
var showmin = '5';
</script><script type="text/javascript">
define("", ["vr"], function(vr) {
	vr.add(491, "10001001", "", 0,"15300918759	上海 中国电信 ");
	}
);				      
				     */
					Matcher m = Pattern.compile("号码通用户数据：(.*?)\\}", Pattern.DOTALL).matcher(result);
					if (m.find()) {
						String block = m.group(1);
						System.out.println(block);
						Matcher m1 = Pattern.compile("^(.+?)'").matcher(block);
						if (m1.find()){
							pni.tag = m1.group(1);
						}
						Matcher m2 = Pattern.compile(incomingCall+"\\t(.+?)(\\s.+?)?\"\\)").matcher(block);
						if (m2.find()){
							pni.from = m2.group(1);
							pni.operator=m2.group(2);
						}
						pni.spamValue = 0;
						if (pni.tag.contains("中介") || pni.tag.contains("推销") || pni.tag.contains("骚扰") || pni.tag.contains("诈骗")) {
							pni.spamValue++;
							if (pni.from.contains("上海")) {
								pni.spamValue++;
							}
						}
					} else {
						pni.spamValue = 0;
					}
					pniCache.put(incomingCall, pni);// Exception won't enter into cache
				    EntityUtils.consume(entity1);
				} finally {
				    response1.close();
				}

			} catch (IOException e) {
				logger.severe(e.toString());
			}
			logger.info("Number type: " + pni.tag + " from: " + pni.from  + " operator: " + pni.operator + " spamValue: " + pni.spamValue);
			return pni; 
		}
	}

	
//	private PhoneNumberInfo getPhoneNumberInfo(String incomingCall) {
//		if (pniCache.get(incomingCall) != null) {
//			logger.info("Cache hit for: " + incomingCall);
//			return pniCache.get(incomingCall);
//		} else {
//			PhoneNumberInfo pni = new PhoneNumberInfo();
//			pni.tag = "unknown";
//			pni.from = "unknown";
//			pni.spamValue = -1;
//			try {
//				//HttpURLConnection huc = (HttpURLConnection) new URL("http://wap.sogou.com/web/searchList.jsp?keyword=" + incomingCall).openConnection();
//				HttpURLConnection huc = (HttpURLConnection) new URL("http://www.sogou.com/web?query=" + incomingCall).openConnection();
//				HttpURLConnection.setFollowRedirects(true);
//				huc.setConnectTimeout(5 * 1000);
//				huc.setReadTimeout(1 * 1000);
//				huc.setRequestMethod("GET");
//				huc.addRequestProperty("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
//				huc.addRequestProperty("Accept-Language","zh-CN,zh;q=0.8,en-US;q=0.6,en;q=0.4");
//				huc.addRequestProperty("Accept-Encoding","gzip,deflate");
//				huc.addRequestProperty("Cache-Control","no-cache");
//				huc.setRequestProperty("User-Agent",
//						"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/34.0.1847.131 Safari/537.36");
//				huc.connect();
//				int length = huc.getContentLength();
//				if (length==-1) {
//					length = 1000000;
//				}
//				byte[] buffer = new byte[length+1];
//				InputStream input = huc.getInputStream();
//				int bytesRead = 0;
//				while (true){
//					if (bytesRead==length){
//						break;
//					}
//					System.out.println("bytesRead:"+bytesRead+"Left:"+(length-bytesRead));
//					int ret = input.read(buffer,bytesRead,length-bytesRead);
//					if(ret==-1){
//						break;
//					}
//					bytesRead+=ret;
//				}
//				buffer[bytesRead] = '\0';
//				String s = new String(buffer, "UTF-8");
//				//Pattern p = Pattern.compile("号码通用户数据:(\\S+)\\s(\\S+)\\s");
//				Pattern p = Pattern.compile("html(.+)html");
//				
//				//<h4 id="phoneinfo" class="vestiong"></h4>
//
//				//<p id="phonenumberfrom" class="idMsg" style="display: none"></p>
//				Matcher m = p.matcher(s);
//				if (m.find()) {
//					pni.tag = m.group(1);
//					pni.from = m.group(2);
//					pni.spamValue = 0;
//					if (pni.tag.contains("中介") || pni.tag.contains("推销") || pni.tag.contains("骚扰") || pni.tag.contains("诈骗")) {
//						pni.spamValue++;
//						if (pni.from.contains("上海")) {
//							pni.spamValue++;
//						}
//					}
//				} else {
//					pni.spamValue = 0;
//				}
//				pniCache.put(incomingCall, pni);// Exception won't enter into
//												// cache
//			} catch (IOException e) {
//				logger.severe(e.toString());
//			}
//			logger.info("Number type: " + pni.tag + " from: " + pni.from + " spamValue: " + pni.spamValue);
//			return pni;
//		}
//	}

	public static void main(String[] args) {

//		new IncomingCallNotificationHandler().getPhoneNumberInfo1("15300918759");
//		new IncomingCallNotificationHandler().getPhoneNumberInfo1("02161961691");
//		new IncomingCallNotificationHandler().getPhoneNumberInfo1("13701832975");
		new IncomingCallNotificationHandler().getPhoneNumberInfo1("02151374173");
	}

}
