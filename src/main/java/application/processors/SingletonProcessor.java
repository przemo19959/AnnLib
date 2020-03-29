package application.processors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;
import javax.tools.Diagnostic.Kind;

import com.google.auto.service.AutoService;

import application.annotations.Singleton;
import application.services.SingletonService;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SingletonProcessor extends AbstractProcessor {
	private static final String SOURCE_FOLDER_ERROR = "Annotated element must be placed in one of following source folders: {0}!";
	private static final String[] POSSIBLE_SOURCE_FOLDERS = {"src", "src/main/java"};
	private static final List<String> PATHS = Arrays.asList("<factorypath>",
		"<factorypathentry kind=\"WKSPJAR\" id=\"/AnnLib/target/AnnLib-0.0.1-SNAPSHOT.jar\" enabled=\"true\" runInBatchMode=\"false\"/>",
		"<factorypathentry kind=\"EXTJAR\" id=\"M2_REPO\\com\\github\\javaparser\\javaparser-core\\3.15.17\\javaparser-core-3.15.17.jar\" enabled=\"true\" runInBatchMode=\"false\"/>",
		"</factorypath>");

	//TODO - 28 mar 2020:dodać, ewentualną walidację, aby nie wpisano słów kluczowych Javy
	public static final Pattern FIELD_NAME_PATTERN = Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*");
	// Processor API
	private Messager messager;

	// own API
	private String projectPath;
	private long start;

	//services
	private SingletonService singletonService;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		Filer filer = processingEnv.getFiler();
		messager = processingEnv.getMessager();

		setProjectPath(filer);
		fun();
		singletonService = new SingletonService();
	}

	private void fun() {
		log(projectPath);
		//TODO - 29 mar 2020:dodać, genrację pliku factory path (jeśli nie ma) i dodanie do niego zawartości PATHS
	}

	private void setProjectPath(Filer filer) {
		try {
			URI uri = filer.getResource(StandardLocation.SOURCE_OUTPUT, "", "").toUri();
			projectPath = Paths.get(uri).getParent().toString().replace("\\", "/");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		for(TypeElement annotation:annotations) {
			for(Element element:roundEnv.getElementsAnnotatedWith(annotation)) {

				Path path = getPathFromCorrectSourceFolder(element);
				if(path == null) {
					messager.printMessage(Kind.ERROR, MessageFormat.format(SOURCE_FOLDER_ERROR, Arrays.toString(POSSIBLE_SOURCE_FOLDERS)), element, //
						getAnnotationMirror(element, annotation.getQualifiedName().toString()));
					return true;
				}

				log(path.toString());
				String newCode = "no code";
				try {
					switch (annotation.getSimpleName().toString()) {
						case "Singleton" : {
							start = System.currentTimeMillis();
							newCode = singletonService.processAnnotation(element, //
								path, element.getSimpleName().toString());
							log("Singleton: " + (System.currentTimeMillis() - start) + "[ms]");
							break;
						}
						default :
							break;
					}

					rewriteCodeIfChanged(path.toFile(), newCode);
				} catch (AnnotationException ae) {
					messager.printMessage(Kind.ERROR, ae.getMessage(), ae.getAnnotatedElement(), getAnnotationMirror(ae.getAnnotatedElement(), ae.getAnnotationClass()));
				}
			}
		}
		return true;
	}

	private void rewriteCodeIfChanged(File file, String newCode) {
		if(newCode.equals("no code") == false) {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
				bw.write(newCode);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private Path getPathFromCorrectSourceFolder(Element element) {
		Path path = null;
		for(String sFolder:POSSIBLE_SOURCE_FOLDERS) {
			String filePath = projectPath + "/" + sFolder + "/" + element.asType().toString().replace(".", "/") + ".java";
			path = Paths.get(filePath);
			if(Files.exists(path))
				break;
			path = null;
		}
		return path;
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		Set<String> result = new HashSet<>();
		result.add(Singleton.class.getCanonicalName());
		return result;
	}

	private void log(String value) {
		if(messager != null)
			messager.printMessage(Kind.ERROR, value);
	}

	private <A extends Annotation> AnnotationMirror getAnnotationMirror(Element annotatedElement, Class<A> annotationClass) {
		for(AnnotationMirror aMirror:annotatedElement.getAnnotationMirrors()) {
			if(aMirror.getAnnotationType().toString().equals(annotationClass.getCanonicalName()))
				return aMirror;
		}
		return null;
	}

	private <A extends Annotation> AnnotationMirror getAnnotationMirror(Element annotatedElement, String annotationCanonicalName) {
		for(AnnotationMirror aMirror:annotatedElement.getAnnotationMirrors()) {
			if(aMirror.getAnnotationType().toString().equals(annotationCanonicalName))
				return aMirror;
		}
		return null;
	}
}
