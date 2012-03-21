package br.com.taxisimples.yaml.fixtures;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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
import org.hibernate.ejb.HibernateEntityManagerFactory;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.type.BagType;
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
						Field field = entityClass.getDeclaredField(propertieEntry.getKey());
						boolean isAccessible = field.isAccessible();
						field.setAccessible(true);
						
						if (propertieType.isCollectionType()) {
							BagType bagType = (BagType)propertieType;
							List<Object> list = new ArrayList<Object>();
							field.set(instance, list);
							for (String referenceName : (List<String>)propertieEntry.getValue()) {
								Object reference = createOrUseInstance(Class.forName(bagType.getAssociatedEntityName((SessionFactoryImplementor) getSessionFactory())),referenceName);
								list.add(reference);
							}
						} else {
							Object value;
							if (propertieType.isEntityType()) {
								value = createOrUseInstance(entityClass,propertieEntry.getKey());								
							} else {
								value = propertieEntry.getValue(); 
							}
							field.set(instance, value);
						}
						field.setAccessible(isAccessible);
					}
					
				} catch (Exception e) {
					throw new RuntimeException("Some error", e);
				}
			}			
		}
		for (Entry<String, Object> entry: objects.entrySet()) {
			Object mergedObject = entityManager.merge(entry.getValue());
			objects.put(entry.getKey(),mergedObject);
		}
		System.out.println("teste");
		
	}
	
	@SuppressWarnings("unchecked")
	protected Object createOrUseInstance(Class entityClass, String key) throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
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

