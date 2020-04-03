package application.services;

import java.util.List;
import java.util.function.Predicate;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

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
}
