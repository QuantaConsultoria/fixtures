package br.com.taxisimples.yaml.fixtures.serializer;


public interface Deserializer<Entidade> {
	
	Entidade deserialize(Object data);

}
