package application.annotations;

public @interface GenerateRepositories {
	String domainPackagePath();
	String repositoryPackagePath() default "repositories";
	String repositorySuffix() default "Repo";
	Class<?> repositoryInterface();
}
