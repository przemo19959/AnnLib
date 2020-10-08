package application.processors;

import java.nio.file.Path;

import javax.lang.model.element.Element;

public class OperationDTO {
	private final Element annotatedElement;
	private final Path path;

	public OperationDTO(Element annotatedElement, Path path) {
		this.annotatedElement = annotatedElement;
		this.path = path;
	}

	//@formatter:off
	public Element getAnnotatedElement() {return annotatedElement;}
	public Path getPath() {return path;}
	//@formatter:on
}
