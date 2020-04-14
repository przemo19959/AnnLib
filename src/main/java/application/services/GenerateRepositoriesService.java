package application.services;

import java.io.IOException;
import java.nio.file.Path;
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
import application.services.general.ClassFinder;
import application.services.general.ProcessorTemplate;
import application.services.general.Utils;

public class GenerateRepositoriesService extends ProcessorTemplate<GenerateRepositories> {
	private static final String SUFFIX_ERROR = "Suffix must not contain spaces and be empty!";
	private static final String GENERIC_PARAM_ERROR = "Interface \"{0}\" don''t have two generic parameters!";
	private static final String NOT_INTERFACE = "Object \"{0}\" is not interface!";
	private static final String EMPTY_REPO_PATH = "Repository package path attribute must not be empty!";
	private static final String EMPTY_DOMAIN_PATH = "Domain package path attribute must not be empty!";
	private static final String NO_ID_IN_ENTITY = "Entity \"{0}\" doesn''t have field annotated with @{1}!";
	
	private static final String LOOK_FOR_ANNOTATION = "Entity";
	private static final String ID = "Id";

	private ClassFinder classFinder;
	private Types types;

	//annotation fields
	private String domainPackagePath;
	private String repositoryPackagePath;
	private String repositorySuffix;
	private TypeMirror repositoryInterface;

	public GenerateRepositoriesService(ClassFinder classFinder, Types types) {
		this.classFinder = classFinder;
		this.types = types;
	}

	@Override
	public void step1_injectAnnotation(GenerateRepositories annotation) {
		domainPackagePath = annotation.domainPackagePath();
		checkArgument(domainPackagePath.isEmpty() == false, EMPTY_DOMAIN_PATH);

		repositoryPackagePath = annotation.repositoryPackagePath();
		checkArgument(repositoryPackagePath.isEmpty() == false, EMPTY_REPO_PATH);

		repositorySuffix = annotation.repositorySuffix();
		checkArgument(repositorySuffix.equals("") == false && repositorySuffix.contains(" ") == false, SUFFIX_ERROR);

		repositoryInterface = getClassAttribute(annotation);
		TypeElement tmp = (TypeElement) types.asElement(repositoryInterface);
		checkArgument(tmp.getKind().isInterface(), MessageFormat.format(NOT_INTERFACE, tmp.getSimpleName().toString()));
		checkArgument(tmp.getTypeParameters().size() == 2, MessageFormat.format(GENERIC_PARAM_ERROR, tmp.getSimpleName().toString()));
	}

	@Override
	public void step2_processing(ClassOrInterfaceDeclaration cls, String className) {
		classFinder.setAnnotationElement(annotatedElement);
		classFinder.setAnnotationCls(annotation);
		List<TypeElement> entities = classFinder.getAnnotatedClassesFromPackage(domainPackagePath, LOOK_FOR_ANNOTATION);
		if(entities.isEmpty() == false) {
			Utils.createRepositoryPackageIfNotPresent(classFinder.getFullPath(), repositoryPackagePath);

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
				checkArgument(idElement != null, MessageFormat.format(NO_ID_IN_ENTITY, entity.toString(), ID));

				repoName = entity.getSimpleName() + repositorySuffix;
				path = Utils.createClassFileIfNotPresent(classFinder.getFullPath(), repositoryPackagePath, repoName + ".java");
				try {
					CompilationUnit cu = StaticJavaParser.parse(path);
					codeChanged = createRepositoryInterface(cu, repoName, entity.toString(), idElement);
					if(codeChanged) {
						Utils.rewriteCodeIfChanged(path.toFile(), cu.toString());
						codeChanged = false;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
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

	private boolean createRepositoryInterface(CompilationUnit cu, String repoName, String domainPath, Element idElement) {
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
}
