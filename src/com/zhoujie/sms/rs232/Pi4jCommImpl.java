package com.zhoujie.sms.rs232;

import java.io.UnsupportedEncodingException;
import java.util.logging.Logger;

import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialDataEvent;
import com.pi4j.io.serial.SerialDataListener;
import com.pi4j.io.serial.SerialFactory;
import com.pi4j.io.serial.SerialPortException;

public class Pi4jCommImpl implements CommInterface {
	
	private final static Logger logger = Logger.getLogger(Pi4jCommImpl.class.getSimpleName());
	final Serial serial = SerialFactory.createInstance();
	private DataAvailableListener listener;
		

	@Override
	public void sendAT(ATCommand command) {
		try {
			serial.write(command.getCommand().getBytes("US-ASCII"));
			serial.write((byte)13);
			serial.write((byte)10);
		} catch (IllegalStateException e) {
			logger.severe(e.toString());
		} catch (UnsupportedEncodingException e){
			logger.severe(e.toString());
		}
	}

	@Override
	public void initialize(String port) {
		try {
			serial.addListener(new SerialDataListener() {
	            @Override
	            public void dataReceived(SerialDataEvent event) {
	                  listener.available(event.getData());
	            }            
	        });
			serial.open(port, 9600);
		} catch (SerialPortException e) {
			System.err.println("Failed to init the serial port");
			System.exit(-1);
		}
	}

	@Override
	public void registerDataAvailableListener(DataAvailableListener listener) {
		this.listener=listener;
	}

	@Override
	public void close() {
		serial.close();
	}
}
