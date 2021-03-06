package io.cloudpipelines.projectcrawler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcabi.github.Coordinates;
import com.jcabi.github.Github;
import com.jcabi.github.MyRtGithub;
import com.jcabi.http.Response;
import com.jcabi.http.wire.RetryWire;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Marcin Grzejszczak
 */
class GithubRepositoryManagementBuilder implements RepositoryManagementBuilder {

	private static final Logger log = LoggerFactory.getLogger(GithubRepositoryManagementBuilder.class);

	@Override public RepositoryManagement build(Options options) {
		boolean applicable = isApplicable(options.rootUrl);
		if (applicable) {
			return createNewRepoManagement(options);
		}
		if (options.repository != Repositories.GITHUB) {
			return null;
		}
		return createNewRepoManagement(options);
	}

	RepositoryManagement createNewRepoManagement(Options options) {
		return new GithubRepositoryManagement(options);
	}

	private boolean isApplicable(String url) {
		boolean applicable = StringUtils.isNotBlank(url) && url.contains("github");
		if (log.isDebugEnabled()) {
			log.debug("URL [{}] is applicable [{}]", url, applicable);
		}
		return applicable;
	}
}

class GithubRepositoryManagement implements RepositoryManagement {

	private static final Logger log = LoggerFactory.getLogger(GithubRepositoryManagement.class);

	private final Github github;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Options options;

	GithubRepositoryManagement(Options options) {
		this.github = new MyRtGithub(github(options)
				.entry().through(RetryWire.class));
		this.options = options;
	}

	GithubRepositoryManagement(Github github, Options options) {
		this.github = github;
		this.options = options;
	}

	private Github github(Options options) {
		if (StringUtils.isNotBlank(options.token)) {
			log.info("Token passed to github client");
			return new MyRtGithub(options.token);
		}
		if (StringUtils.isNotBlank(options.username)) {
			log.info("Username and password passed to github client");
			return new MyRtGithub(options.username, options.password);
		}
		log.info("No security passed to github client");
		return new MyRtGithub();
	}

	@Override public List<Repository> repositories(String org) {
		try {
			List<String> responses = orgRepos(org);
			List<Map> map = responses.stream()
					.map(this::read)
					.flatMap(Collection::stream)
					.collect(Collectors.toList());
			List<Repository> repositories = allNonFilteredOutProjects(map);
			return addManuallySetProjects(org, repositories);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private List<Map> read(String response) {
		try {
			return this.objectMapper.readValue(response, List.class);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private List<Repository> allNonFilteredOutProjects(List<Map> map) {
		return map.stream()
						.map(entry -> new Repository(
								options.projectName(entry.get("name").toString()),
								entry.get("ssh_url").toString(),
								entry.get("clone_url").toString(),
								"master"))
						.filter(repo -> !options.isIgnored(repo.name))
						.collect(Collectors.toList());
	}

	private List<Repository> addManuallySetProjects(String org, List<Repository> repositories) {
		repositories.addAll(this.options.projects
				.stream().map(pb -> new Repository(options.projectName(pb.projectName),
				sshKey(org, pb), cloneUrl(org, pb), pb.branch))
				.collect(Collectors.toSet()));
		return repositories;
	}

	private String sshKey(String org, ProjectAndBranch pb) {
		return "git@github.com:" + org + "/" + pb.project + ".git";
	}

	private String cloneUrl(String org, ProjectAndBranch pb) {
		return "https://github.com/" + org + "/" + pb.project + ".git";
	}

	List<String> orgRepos(String org) throws IOException {
		List<String> repos = new ArrayList<>();
		Response response = null;
		int page = 1;
		while (response == null || hasNextLink(response)) {
			log.info("Grabbing page [" + page + "]");
			response = fetchOrgsRepo(org, page);
			if (response.status() == 404) {
				log.warn("Got 404, will assume that org is actually a user");
				response = fetchUsersRepo(org, page);
				if (response.status() >= 400) {
					throw new IllegalStateException("Status [" + response.status() + "] was returned for orgs and users");
				}
			}
			repos.add(response.body());
			page = page + 1;
		}
		return repos;
	}

	private boolean hasNextLink(Response response) {
		List<String> link = response.headers().get("Link");
		if (link == null || link.isEmpty()) {
			return false;
		}
		return link.get(0).contains("rel=\"next\"");
	}

	private Response fetchUsersRepo(String org, int page) throws IOException {
		return this.github.entry().method("GET").uri()
				.path("users/" + org + "/repos")
				.queryParam("page", page)
				.back().fetch();
	}

	private Response fetchOrgsRepo(String org, int page) throws IOException {
		return this.github.entry().method("GET").uri()
				.path("orgs/" + org + "/repos")
				.queryParam("page", page).back().fetch();
	}

	@Override public String fileContent(String org, String repo,
			String branch, String filePath) {
		try {
			String content = new java.util.Scanner(
					getFileContent(org, repo, branch, filePath))
					.useDelimiter("\\A").next();
			if (log.isDebugEnabled()) {
				log.debug("File [{}] for branch [{}] org [{}] and repo [{}] exists",
						filePath, branch, org, repo);
			}
			return content;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}  catch (AssertionError | Exception e) {
			log.warn("Exception [{}] occurred when retrieving file [{}] for branch [{}] org [{}] and repo [{}]",
					e, filePath, branch, org, repo);
			return "";
		}
	}

	InputStream getFileContent(String org, String repo, String branch,
			String filePath) throws IOException {
		return this.github.repos().get(
				new Coordinates.Simple(org, repo))
				.contents().get(filePath, branch).raw();
	}

	boolean descriptorExists(String org, String repo, String branch,
			String filePath) throws IOException {
		return this.github.repos().get(
				new Coordinates.Simple(org, repo))
				.contents()
				.exists(filePath, branch);
	}
}


