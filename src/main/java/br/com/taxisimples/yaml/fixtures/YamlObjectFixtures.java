package br.com.taxisimples.yaml.fixtures;

import java.lang.reflect.Field;
import java.util.Map;
import org.hibernate.type.Type;

public class YamlObjectFixtures {

	private Field field;
	
	private Object instance;
	
	private Map<String, Object> yamlValue;
	
	private Type hibernateType;

	public YamlObjectFixtures(Field field, Object instance, Map<String, Object> yamlValue, Type hibernateType) {
		super();
		this.field = field;
		this.instance = instance;
		this.yamlValue = yamlValue;
		this.hibernateType = hibernateType;
	}

	public Field getField() {
		return field;
	}

	public Object getInstance() {
		return instance;
	}

	public Map<String, Object> getYamlValue() {
		return yamlValue;
	}

	public Type getHibernateType() {
		return hibernateType;
	}

}
