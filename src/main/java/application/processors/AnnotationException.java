package application.processors;

import java.lang.annotation.Annotation;

import javax.lang.model.element.Element;

@SuppressWarnings("serial")
public class AnnotationException extends Exception {
	private final String message;
	private final Element annotatedElement;
	private final Class<? extends Annotation> annotationClass;
	
	public AnnotationException(String message, Element annotatedElement, Class<? extends Annotation> annotationClass) {
		this.message = message;
		this.annotatedElement = annotatedElement;
		this.annotationClass = annotationClass;
	}
	
	//@formatter:off
	public String getMessage() {return message;}
	public Element getAnnotatedElement() {return annotatedElement;}
	public Class<? extends Annotation> getAnnotationClass() {return annotationClass;}
	//@formatter:on
}
