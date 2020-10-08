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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.StandardLocation;
import javax.tools.Diagnostic.Kind;

import com.google.auto.service.AutoService;

import application.annotations.GenerateControllers;
import application.annotations.GenerateRepositories;
import application.annotations.Singleton;
import application.annotations.ThreadTemplate;
import application.services.GenerateControllersService;
import application.services.GenerateRepositoriesService;
import application.services.SingletonService;
import application.services.ThreadTemplateService;
import application.services.general.ClassFinder;
import application.services.general.ServiceTemplate;
import lombok.SneakyThrows;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(value = {"name"})
public class AnnLibProcessor extends AbstractProcessor {
	private static final String ELAPSED_TIME_TEMPLATE = "{0} => {1}: {2}[ms]";
	private static final String NO_PROCESSING_SERVICE = "Annotation {0} has no processing service!";
	private static final String JAVA_FILE_TEMPLATE = "{0}.java";
	public static final String PATH_TEMPLATE = "{0}/{1}/{2}";
	private static final String WRONG_SOURCE_FOLDER_ERROR = "Class {0} can''t be localized! Check source folder name!";
	private static final String PROJECT_PATH_INFO = "Project path is equal to: \"{0}\".";
	private static final String NAME_OPTION_INFO = "Name option is equal to:\"{0}\".";
	public static final String NO_CODE = "no code";
	private static final String NAME_OPTION_ERROR = "Processor \"name\" option doesn't exist. Define source output generated directory in project!";
	private static final String SOURCE_FOLDER_ERROR = "Annotated element must be placed in one of following source folders: {0}!";
	private static final String[] POSSIBLE_SOURCE_FOLDERS = {"src", "src/main/java"};

	//TODO - 28 mar 2020:dodać, ewentualną walidację, aby nie wpisano słów kluczowych Javy
	public static final Pattern FIELD_NAME_PATTERN = Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*");

	// Processor API
	private Messager messager;
	private Elements elements;
	private Types types;

	// own API
	private String projectPath;
	private long start;
	private Map<String, String> options;
	private Map<String, Function<OperationDTO, String>> operations;

	//annotation services
	private ServiceTemplate<Singleton> singletonService;
	private ServiceTemplate<ThreadTemplate> threadTemplateService;
	private ServiceTemplate<GenerateRepositories> generateRepositoriesService;
	private ServiceTemplate<GenerateControllers> generateControllersService;

