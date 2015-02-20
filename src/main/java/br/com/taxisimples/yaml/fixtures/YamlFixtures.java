package br.com.taxisimples.yaml.fixtures;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;

import org.hibernate.SessionFactory;
import org.hibernate.annotations.CollectionOfElements;
import org.hibernate.annotations.MapKeyManyToMany;
import org.hibernate.ejb.HibernateEntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.type.BagType;
import org.hibernate.type.BigDecimalType;
import org.hibernate.type.ComponentType;
import org.hibernate.type.DateType;
import org.hibernate.type.DoubleType;
import org.hibernate.type.LongType;
import org.hibernate.type.MapType;
import org.hibernate.type.TimestampType;
import org.hibernate.type.Type;
import org.ho.yaml.Yaml;
import org.springframework.stereotype.Service;

import br.com.taxisimples.yaml.fixtures.serializer.DateDeserializer;

@Service
public class YamlFixtures implements Fixture {

	private Map<String,Object> objects = new HashMap<String, Object>();
	private Map<String,Object> references = new HashMap<String, Object>();
	
	@Resource
	protected EntityManagerFactory emf;
	
	@PersistenceContext
	protected EntityManager entityManager;
	
	protected SessionFactory getSessionFactory() {
		return ((HibernateEntityManagerFactory) emf).getSessionFactory();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T> T load(String name) {
		T t = (T)objects.get(name);
		if (entityManager.contains(t)) {
			entityManager.refresh(t);			
		} else {
			t = loadEntity(t);
		}
		return t;
	}
	
	private <T> T loadEntity(T t) {
		return (T) entityManager.find(t.getClass(), getId(t, t.getClass()));
	}
	
	public Object getId(Object object, Class type) {
		try {
			try {
				return getIdByReflection(object, type.getDeclaredField("id"));
			} catch (Exception e) {
				return getIdByReflection(object, type.getField("id"));
			}
		} catch (Exception e) {
			if (Object.class.equals(object.getClass())) {
				throw new RuntimeException("The object must have a id property", e);				
			} else {
				return getId(object, object.getClass().getSuperclass());
			}
		}
	}
	
	protected Object getIdByReflection(Object object, Field field) throws IllegalArgumentException, IllegalAccessException {
		field.setAccessible(true);
		Object idValue = field.get(object);
		field.setAccessible(false);
		return idValue;
	}
	
	@Override
	public void addScenario(InputStream... yamls) {
		for (InputStream yaml: yamls) {
			addScenario(yaml);
		}
		checkForAlias();
		for (Object object :objects.values()) {
			entityManager.persist(object);
		}
		entityManager.flush();
		for (Entry<String, Object> entry: objects.entrySet()) {
			Object mergedObject = entityManager.merge(entry.getValue());
			objects.put(entry.getKey(),mergedObject);
		}
		//for (Object object :objects.values()) {
		//	entityManager.refresh(object);
		//}
		
	}
	
	@SuppressWarnings({"unchecked","rawtypes"})
	protected void addScenario(InputStream yaml) {
		
		try {
			Map<String,Map<String,Map<String,Object>>> yamlFixtures = (Map<String, Map<String, Map<String,Object>>>) Yaml.load(yaml);
			mapClassWithAlias(yamlFixtures);
			
			for (Entry<String, Map<String, Map<String,Object>>> entityTypesEntry: yamlFixtures.entrySet()) {
				ClassMetadata entityMetaData = getClassMetadata(entityTypesEntry.getKey());
				for (Entry<String, Map<String,Object>> entityEntry : entityTypesEntry.getValue().entrySet()) {
					if (objects.containsKey(entityEntry.getKey())) {
						throw new RuntimeException("Fixture name "+entityEntry.getKey()+" is alread used.");
					}
					
					Class entityClass = Class.forName(entityMetaData.getEntityName());
					Object instance = createOrUseInstance(entityClass,entityEntry.getKey());
					objects.put(entityEntry.getKey(), instance);
					
					for (Entry<String, Object> propertieEntry : entityEntry.getValue().entrySet()) {
						Type hibernateType = entityMetaData.getPropertyType(propertieEntry.getKey());
						Field field;
						field = getField(entityClass,propertieEntry.getKey());

						setValue(field, instance, propertieEntry.getValue(), hibernateType);
					}
						
				}			
			}
		} catch (Throwable e) {
			throw new RuntimeException("Some error", e);
		}
	}
	
	
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object parseEmbbed(Class clazz, Map<String, Object> valuesMap, ComponentType hibernateType) throws Exception {
			Object instance = clazz.getConstructor(new Class[0]).newInstance();
		
		for(String fieldName: valuesMap.keySet()){
			Field field =  getField(clazz,fieldName);
			setValue(field, instance, valuesMap.get(fieldName),getHibernateType(fieldName,hibernateType));
		}
		
		return instance;
	}
	
	private Type getHibernateType(String fieldName, ComponentType hibernateType) {
		return hibernateType.getSubtypes()[hibernateType.getPropertyIndex(fieldName)];
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setPrimitivesValues(Field field, Object instance, Object objectValue, Type hibernateType) throws Exception {
		Object value;
		if (hibernateType.isEntityType()) {
			value = createOrUseInstance(field.getType(),(String)objectValue);								
		} else if (hibernateType instanceof BigDecimalType) {
			value = new BigDecimal((Double)objectValue);
		} else if (Enum.class.isAssignableFrom(field.getType())) {
			value = Enum.valueOf((Class<Enum>)field.getType(), (String)objectValue);								
		} else if (hibernateType instanceof DateType || hibernateType instanceof TimestampType) {
			value = new DateDeserializer().deserialize(objectValue);
		} else if (hibernateType instanceof LongType) { 
			value = Long.valueOf(objectValue.toString());
		} else if (hibernateType instanceof DoubleType) { 
			value = Double.parseDouble(objectValue.toString());
		} else {
			value = objectValue; 
		} 
		field.set(instance, value);
	}
	
	@SuppressWarnings("unchecked")
	private void setValue(Field field, Object instance, Object object, Type hibernateType) throws Exception{
		
		boolean isAccessible = field.isAccessible();
		field.setAccessible(true);
		
		if (hibernateType != null && hibernateType.isCollectionType()) {
			if (hibernateType instanceof MapType) {
				setMapValues(field, instance, object, hibernateType);
			} else {
				setListValues(field, instance, object, hibernateType);
			}
		} else if(hibernateType instanceof ComponentType) {
			field.set(instance, parseEmbbed(field.getType(),(Map<String, Object>) object, (ComponentType) hibernateType));
		} else {
			setPrimitivesValues(field,instance,object,hibernateType);
		}
		field.setAccessible(isAccessible);
	}

	private void setListValues(Field field, Object instance, Object object, Type hibernateType) throws Exception {
		BagType bagType = (BagType)hibernateType;
		List<Object> list = new ArrayList<Object>();
		field.set(instance, list);
		
		Class bagTypeClass = null; 
		boolean isBagAssociated = false;
		try {
			bagTypeClass = Class.forName(bagType.getAssociatedEntityName((SessionFactoryImplementor) getSessionFactory()));
			isBagAssociated = true;
		} catch (Exception e) {
			isBagAssociated = false;
			bagTypeClass = (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
		}
		
		for (String referenceName : (List<String>)object) {
			Object reference;
			if (isBagAssociated) {
				reference = createOrUseInstance(bagTypeClass ,referenceName);
			} else {
				reference = referenceName;
			}
			list.add(reference);
		}
	}

	@SuppressWarnings({ "rawtypes", "deprecation", "unchecked" })
	private void setMapValues(Field field, Object instance, Object object, Type hibernateType) throws Exception{
		@SuppressWarnings("unused")
		MapType mapType = (MapType)hibernateType;
		@SuppressWarnings("unused")
		Map map = new HashMap();
		
		MapKeyManyToMany keyMap = field.getAnnotation(MapKeyManyToMany.class);
		CollectionOfElements valueCollection = field.getAnnotation(CollectionOfElements.class);
		
		ClassMetadata keyMetaData = getSessionFactory().getClassMetadata(keyMap.targetEntity());
		
		Type valueType = mapType.getElementType((SessionFactoryImplementor)getSessionFactory());
		
		
		for (Entry<Object,Object> entry : ((Map<Object,Object>)object).entrySet()) {
			Object value;
			Object key;
			if (valueType.isEntityType()) {
				value = createOrUseInstance(valueCollection.targetElement(),(String)entry.getValue());
			} else {
				value = entry.getValue();
			}
		
			if (keyMetaData!=null) {
				key = createOrUseInstance(keyMap.targetEntity(),(String)entry.getKey());
			} else {
				key = entry.getKey();
			}
			
			map.put(key, value);
		}
		field.set(instance, map);
	}

	private void mapClassWithAlias(
			Map<String, Map<String, Map<String, Object>>> yamlFixtures) throws ClassNotFoundException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
		for(String className: yamlFixtures.keySet()) {
			ClassMetadata entityMetaData = getClassMetadata(className);
			Class entityClass = Class.forName(entityMetaData.getEntityName());
			for (String alias: yamlFixtures.get(className).keySet()) {
				createOrUseInstance(entityClass, alias);
			}
		}
		
	}

	@SuppressWarnings("rawtypes")
	private Field getField(Class entityClass, String fieldName) {
		try {
			return entityClass.getDeclaredField(fieldName);
		} catch (Throwable e) {
			try {
				return entityClass.getField(fieldName);
			} catch (Throwable e2) {
				if (entityClass.getSuperclass()!=null) {
					return getField(entityClass.getSuperclass(), fieldName);
				} else {
					throw new RuntimeException("Field "+fieldName+" doesn't exist");
				}
			}
		}
	}

	private void checkForAlias() {
		for (Entry<String,Object> entry: references.entrySet()) {
			if (!objects.containsKey(entry.getKey())) {
				throw new RuntimeException("Alias "+entry.getKey()+" is referenced but not is defined");
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected Object createOrUseInstance(@SuppressWarnings("rawtypes") Class entityClass, String key) throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
		if (!references.containsKey(key)) {
			Constructor<?> constructor = entityClass.getConstructor(new Class[] { });
			Object instance = constructor.newInstance(new Object[] {});
			references.put(key,instance);
		}
		return references.get(key);
	}

	@SuppressWarnings("unchecked")
	protected ClassMetadata getClassMetadata(String entityTypeName) {
		Map<String, ClassMetadata> allClassMetadata = getSessionFactory().getAllClassMetadata();
		
		List<ClassMetadata> classes = new ArrayList<ClassMetadata>();
		
		for (String typeName : allClassMetadata.keySet()) {
			if (typeName.endsWith("."+entityTypeName)) {
				classes.add(allClassMetadata.get(typeName));
			}
		}
		
		if (classes.isEmpty()) { 
			throw new RuntimeException(entityTypeName+" is not a valid Entity");
		}
		
		if (classes.size() > 1) {
			String types = "";
			for (ClassMetadata classMetadata : classes) {
				types += classMetadata.getEntityName()+"\n";
			}
			throw new RuntimeException("Exist too many options to "+entityTypeName+". "+types);
		}
		
		return classes.get(0);
	}

}

