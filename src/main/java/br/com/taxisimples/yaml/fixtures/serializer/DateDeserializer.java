package br.com.taxisimples.yaml.fixtures.serializer;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class DateDeserializer implements Deserializer<Date>{

	@Override
	public Date deserialize(Object objectValue) {
		String date =  objectValue.toString();
		String format = "yyyy-MM-dd";
		if (date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
			format = "yyyy-MM-dd";
		} else if (date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")) {
			format = "yyyy-MM-dd HH:mm:ss";
		}else if (date.matches("[0-9]{4}/[0-9]{2}/[0-9]{2}")) {
			format = "yyyy/MM/dd";
		} else if (date.matches("[0-9]{2}-[0-9]{2}-[0-9]{4}")) {
			format = "dd-MM-yyyy";
		} else if (date.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}")) {
			format = "dd/MM/yyyy";
		} else if (date	.matches("[A-Za-z]{3} [A-Za-z]{3} [0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2} [A-Za-z]{3} [0-9]{4}")) {
			format = "EEE MMM dd HH:mm:ss Z yyyy";
		} else if (date	.matches("[A-Za-z]{3} [0-9]{1,2}, [0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} [A-Za-z]{2}")) {
			format = "MMM dd, yyyy HH:mm:ss aaa";
		}

		try {
			return new SimpleDateFormat(format, Locale.ROOT).parse(date);
		} catch (ParseException e) {
			throw new RuntimeException("Date can't be parsable with objetct", e);
		}
	}
  
}  