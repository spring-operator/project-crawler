package io.cloudpipelines.projectcrawler;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */

class GithubRepositoryManagementBuilderTests {

	GithubRepositoryManagementBuilder sut = new GithubRepositoryManagementBuilder();

	@Test
	void should_return_false_when_url_is_empty() {
		then(sut.build(OptionsBuilder.builder().build())).isNull();
	}

	@Test
	void should_return_false_when_url_does_not_contain_github() {
		then(sut.build(OptionsBuilder.builder().rootUrl("foo").build())).isNull();
	}

	@Test
	void should_return_true_when_repositories_is_github_as_enum() {
		then(githubBuilder().build(OptionsBuilder.builder().rootUrl("foo")
				.repository(Repositories.GITHUB).build())).isNotNull();
	}

	@Test
	void should_return_true_when_repositories_is_github() {
		then(githubBuilder().build(OptionsBuilder.builder().rootUrl("foo")
				.repository("github").build())).isNotNull();
	}

	@Test
	void should_return_true_when_url_contains_github() {
		then(githubBuilder().build(OptionsBuilder.builder()
				.rootUrl("http://github").build())).isNotNull();
	}

	@Test
	@Disabled
	void should_call_the_real_thing_via_org() {
		then(new GithubRepositoryManagementBuilder().build(
				OptionsBuilder.builder()
						.exclude(".*")
						.project("github-webook")
						.rootUrl("http://github")
						.build())
				.repositories("spring-cloud")).isNotNull();
	}

	@Test
	@Disabled
	void should_call_the_real_thing_via_username() {
		then(new GithubRepositoryManagementBuilder().build(
				OptionsBuilder.builder()
						.exclude(".*")
						.project("github-webook")
						.rootUrl("http://github")
						.build())
				.repositories("marcingrzejszczak")).isNotNull();
	}

	@Test
	@Disabled
	void should_call_the_real_thing_to_get_a_file() {
		then(new GithubRepositoryManagementBuilder().build(
				OptionsBuilder.builder()
						.rootUrl("http://github")
						.build())
				.fileContent("marcingrzejszczak",
						"github-webhook", "master", "sc-pipelines.yml")).isNotNull();
	}

	private GithubRepositoryManagementBuilder githubBuilder() {
		return new GithubRepositoryManagementBuilder() {
			@Override RepositoryManagement createNewRepoManagement(Options options) {
				return new RepositoryManagement() {
					@Override public List<Repository> repositories(String org) {
						return null;
					}

					@Override public String fileContent(String org, String repo,
							String branch, String filePath) {
						return null;
					}
				};
			}
		};
	}

}