package application.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;

import application.annotations.GenerateRepositories;
import application.processors.AnnotationException;

public class GenerateRepositoriesService extends ProcessorTemplate<GenerateRepositories> {
	private static final String SUFFIX_ERROR = "Suffix must not contain spaces and be empty!";
	private static final String GENERIC_PARAM_ERROR = "Interface \"{0}\" don''t have two generic parameters!";
	private static final String NOT_INTERFACE = "Object \"{0}\" is not interface!";
	private static final String EMPTY_REPO_PATH = "Repository package path attribute must not be empty!";
	private static final String EMPTY_DOMAIN_PATH = "Domain package path attribute must not be empty!";
	private static final String NO_ID_IN_ENTITY = "Entity \"{0}\" doesn''t have field annotated with @{1}!";
	private static final String LOOK_FOR_ANNOTATION = "Entity";
	private static final String ID = "Id";
	//for testing
	//	private static final String LOOK_FOR_ANNOTATION = "Singleton";
	//	private static final String ID = "XmlElement";
	
	//TODO - 11 kwi 2020:Wzorczec dla generacji kontrolera
	//@Controller
	//@RequestMapping((MyClass.BASE_URL)
	//public class MyClass{
	//...
	//}
	
	private ClassFinder classFinder;
	private Types types;

	//annotation fields
	private String domainPackage;
	private String repositoryPackagePath;
	private String repositorySuffix;
	private TypeMirror repositoryInterface;

	public GenerateRepositoriesService(ClassFinder classFinder, Types types) {
		this.classFinder = classFinder;
		this.types = types;
		classFinder.setAnnotationCls(GenerateRepositories.class);
	}

	public String serviceAnnotation(Element annotationElement, Path path) throws AnnotationException {
		return processAnnotation(annotationElement, path, GenerateRepositories.class);
	}

	@Override
	public void step1_injectAnnotation(GenerateRepositories annotation) throws AnnotationException {
		domainPackage = annotation.domainPackagePath();
		if(domainPackage.equals(""))
			throw new AnnotationException(EMPTY_DOMAIN_PATH, annotationElement, GenerateRepositories.class);
		repositoryPackagePath = annotation.repositoryPackagePath();
		if(repositoryPackagePath.equals(""))
			throw new AnnotationException(EMPTY_REPO_PATH, annotationElement, GenerateRepositories.class);
		repositorySuffix = annotation.repositorySuffix();
		if(repositorySuffix.equals("") || repositorySuffix.contains(" "))
			throw new AnnotationException(SUFFIX_ERROR, annotationElement, GenerateRepositories.class);
		repositoryInterface = getClassAttribute(annotation);
		TypeElement tmp = (TypeElement) types.asElement(repositoryInterface);
		if(tmp.getKind().isInterface() == false)
			throw new AnnotationException(MessageFormat.format(NOT_INTERFACE, tmp.getSimpleName().toString()), annotationElement, GenerateRepositories.class);
		if(tmp.getTypeParameters().size() != 2)
			throw new AnnotationException(MessageFormat.format(GENERIC_PARAM_ERROR, tmp.getSimpleName().toString()), annotationElement, GenerateRepositories.class);
	}

