package br.com.taxisimples.yaml.fixtures;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.type.BagType;
import org.hibernate.type.BigDecimalType;
import org.hibernate.type.DateType;
import org.hibernate.type.DoubleType;
import org.hibernate.type.MapType;
import org.hibernate.type.Type;
import org.ho.yaml.Yaml;
import org.springframework.stereotype.Service;

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
		return (T)objects.get(name);
	}

	@SuppressWarnings({"unchecked","rawtypes"})
	@Override
	public void addScenario(Object path, String resourceName) {
		
		Map<String,Map<String,Map<String,Object>>> yamlFixtures = (Map<String, Map<String, Map<String,Object>>>) Yaml.load(path.getClass().getResourceAsStream(resourceName));
		
		for (Entry<String, Map<String, Map<String,Object>>> entityTypesEntry: yamlFixtures.entrySet()) {
			ClassMetadata entityMetaData = getClassMetadata(entityTypesEntry.getKey());
			for (Entry<String, Map<String,Object>> entityEntry : entityTypesEntry.getValue().entrySet()) {
				if (objects.containsKey(entityEntry.getKey())) {
					throw new RuntimeException("Fixture name "+entityEntry.getKey()+" is alread used.");
				}
				try {
					Class entityClass = Class.forName(entityMetaData.getEntityName());
					Object instance = createOrUseInstance(entityClass,entityEntry.getKey());
					objects.put(entityEntry.getKey(), instance);
					
					for (Entry<String, Object> propertieEntry : entityEntry.getValue().entrySet()) {
						
						Type propertieType = entityMetaData.getPropertyType(propertieEntry.getKey());
						Field field;
						field = getField(entityClass,propertieEntry.getKey());
						
						boolean isAccessible = field.isAccessible();
						field.setAccessible(true);
						
						if (propertieType.isCollectionType()) {
							if (propertieType instanceof MapType) {
								@SuppressWarnings("unused")
								MapType mapType = (MapType)propertieType;
								@SuppressWarnings("unused")
								Map map = new HashMap();
								
								MapKeyManyToMany keyMap = field.getAnnotation(MapKeyManyToMany.class);
								CollectionOfElements valueCollection = field.getAnnotation(CollectionOfElements.class);
								
								ClassMetadata keyMetaData = getSessionFactory().getClassMetadata(keyMap.targetEntity());
								
								Type valueType = mapType.getElementType((SessionFactoryImplementor)getSessionFactory());
								
								
								for (Entry<Object,Object> entry : ((Map<Object,Object>)propertieEntry.getValue()).entrySet()) {
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
								
							} else {
								BagType bagType = (BagType)propertieType;
								List<Object> list = new ArrayList<Object>();
								field.set(instance, list);
								for (String referenceName : (List<String>)propertieEntry.getValue()) {
									Object reference = createOrUseInstance(Class.forName(bagType.getAssociatedEntityName((SessionFactoryImplementor) getSessionFactory())),referenceName);
									list.add(reference);
								}
							}
						} else {
							Object value;
							if (propertieType.isEntityType()) {
								value = createOrUseInstance(field.getType(),(String)propertieEntry.getValue());								
							} else if (propertieType instanceof BigDecimalType) {
								value = new BigDecimal((Double)propertieEntry.getValue());
							} else if (Enum.class.isAssignableFrom(field.getType())) {
								value = Enum.valueOf((Class<Enum>)field.getType(), (String)propertieEntry.getValue());								
							} else if (propertieType instanceof DateType) {
								DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
								value = format.parse(propertieEntry.getValue().toString());
							} else if (propertieType instanceof DoubleType) { 
								value = Double.parseDouble(propertieEntry.getValue().toString());
							} else {
								value = propertieEntry.getValue(); 
							} 
							field.set(instance, value);
						}
						field.setAccessible(isAccessible);
					}
					
				} catch (Throwable e) {
					throw new RuntimeException("Some error", e);
				}
			}			
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
					throw new RuntimeException("Field "+fieldName+" doesn�t exist");
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
			entityManager.persist(instance);
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

