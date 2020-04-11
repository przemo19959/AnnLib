package application.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation automatically generates Spring Data Repository interfaces based on classes in domain package. This annotation can be placed at any class (even one of domain class), though it is
 * better to place it on configuration class. Every not abstract class is collected from domain package. Then for every class annotated with @Entity and containing field annotated with @Id, repository
 * interface is created. Path attributes are relative to source folder. More precisely there can be two source folder names (src or src/main/java). Paths are relative to one of those. Example: source
 * folder = "src/main/java", domainPackagePath= "com.example/org" will make processor to look at "src/main/java/com/example/org" location. As directory separator can be used dot or slash as was shown
 * in example. <br>
 * <br>
 * By default there is not exclude nor include attribute. If user want to exclude some domain classes from repository generation, then he must create separate directories and set proper path
 * attribute. Best practice is to create two directories at central domain package. One with classes for repository generation and other with left classes. <br>
 * <br>
 * Because repository interface may contain some additonal queries or more extending interfaces, this annotation never is performing deletion. Rather it is adding required values when there is no
 * such. To sum it up this annotation:
 * <ul>
 * <li>creates repository interface "public interface $1+$2 extends $3<$1,$4>{...}" in package pointed by path {@link GenerateRepositories#repositoryPackagePath()} for every domain class at passed
 * path {@link GenerateRepositories#domainPackagePath()}</li>
 * <li>where:
 * <ul>
 * <li>$1 is domain class name</li>
 * <li>$2 is repository name suffix {@link GenerateRepositories#repositorySuffix()}</li>
 * <li>$3 is extended (for intended purpose one from Spring Data) repository interface set by {@link GenerateRepositories#repositoryInterface()}}</li>
 * <li>$4 is type of primary key of domain class, which is derived from field annotated by @Id</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * @author hex
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateRepositories {
	/**
	 * Relative to source folder path, where processor will look for classes annotated with @Entity and with field annotated with @Id. If set path doesn't contain any such class, exception (build
	 * error) will be thrown. If any class anotated with @Entity don't have field annotated with @Id, exception is thrown. Those exceptions contains info for user.
	 */
	String domainPackagePath();
	/**
	 * Relative to source folder path, where processor will create repository interfaces.
	 */
	String repositoryPackagePath() default "repositories";
	/**
	 * Generated repository name is build by domain class and suffix. User can set own suffix. Can't be empty or contain spaces.
	 */
	String repositorySuffix() default "Repo";
	/**
	 * Repository interface that will be extended by generated interface. Primary purpose of this annotation is to work with Spring Data repositories. Passed interface must have two generic arguments.
	 * When user passes other interface (example: Function<T,D>), then generation will work, but resulting interface will be of minimal use. When user changes repository interface as sub or super
	 * type, then old interface won't be changed. To avoid deleting extending interfaces, this annotation only adds new extending interface, so user sometimes will have to remove manually not needed
	 * interface.
	 */
	Class<?> repositoryInterface();
}
