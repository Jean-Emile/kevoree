package org.kevoree.library.javase.hidrfid;

import gnu.io.*;
import org.kevoree.annotation.*;
import org.kevoree.framework.AbstractComponentType;
import org.kevoree.framework.MessagePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.TooManyListenersException;

/**
 * User: Erwan Daubert
 * Date: 18/04/11
 * Time: 11:28
 */
@DictionaryType({
		@DictionaryAttribute(name = "PORT", optional = false, defaultValue = "/dev/ttyS0")
})
@Requires({
		@RequiredPort(name = "TAG", optional = true, type = PortType.MESSAGE, needCheckDependency = false)
})
@ComponentType
@Library(name = "HID_RFID")
public class ProxProRFIDReader extends AbstractComponentType implements SerialPortEventListener {

	private InputStream inputStream;
	private SerialPort serialPort;

	private Logger logger = LoggerFactory.getLogger(ProxProRFIDReader.class);

	@Start
	public void start() {
		try {
			CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier((String) this.getDictionary().get("PORT"));

			CommPort commPort = portIdentifier.open(this.getNodeName() + ":" + this.getName(), 2000);

			if (commPort instanceof SerialPort) {
				serialPort = (SerialPort) commPort;
				// TODO maybe serial port parameters must be reviewed
				//serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

				inputStream = serialPort.getInputStream();

				serialPort.addEventListener(this);
				serialPort.notifyOnDataAvailable(true);
			} else {
				// TODO log
				logger.error("Only serial ports are handled by this component.");
				commPort.close();
			}
		} catch (NoSuchPortException e) {
			// TODO log
			e.printStackTrace();
		} catch (PortInUseException e) {
			// TODO log
			e.printStackTrace();
		} /*catch (UnsupportedCommOperationException e) {
			// TODO log
			e.printStackTrace();
		} */catch (TooManyListenersException e) {
			// TODO log
			e.printStackTrace();
		} catch (IOException e) {
			// TODO log
			e.printStackTrace();
		}
	}

	@Stop
	public void stop() {
		try {
			inputStream.close();
		} catch (IOException e) {
				// TODO log
			e.printStackTrace();
		}
		serialPort.removeEventListener();
		serialPort.close();


	}

	@Override
	public void serialEvent(SerialPortEvent serialPortEvent) {
		int data;
		byte[] buffer = new byte [16];

		try {
			int len = 0;
			while ((data = inputStream.read()) > -1) {
				if (data == '\n') {
					break;
				}
				buffer[len++] = (byte) data;
			}
			if (isPortBinded("TAG")) {
				getPortByName("TAG", MessagePort.class).process(new String(buffer, 0, len));
			} else {
				logger.info(new String(buffer, 0, len));
				//logger.info(" length : " + len);
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
