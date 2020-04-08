package application.services;

import java.nio.file.Path;

import javax.lang.model.element.Element;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import application.annotations.GenerateRepositories;
import application.processors.AnnotationException;

public class GenerateRepositoriesService extends ProcessorTemplate<GenerateRepositories> {
	private ClassFinder classFinder;
	
	//annotation fields
	private String domainPackage;
	
	public GenerateRepositoriesService() {
		classFinder=new ClassFinder(annotationElement, GenerateRepositories.class);
	}
	
	public String serviceAnnotation(Element annotationElement, Path path) throws AnnotationException {
		return processAnnotation(annotationElement, path, GenerateRepositories.class);
	}

	@Override
	public void step1_injectAnnotation(GenerateRepositories annotation) throws AnnotationException {
		domainPackage=annotation.domainPackage();
		if(domainPackage.equals(""))
			throw new AnnotationException("DomainPackage attribute must not be empty!", annotationElement, GenerateRepositories.class);
	}

	@Override
	public void step2_processing(ClassOrInterfaceDeclaration cls, String className) throws AnnotationException {
//		classFinder.getAnnotatedClassesFromPackage(domainPackage, "Entity");
		//TODO - 8 kwi 2020:coś w tej metodzie wywołuje nullpointer
	}

	

	//	public interface AgeCateforyRepo extends CrudRepository<AgeCategory,Integer> {
}
