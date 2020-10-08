package application.services.general;

import java.io.File;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import application.processors.AnnLibProcessor;
import application.processors.AnnotationException;

public class ClassFinder {
	private static final String PATH_TEMPLATE = "{0}/{1}/";
	private static final String NO_ANNOTATED_CLASS_IN_PACKAGE = "Package \"{0}\" doesn''t contain any class annotated with @{1}!";
	private static final String PACKAGE_NOT_FOUND = "ERROR: Package \"{0}\" not found in classpath!";
	
	private final String projectPath;
	private final Elements elements;
	
	private String sourceFolderName;
	private Element annotationElement;
	private Class<? extends Annotation> annotationCls;

	public ClassFinder(String projectPath, Elements elements) {
		this.projectPath = projectPath;
		this.elements = elements;
	}

	//@formatter:off
	public void setSourceFolderName(String sourceFolderName) {this.sourceFolderName = sourceFolderName;}
	public <T extends Annotation> void setAnnotationCls(Class<T> annotationCls) {this.annotationCls = annotationCls;}
	public void setAnnotationElement(Element annotationElement) {this.annotationElement=annotationElement;}
	public String getFullPath() {return MessageFormat.format(PATH_TEMPLATE, projectPath, sourceFolderName);}
	//@formatter:on

	public List<TypeElement> getAnnotatedClassesFromPackage(String packageName, String lookForAnnotation) throws AnnotationException {
		List<TypeElement> annotatatedClasses = new ArrayList<>();
		packageName = packageName.replace("/", ".");
		File folder = getPackageFileBasedOnName(packageName);

		if(folder != null) {
			TypeElement typeElement = null;
			for(File file:folder.listFiles()) {
				if(isFileJavaClass(file)) {
					typeElement = elements.getTypeElement(packageName + "." + getFileNameWithoutExtention(file));
					if(isClassNotAbstractClass(typeElement) && isClassAnnotatedWith(lookForAnnotation, typeElement)) {
						annotatatedClasses.add(typeElement);
					}
				}
			}
		}
		if(annotatatedClasses.isEmpty())
			throw new AnnotationException(MessageFormat.format(NO_ANNOTATED_CLASS_IN_PACKAGE, //
				packageName, lookForAnnotation), annotationElement, annotationCls);
		return annotatatedClasses;
	}

	private boolean isClassNotAbstractClass(TypeElement typeElement) {
		return typeElement != null && typeElement.getKind().equals(ElementKind.CLASS) && typeElement.getModifiers().contains(Modifier.ABSTRACT) == false;
	}

	private boolean isClassAnnotatedWith(String annotationName, Element element) {
		for(AnnotationMirror a:element.getAnnotationMirrors()) {
			if(a.getAnnotationType().asElement().getSimpleName().toString().equals(annotationName))
				return true;
		}
		return false;
	}

	private File getPackageFileBasedOnName(String packageName) throws AnnotationException {
		Path path = Paths.get(MessageFormat.format(AnnLibProcessor.PATH_TEMPLATE, projectPath, sourceFolderName, packageName.replace(".", "/")));
		if(Files.exists(path) == false)
			throw new AnnotationException(MessageFormat.format(PACKAGE_NOT_FOUND, packageName), annotationElement, annotationCls);
		return path.toFile();
	}

	//@formatter:off	
	private static String getFileNameWithoutExtention(File file) {return file.getName().substring(0, file.getName().lastIndexOf('.'));}
	private static boolean isFileJavaClass(File file) {return file.getName().endsWith(".java");}
	//@formatter:on
}
