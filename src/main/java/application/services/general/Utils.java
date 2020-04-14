package application.services.general;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

public class Utils {
	public static <T> T findWhere(List<T> list, Predicate<T> predicate) {
		return list.stream().filter(predicate).findFirst().orElse(null);
	}

	public static boolean modifiersEqual(FieldDeclaration fd1, FieldDeclaration fd2) {
		return fd1.getModifiers().equals(fd2.getModifiers());
	}
	
	public static boolean modifiersEqual(ConstructorDeclaration cd1, ConstructorDeclaration cd2) {
		return cd1.getModifiers().equals(cd2.getModifiers());
	}
	
	public static boolean modifiersEqual(MethodDeclaration md1, MethodDeclaration md2) {
		return md1.getModifiers().equals(md2.getModifiers());
	}

	public static boolean typeEqual(int i, FieldDeclaration fd1, FieldDeclaration fd2) {
		return fd1.getVariable(i).getType().equals(fd2.getVariable(i).getType());
	}
	
	public static boolean typeEqual(MethodDeclaration md1, MethodDeclaration md2) {
		return md1.getType().equals(md2.getType());
	}

	public static boolean nameEqual(int i, FieldDeclaration fd1, FieldDeclaration fd2) {
		return fd1.getVariable(i).getName().equals(fd2.getVariable(i).getName());
	}
	
	public static boolean nameEqual(ConstructorDeclaration cd1, ConstructorDeclaration cd2) {
		return cd1.getName().equals(cd2.getName());
	}
	
	public static boolean nameEqual(MethodDeclaration md1, MethodDeclaration md2) {
		return md1.getName().equals(md2.getName());
	}
	
	public static boolean nameEqual(AnnotationExpr ae1, AnnotationExpr ae2) {
		return ae1.getName().equals(ae2.getName());
	}
	
	
	public static void createRepositoryPackageIfNotPresent(String fullSourcePath, String relativePath) {
		Path path = Paths.get(fullSourcePath + relativePath.replace(".", "/"));
		if(Files.exists(path) == false) {
			try {
				Files.createDirectory(path);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static Path createClassFileIfNotPresent(String fullSourcePath, String relativePath,String repoName) {
		Path path = Paths.get(fullSourcePath + relativePath.replace(".", "/") + "/" + repoName);
		if(Files.exists(path) == false) {
			try {
				Files.createFile(path);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return path;
	}
	
	public static void rewriteCodeIfChanged(File file, String newCode) {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
			bw.write(newCode);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
