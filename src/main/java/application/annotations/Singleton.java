package application.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Singleton {
	/**
	 * name of single instance field in annotated class
	 */
	String name() default "INSTANCE";
	/**
	 * name of public static method, which returns singleton instance
	 */
	String methodName() default "getInstance";
	/**
	 * wether getInstance method is thread safe
	 */
	boolean threadSafe() default true;
	
	String[] initFields() default {};
}
