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

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(value = {"name"})
public class AnnLibProcessor extends AbstractProcessor {
	private static final String NO_CODE = "no code";
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
	private SingletonService singletonService;
	private ThreadTemplateService threadTemplateService;
	private GenerateRepositoriesService generateRepositoriesService;
	private GenerateControllersService generateControllersService;

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
		generateControllersService = new GenerateControllersService(classFinder,types);

		initOperations(); //must be last
	}

	private void setProjectPath(Filer filer) {
		try {
			String sOutputDirName = options.get("name");
			log("Name option is equal to:\"" + sOutputDirName + "\".", Kind.NOTE);
			if(sOutputDirName != null) {
				URI uri = filer.getResource(StandardLocation.SOURCE_OUTPUT, "", "").toUri();
				projectPath = Paths.get(uri).toString().replace("\\", "/").replace(sOutputDirName, ""); //important replace order
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void initOperations() {
		operations = new HashMap<>();
		operations.put(Singleton.class.getSimpleName(), o -> singletonService.processAnnotation(o.getAnnotatedElement(), o.getPath(), Singleton.class));
		operations.put(ThreadTemplate.class.getSimpleName(), o -> threadTemplateService.processAnnotation(o.getAnnotatedElement(), o.getPath(), ThreadTemplate.class));
		operations.put(GenerateRepositories.class.getSimpleName(), o -> generateRepositoriesService.processAnnotation(o.getAnnotatedElement(), o.getPath(), GenerateRepositories.class));
		operations.put(GenerateControllers.class.getSimpleName(), o -> generateControllersService.processAnnotation(o.getAnnotatedElement(), o.getPath(), GenerateControllers.class));
	}

	private class OperationDTO {
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

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		for(TypeElement annotation:annotations) {
			for(Element element:roundEnv.getElementsAnnotatedWith(annotation)) {
				if(projectPath == null) {
					messager.printMessage(Kind.ERROR, NAME_OPTION_ERROR, element, //
						getAnnotationMirror(element, annotation.getQualifiedName().toString()));
					return true;
				}

				Path path = getPathFromCorrectSourceFolder(element);
				if(path == null) {
					messager.printMessage(Kind.ERROR, MessageFormat.format(SOURCE_FOLDER_ERROR, Arrays.toString(POSSIBLE_SOURCE_FOLDERS)), element, //
						getAnnotationMirror(element, annotation.getQualifiedName().toString()));
					return true;
				}

				log(path.toString(), Kind.NOTE);
				
				String newCode = NO_CODE;
				OperationDTO operationDTO = new OperationDTO(element, path);
				start = System.currentTimeMillis();
				Function<OperationDTO, String> op = operations.get(annotation.getSimpleName().toString());
				if(op == null)
					log("Annotation" + annotation.getSimpleName() + "has no processing service!", Kind.NOTE);
				else {
					try {
						newCode = op.apply(operationDTO);
						log(annotation.getSimpleName() + ": " + (System.currentTimeMillis() - start) + "[ms]", Kind.NOTE);

						rewriteCodeIfChanged(path.toFile(), newCode);
					} catch (AnnotationException ae) {
						messager.printMessage(Kind.ERROR, ae.getMessage(), ae.getAnnotatedElement(), getAnnotationMirror(ae.getAnnotatedElement(), ae.getAnnotationClass()));
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
		for(String sFolder:POSSIBLE_SOURCE_FOLDERS) {
			String filePath = projectPath + sFolder + "/" + element.asType().toString().replace(".", "/") + ".java";
			path = Paths.get(filePath);
			if(Files.exists(path)) {
				classFinder.setSourceFolderName(sFolder);
				break;
			}
			log("Path " + filePath + " doesn't exist!", Kind.NOTE);
			path = null;
		}
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

	private void log(String value, Kind kind) {
		if(messager != null)
			messager.printMessage(kind, "[AnnLib] " + value);
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
