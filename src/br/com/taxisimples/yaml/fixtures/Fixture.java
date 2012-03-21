package br.com.taxisimples.yaml.fixtures;

public interface Fixture {

	<T> T load(String nome);

	void addScenario(Object path, String string);

}