	@Override
	public void step2_processing(ClassOrInterfaceDeclaration cls, String className) throws AnnotationException {
		classFinder.setAnnotationElement(annotationElement);
		List<TypeElement> entities = classFinder.getAnnotatedClassesFromPackage(domainPackage, LOOK_FOR_ANNOTATION);
		if(entities.isEmpty() == false) {
			createRepositoryPackageIfNotPresent();

			Path path = null;
			String repoName = "";
			boolean codeChanged = false;
			Element idElement = null;
			for(TypeElement entity:entities) {
				idElement = entity.getEnclosedElements().stream()//
					.filter(ee -> ee.getKind().equals(ElementKind.FIELD))//
					.filter(ee -> {
						for(AnnotationMirror a:ee.getAnnotationMirrors()) {
							if(a.getAnnotationType().asElement().getSimpleName().toString().equals(ID)) {
								return true;
							}
						}
						return false;
					}).findFirst().orElse(null);
				if(idElement == null)
					throw new AnnotationException(MessageFormat.format(NO_ID_IN_ENTITY, entity.toString(), ID), annotationElement, GenerateRepositories.class);

				repoName = entity.getSimpleName() + repositorySuffix;
				path = createClassFileIfNotPresent(repoName + ".java");
				try {
					CompilationUnit cu = StaticJavaParser.parse(path);
					codeChanged = createRepositoryInterface(cu, repoName, entity.toString(), idElement);
					if(codeChanged) {
						rewriteCodeIfChanged(path.toFile(), cu.toString());
						codeChanged = false;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void rewriteCodeIfChanged(File file, String newCode) {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
			bw.write(newCode);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private TypeMirror getClassAttribute(GenerateRepositories annotation) {
		try {
			annotation.repositoryInterface();
		} catch (MirroredTypeException mte) {
			return mte.getTypeMirror();
		}
		return null;
	}

	private String getSimpleName(String fullName) {
		if(fullName.contains("."))
			return fullName.substring(fullName.lastIndexOf('.') + 1);
		return fullName;
	}

	private boolean createRepositoryInterface(CompilationUnit cu, String repoName, String domainPath, Element idElement) throws AnnotationException {
		String idElementType = idElement.asType().getKind().isPrimitive() ? types.boxedClass((PrimitiveType) idElement.asType()).toString() : idElement.asType().toString();
		ClassOrInterfaceType type = new ClassOrInterfaceType()//
			.setName(types.asElement(repositoryInterface).getSimpleName().toString())//
			.setTypeArguments(new TypeParameter(repoName.replace(repositorySuffix, "")), //
				new TypeParameter(getSimpleName(idElementType)));

		ClassOrInterfaceDeclaration coid = new ClassOrInterfaceDeclaration()//
			.setInterface(true)//
			.setModifiers(Keyword.PUBLIC)//
			.setName(repoName)//
			.addExtendedType(type);
		boolean result = false;

		ClassOrInterfaceDeclaration tmp = cu.getInterfaceByName(repoName).orElse(null);
		if(tmp != null) {
			
			ClassOrInterfaceType coit = tmp.getExtendedTypes().stream()//
				.filter(sn -> sn.getName().equals(coid.getExtendedTypes(0).getName()))//
				.findFirst().orElse(null);
			
			if(coit != null) {
				NodeList<Type> ta = coit.getTypeArguments().orElse(null);
				if(ta != null) {
					if(ta.size() != 2) {
						ta.clear();
						ta.addAll(type.getTypeArguments().get());
						result = true;
					} else if(ta.size() == 2) {
						if(ta.get(0).equals(type.getTypeArguments().get().get(0)) == false) {
							ta.set(0, type.getTypeArguments().get().get(0));
							result = true;
						}
						if(ta.get(1).equals(type.getTypeArguments().get().get(1)) == false) {
							ta.set(1, type.getTypeArguments().get().get(1));
							result = true;
						}
					}
				} else {
					coit.setTypeArguments(type.getTypeArguments().get());
					result = true;
				}
			} else {
				tmp.addExtendedType(coid.getExtendedTypes(0));
				result = true;
			}
		} else {
			cu.addType(coid);
			result = true;
		}

		PackageDeclaration pd = new PackageDeclaration().setName(repositoryPackagePath.replace("/", "."));

		PackageDeclaration tmp2 = cu.getPackageDeclaration().orElse(null);
		if(tmp2 != null) {
			if(tmp2.getName().equals(pd.getName()) == false) {
				tmp2.setName(pd.getName());
				result = true;
			}
		} else {
			cu.setPackageDeclaration(pd);
			result = true;
		}

		addImportIfNotPresent(cu, repositoryInterface.toString());
		addImportIfNotPresent(cu, domainPath);
		addImportIfNotPresent(cu, idElementType);

		return result;
	}

	private void addImportIfNotPresent(CompilationUnit cu, String importValue) {
		ImportDeclaration id = new ImportDeclaration(importValue, false, false);
		ImportDeclaration i = cu.getImports().stream()//
			.filter(i2 -> i2.getNameAsString().equals(id.getNameAsString()))//
			.findFirst().orElse(null);
		if(i == null) {
			cu.addImport(id);
		}
	}

	private Path createClassFileIfNotPresent(String repoName) {
		Path path = Paths.get(classFinder.getFullPath() + repositoryPackagePath.replace(".", "/") + "/" + repoName);
		if(Files.exists(path) == false) {
			try {
				Files.createFile(path);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return path;
	}

	private void createRepositoryPackageIfNotPresent() {
		Path path = Paths.get(classFinder.getFullPath() + repositoryPackagePath.replace(".", "/"));
		if(Files.exists(path) == false) {
			try {
				Files.createDirectory(path);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
