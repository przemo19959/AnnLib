package application.services.general;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Path;

import javax.lang.model.element.Element;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import application.processors.AnnotationException;

public class ProcessorTemplate<T extends Annotation> {
	protected boolean codeChanged;
	protected Element annotatedElement;
	protected Class<T> annotation;
	
	//@formatter:off
	public void step1_injectAnnotation(@SuppressWarnings("unused") T annotation){};
	@SuppressWarnings("unused")
	public void step2_processing(ClassOrInterfaceDeclaration cls,String className){};
	//@formatter:on
	
	
	public String processAnnotation(Element annotatedElement, Path path, Class<T> annotation) throws AnnotationException {
		this.annotatedElement = annotatedElement;
		this.annotation=annotation;
		String className=annotatedElement.getSimpleName().toString();
		codeChanged = false;

		step1_injectAnnotation(annotatedElement.getAnnotation(annotation));
		try {
			CompilationUnit cu = StaticJavaParser.parse(path);
			ClassOrInterfaceDeclaration cls = cu.getClassByName(className).orElse(null);
			if(cls != null) {
				step2_processing(cls,className);
				if(codeChanged)
					return cu.toString();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return "no code";
	}
	
	public void checkArgument(boolean condition, String message){
		if(condition==false)
			throw new AnnotationException(message, annotatedElement, annotation);
	}

}
