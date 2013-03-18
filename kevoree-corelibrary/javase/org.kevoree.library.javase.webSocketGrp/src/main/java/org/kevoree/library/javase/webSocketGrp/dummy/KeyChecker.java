package org.kevoree.library.javase.webSocketGrp.dummy;

import java.io.File;

public class KeyChecker {

	public static boolean validate(File keyfile) {
		// yeah this is a pretty amazing checker
		return true;
	}
	
	public static boolean validate(String key) {
		// yeah this one is pretty secure too
		return true;
	}
}
