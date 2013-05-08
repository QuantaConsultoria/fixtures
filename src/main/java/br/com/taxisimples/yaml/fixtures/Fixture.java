package br.com.taxisimples.yaml.fixtures;

import java.io.InputStream;

public interface Fixture {

	<T> T load(String nome);

	void addScenario(InputStream... yamlFile);

}
