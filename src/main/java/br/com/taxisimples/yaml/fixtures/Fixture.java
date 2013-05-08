package br.com.taxisimples.yaml.fixtures;

import java.io.InputStream;

public interface Fixture {

	<T> T load(String nome);

	@Deprecated
	void addScenario(Object path, String string);

	void addScenario(InputStream yamlFile);


}
