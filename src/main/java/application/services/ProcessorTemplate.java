package application.services;

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
	protected Element annotationElement;
	
	//@formatter:off
	public void step1_injectAnnotation(@SuppressWarnings("unused") T annotation) throws AnnotationException {};
	@SuppressWarnings("unused")
	public void step2_processing(ClassOrInterfaceDeclaration cls,String className) throws AnnotationException {};
	//@formatter:on
	
	
	public String processAnnotation(Element annotationElement, Path path, Class<T> processedAnnotation) throws AnnotationException {
		this.annotationElement = annotationElement;
		String className=annotationElement.getSimpleName().toString();
		codeChanged = false;
		
		step1_injectAnnotation(annotationElement.getAnnotation(processedAnnotation));
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

}
