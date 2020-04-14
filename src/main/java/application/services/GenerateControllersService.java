package application.services;

import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import application.annotations.GenerateControllers;
import application.services.general.ClassFinder;
import application.services.general.ProcessorTemplate;
import application.services.general.Utils;

public class GenerateControllersService extends ProcessorTemplate<GenerateControllers> {
	private static final String EMPTY_DOMAIN_PATH = "Domain package path attribute must not be empty!";
	private static final String LOOK_FOR_ANNOTATION = "Entity";
	private static final String EMPTY_CONTROLLER_PATH = "Controller package path attribute must not be empty!";
	private static final String SUFFIX_ERROR = "Suffix must not contain spaces and be empty!";
	private static final String NOT_ANNOTATION = "Object \"{0}\" is not annotation!";

	private ClassFinder classFinder;
	private Types types;

	//annotation fields
	private String domainPackagePath;
	private String controllerPackagePath;
	private String controllerSuffix;
	private List<? extends TypeMirror> controllerAnnotations;

	public GenerateControllersService(ClassFinder classFinder, Types types) {
		this.classFinder = classFinder;
		this.types = types;
	}

	@Override
	public void step1_injectAnnotation(GenerateControllers annotation){
		domainPackagePath = annotation.domainPackagePath();
		checkArgument(domainPackagePath.equals("")==false, EMPTY_DOMAIN_PATH);
		
		controllerPackagePath = annotation.controllerPackagePath();
		checkArgument(controllerPackagePath.equals("")==false, EMPTY_CONTROLLER_PATH);
		
		controllerSuffix = annotation.controllerSuffix();
		checkArgument(controllerSuffix.equals("")==false && controllerSuffix.contains(" ")==false, SUFFIX_ERROR);
		
		controllerAnnotations=getClassAttribute(annotation);
		for(TypeMirror e:controllerAnnotations) {
			TypeElement tmp = (TypeElement) types.asElement(e);
			checkArgument(tmp.getKind().isInterface(), MessageFormat.format(NOT_ANNOTATION, tmp.getSimpleName().toString()));
		}
	}
	
	private List<? extends TypeMirror> getClassAttribute(GenerateControllers annotation) {
		try {
			annotation.controllerAnnotation();
		} catch (MirroredTypesException mte) {
			return mte.getTypeMirrors();
		}
		return null;
	}

	@Override
	public void step2_processing(ClassOrInterfaceDeclaration cls, String className){
		classFinder.setAnnotationCls(annotation);
		classFinder.setAnnotationElement(annotatedElement);
		List<TypeElement> entities = classFinder.getAnnotatedClassesFromPackage(domainPackagePath, LOOK_FOR_ANNOTATION);
		if(entities.isEmpty() == false) {
			Utils.createRepositoryPackageIfNotPresent(classFinder.getFullPath(), controllerPackagePath);

			Path path = null;
			String controllerName = "";
			boolean codeChanged = false;
			for(TypeElement entity:entities) {

				controllerName = entity.getSimpleName() + controllerSuffix;
				path = Utils.createClassFileIfNotPresent(classFinder.getFullPath(), controllerPackagePath, controllerName + ".java");
				try {
					CompilationUnit cu = StaticJavaParser.parse(path);
					codeChanged=createControllerClass(cu, controllerName);
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

	private boolean createControllerClass(CompilationUnit cu, String controllerName) {
		//by default first annotation is taken
		AnnotationExpr a1 = new MarkerAnnotationExpr(types.asElement(controllerAnnotations.get(0)).getSimpleName().toString());
		AnnotationExpr a2 = new SingleMemberAnnotationExpr()//
			.setMemberValue(new NameExpr(controllerName + ".BASE_URL"))//
			.setName("RequestMapping");

		FieldDeclaration fd = new FieldDeclaration()//
			.setModifiers(Keyword.PUBLIC, Keyword.STATIC, Keyword.FINAL)//
			.addVariable(new VariableDeclarator()//
				.setType("String")//
				.setName("BASE_URL")//
				.setInitializer(new StringLiteralExpr("/" + controllerName.replace(controllerSuffix, "").toLowerCase())));

		ClassOrInterfaceDeclaration coid = new ClassOrInterfaceDeclaration()//
			.setModifiers(Keyword.PUBLIC)//
			.setName(controllerName)//
			.addAnnotation(a1)//
			.addAnnotation(a2)//
			.addMember(fd);

		boolean result = false;

		ClassOrInterfaceDeclaration tmp = cu.getClassByName(controllerName).orElse(null);
		if(tmp != null) {
			if(tmp.getModifiers().equals(coid.getModifiers()) == false) {
				tmp.setModifiers(coid.getModifiers());
				result = true;
			}
						
			int i=controllerAnnotations.size();
			for(TypeMirror e:controllerAnnotations) {
				AnnotationExpr aTmp= new MarkerAnnotationExpr(types.asElement(e).getSimpleName().toString());
				if(Utils.findWhere(tmp.getAnnotations(), a->Utils.nameEqual(a, aTmp)) == null) {
					i--;
				}else {
					break;
				}
				if(i==0) {
					tmp.addAnnotation(a1);
					result = true;
				}
			}
			
			if(Utils.findWhere(tmp.getAnnotations(), a->Utils.nameEqual(a, a2)) == null) {
				tmp.addAnnotation(a2);
				result = true;
			}
			
			FieldDeclaration tmp2=Utils.findWhere(tmp.getFields(),f->Utils.nameEqual(0, f, fd));
			if(tmp2!=null) {
				if(tmp2.getModifiers().equals(fd.getModifiers())==false) {
					tmp2.setModifiers(fd.getModifiers());
					result=true;
				}
				
				if(tmp2.getVariable(0).getType().equals(fd.getVariable(0).getType())==false) {
					tmp2.getVariable(0).setType(fd.getVariable(0).getType());
					result=true;
				}
				
				Expression expression=tmp2.getVariable(0).getInitializer().orElse(null);
				if(expression!=null){
					if(expression.asStringLiteralExpr().getValue().equals("")) {
						expression.asStringLiteralExpr().setValue(fd.getVariable(0).getInitializer().get().asStringLiteralExpr().getValue());
						result=true;
					}
				}else {
					tmp2.getVariable(0).setInitializer(fd.getVariable(0).getInitializer().get());
					result=true;
				}
			}else {
				tmp.addMember(fd);
				result=true;
			}
		} else {
			cu.addType(coid);
			result = true;
		}
		
		PackageDeclaration pd = new PackageDeclaration().setName(controllerPackagePath.replace("/", "."));
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
		
		addImportIfNotPresent(cu, controllerAnnotations.get(0).toString());
		addImportIfNotPresent(cu, "org.springframework.web.bind.annotation.RequestMapping");

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
