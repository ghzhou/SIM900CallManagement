package com.zhoujie.sms.rs232;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.TooManyListenersException;
import java.util.logging.Logger;

public class RxtxCommImpl implements CommInterface,SerialPortEventListener {
	
	private final static Logger logger = Logger.getLogger(RxtxCommImpl.class.getName());
	private DataAvailableListener listener;
	private SerialPort sp;
	private InputStream is;
	private OutputStream os;
	
	public void serialEvent(SerialPortEvent event) {
		if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE){
			// logger.info("data available");
			byte[] readBuffer = new byte[4096];
			try {
				while (is.available() > 0) {
					int numBytes = is.read(readBuffer, 0, 4096);
					logger.info("Received "+numBytes+"|"+Util.toHex(new String(Arrays.copyOf(readBuffer,numBytes))));
					listener.available(new String(Arrays.copyOf(readBuffer, numBytes)));
				}
			} catch (IOException e) {
				logger.severe(e.toString());
			}
		}
	}



	@Override
	public void sendAT(ATCommand command) {
		try {
			os.write(command.getCommand().getBytes("US-ASCII"));
			os.write(13);
			os.write(10);
			os.flush();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void registerDataAvailableListener(DataAvailableListener listener) {
		this.listener=listener;
	}

	@Override
	public void initialize(String portName) {
		try {
			logger.info("Initialize sim 900a");
			CommPortIdentifier port = CommPortIdentifier.getPortIdentifier(portName);
			sp = (SerialPort) port.open("SMS", 0);
			sp.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			sp.addEventListener(this);
			// sp.notifyOnOutputEmpty(true);
			sp.notifyOnDataAvailable(true);
			//sp.setDTR(false);
			sp.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
			is = sp.getInputStream();
			os = sp.getOutputStream();
		} catch (PortInUseException e) {
			logger.severe("Port is in use");
		} catch (IOException e) {
			logger.severe("Unable to get inputstream/outputsteam");
		} catch (UnsupportedCommOperationException e) {
			logger.severe(e.toString());
		} catch (TooManyListenersException e) {
			logger.severe(e.toString());
		} catch (NoSuchPortException e) {
			logger.severe("No such port: "+portName);
		}
	}

	@Override
	public void close() {
		try {
			if (null != is) {
				is.close();
			}
			if (null != os) {
				os.close();
			}
		} catch (IOException e) {
		}
		sp.close();
	}

}