	//common services
	private ClassFinder classFinder;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);

		Filer filer = processingEnv.getFiler();
		options = processingEnv.getOptions();
		messager = processingEnv.getMessager();
		elements = processingEnv.getElementUtils();
		types = processingEnv.getTypeUtils();

		setProjectPath(filer);
		classFinder = new ClassFinder(projectPath, elements);

		singletonService = new SingletonService();
		threadTemplateService = new ThreadTemplateService();
		generateRepositoriesService = new GenerateRepositoriesService(classFinder, types);
		generateControllersService = new GenerateControllersService(classFinder, types);

		initOperations(); //must be last
	}

	@SneakyThrows
	private void setProjectPath(Filer filer) {
		String sOutputDirName = options.get("name");
		log(MessageFormat.format(NAME_OPTION_INFO, sOutputDirName), Kind.NOTE);
		if(sOutputDirName != null) {
			URI uri = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "tmp", (Element[]) null).toUri(); //uri: ProjectPath/target/classes/tmp
			projectPath = Paths.get(uri).getParent().getParent().toString();
			projectPath = projectPath.substring(0, projectPath.lastIndexOf("\\")).replace("\\", "/");
			log(MessageFormat.format(PROJECT_PATH_INFO, projectPath), Kind.NOTE);
		}
	}

	private void initOperations() {
		operations = new HashMap<>();
		operations.put(Singleton.class.getSimpleName(), o -> singletonService.processAnnotation(o.getAnnotatedElement(), o.getPath(), Singleton.class));
		operations.put(ThreadTemplate.class.getSimpleName(), o -> threadTemplateService.processAnnotation(o.getAnnotatedElement(), o.getPath(), ThreadTemplate.class));
		operations.put(GenerateRepositories.class.getSimpleName(), o -> generateRepositoriesService.processAnnotation(o.getAnnotatedElement(), o.getPath(), GenerateRepositories.class));
		operations.put(GenerateControllers.class.getSimpleName(), o -> generateControllersService.processAnnotation(o.getAnnotatedElement(), o.getPath(), GenerateControllers.class));
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		for(TypeElement annotation:annotations) {
			for(Element element:roundEnv.getElementsAnnotatedWith(annotation)) {

				if(projectPath == null) {
					log(NAME_OPTION_ERROR, Kind.ERROR, element, annotation);
					return true;
				}

				Path path = getPathFromCorrectSourceFolder(element);
				if(path == null) {
					log(MessageFormat.format(SOURCE_FOLDER_ERROR, Arrays.toString(POSSIBLE_SOURCE_FOLDERS)), Kind.ERROR, element, annotation);
					return true;
				}

				String newCode = NO_CODE;
				OperationDTO operationDTO = new OperationDTO(element, path);
				Function<OperationDTO, String> op = operations.get(annotation.getSimpleName().toString());
				
				if(op == null)
					log(MessageFormat.format(NO_PROCESSING_SERVICE, annotation.getSimpleName()), Kind.NOTE);
				else {
					try {
						start = System.currentTimeMillis();
						newCode = op.apply(operationDTO);
						log(MessageFormat.format(ELAPSED_TIME_TEMPLATE, path.toString(), annotation.getSimpleName(), (System.currentTimeMillis() - start)), Kind.NOTE);
						rewriteCodeIfChanged(path.toFile(), newCode);
					} catch (AnnotationException ae) {
						log(ae.getMessage(), Kind.ERROR, ae.getAnnotatedElement(), ae.getAnnotationClass());
					}
				}
			}
		}
		return true;
	}

	private void rewriteCodeIfChanged(File file, String newCode) {
		if(newCode.equals(NO_CODE) == false) {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
				bw.write(newCode);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private Path getPathFromCorrectSourceFolder(Element element) {
		Path path = null;
		String sourceFileFullName = MessageFormat.format(JAVA_FILE_TEMPLATE, element.asType().toString().replace(".", "/"));
		for(String sFolder:POSSIBLE_SOURCE_FOLDERS) {
			path = Paths.get(MessageFormat.format(PATH_TEMPLATE, projectPath, sFolder, sourceFileFullName));
			if(Files.exists(path)) {
				classFinder.setSourceFolderName(sFolder);
				break;
			}
			path = null;
		}
		if(path == null)
			log(MessageFormat.format(WRONG_SOURCE_FOLDER_ERROR, sourceFileFullName), Kind.NOTE);
		return path;
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		Set<String> result = new HashSet<>();
		result.addAll(operations.keySet().stream()//
			.map(i -> "application.annotations." + i)//
			.collect(Collectors.toSet()));
		return result;
	}

	//=============================== Logger ==================================//
	private void log(String value, Kind kind) {
		if(messager != null)
			messager.printMessage(kind, "[AnnLib] " + value);
	}

	private void log(String value, Kind kind, Element element, TypeElement annotation) {
		if(messager != null)
			messager.printMessage(kind, "[AnnLib] " + value, element, getAnnotationMirror(element, annotation.getQualifiedName().toString()));
	}

	private <A extends Annotation> void log(String value, Kind kind, Element element, Class<A> annotation) {
		if(messager != null)
			messager.printMessage(kind, "[AnnLib] " + value, element, getAnnotationMirror(element, annotation));
	}
	//==========================================================================//

	//================================ Mirror Getters ==========================//
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
	//===========================================================================//
}
