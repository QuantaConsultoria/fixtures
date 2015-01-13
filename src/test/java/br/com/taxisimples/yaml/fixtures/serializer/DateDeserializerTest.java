package br.com.taxisimples.yaml.fixtures.serializer;

import java.util.Calendar;

import org.junit.Assert;
import org.junit.Test;

public class DateDeserializerTest {
	
	private int expectedYear = 2012;
	private int expectedMonth = Calendar.MAY;
	private int expectedDay = 21;
	
	@Test
	public void mustDeserializeDate() {
		DateDeserializer dateDeserializer = new DateDeserializer();
		Calendar date = Calendar.getInstance();
		date.setTime(dateDeserializer.deserialize("2012-05-21"));
		
		Assert.assertEquals(expectedYear, date.get(Calendar.YEAR));
		Assert.assertEquals(expectedMonth, date.get(Calendar.MONTH));
		Assert.assertEquals(expectedDay, date.get(Calendar.DAY_OF_MONTH));
	}
	
	@Test
	public void mustDeserializeDateAnTime() {
		int expectedHour = 19;
		int expectedMinute = 35;
		int expectedSecond = 27;
		DateDeserializer dateDeserializer = new DateDeserializer();
		Calendar date = Calendar.getInstance();
		date.setTime(dateDeserializer.deserialize("2012-05-21 19:35:27"));
		
		Assert.assertEquals(expectedYear, date.get(Calendar.YEAR));
		Assert.assertEquals(expectedMonth, date.get(Calendar.MONTH));
		Assert.assertEquals(expectedDay, date.get(Calendar.DAY_OF_MONTH));
		Assert.assertEquals(expectedHour, date.get(Calendar.HOUR_OF_DAY));
		Assert.assertEquals(expectedMinute, date.get(Calendar.MINUTE));
		Assert.assertEquals(expectedSecond, date.get(Calendar.SECOND));
	}

}
