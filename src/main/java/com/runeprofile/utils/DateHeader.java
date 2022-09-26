package com.runeprofile.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;

public class DateHeader {

	private static SimpleDateFormat headerFormat;
	private static SimpleDateFormat stringFormat;

	public static String getDateString(String dateString) {
		if (headerFormat == null) {
			headerFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
		}

		if (stringFormat == null) {
			stringFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		}

		try {
			return stringFormat.format(headerFormat.parse(dateString));
		} catch (ParseException e) {
			return "Failed to parse";
		}
	}
}
