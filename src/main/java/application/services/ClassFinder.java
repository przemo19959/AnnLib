package application.services;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;

import application.processors.AnnotationException;

public class ClassFinder {
	private static final String NO_ANNOTATED_CLASS_IN_PACKAGE = "Package {0} doesn''t contain any class annotated with {1}!";
	private static final String PACKAGE_NOT_FOUND = "ERROR: Package \"{0}\" not found in classpath!";

	private Element annotationElement;
	private Class<? extends Annotation> annotationCls;

	public <T extends Annotation> ClassFinder(Element annotationElement, Class<T> annotationCls) {
		this.annotationElement = annotationElement;
		this.annotationCls = annotationCls;
	}

	public List<Class<?>> getAnnotatedClassesFromPackage(String packageName, String lookForAnnotation) throws AnnotationException {
		List<Class<?>> entityClasses = new ArrayList<>();
		File folder = getPackageFileBasedOnName(packageName);
		if(folder != null) {
			Class<?> cls = null;
			for(File file:folder.listFiles()) {
				if(isFileJavaClass(file)) {
					cls = getClassFromString(packageName + "." + getFileNameWithoutExtention(file));
					if(cls != null) {
						for(Annotation a:cls.getAnnotations()) {
							if(a.toString().equals(lookForAnnotation)) {
								entityClasses.add(cls);
								break;
							}
						}
					}
				}
			}
		}
		if(entityClasses.isEmpty())
			throw new AnnotationException(MessageFormat.format(NO_ANNOTATED_CLASS_IN_PACKAGE, packageName, lookForAnnotation), annotationElement, annotationCls);
		return entityClasses;
	}

	private Class<?> getClassFromString(String value) {
		try {
			return Class.forName(value);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	private File getPackageFileBasedOnName(String packageName) throws AnnotationException {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		URL fileURL = classLoader.getResource(packageName.replace(".", "/"));
		if(fileURL == null)
			throw new AnnotationException(MessageFormat.format(PACKAGE_NOT_FOUND, packageName), annotationElement, annotationCls);
		return getFileFromURL(fileURL); // application.main ==> application/main
	}

	private File getFileFromURL(URL url) {
		try {
			return new File(url.toURI());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return null;
	}

	//@formatter:off	
	private static String getFileNameWithoutExtention(File file) {return file.getName().substring(0, file.getName().lastIndexOf('.'));}
	private static boolean isFileJavaClass(File file) {return file.getName().endsWith(".class") ? true : false;}
	//@formatter:on
}
