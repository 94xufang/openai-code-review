package com.xufun.sdk.infrastructure.git;

import com.xufun.sdk.utils.RandomStingUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Git操作工具类，负责：
 * 1. 获取代码变更（diff）
 * 2. 提交和推送评审日志

 * 需要配置的参数：
 * 【固定配置，需要手动提供】
 * - githubReviewLogUri：存放评审日志的仓库地址
 * - githubToken：操作仓库所需的访问令牌

 * 【动态信息，在CI/CD环境中可自动获取】
 * - project：项目名称
 * - branch：分支名称
 * - author：提交作者
 * - message：提交信息

 * 在GitHub Actions中的使用方式：
 * 1. 固定配置从secrets中读取
 * 2. 动态信息通过工作流脚本从Git上下文获取并设置到环境变量
 */
public class GitCommand {

    private final Logger logger = LoggerFactory.getLogger(GitCommand.class);

    private final String githubReviewLogUri;

    private final String githubToken;

    private final String project;

    private final String branch;

    private final String author;

    private final String message;

    public GitCommand(String githubReviewLogUri, String githubToken, String project, String branch, String author, String message) {
        this.githubReviewLogUri = githubReviewLogUri;
        this.githubToken = githubToken;
        this.project = project;
        this.branch = branch;
        this.author = author;
        this.message = message;
    }

    /**
     * 获取最新一次 Git 提交与上一次提交之间的代码变更内容
     * @return
     */
    public String diff() throws IOException, InterruptedException {
        // 1. 当前目录是否为 Git 仓库
        ProcessBuilder isGitProcessBuilder = new ProcessBuilder("git", "rev-parse", "--git-dir");
        isGitProcessBuilder.directory(new File("."));
        isGitProcessBuilder.redirectErrorStream(true);
        Process isGitProcess = isGitProcessBuilder.start();
        int isGitExit = isGitProcess.waitFor();
        
        if (isGitExit != 0) {
            throw new RuntimeException("Current directory is not a git repository");
        }

        // 2. 最新提交 hash
        ProcessBuilder logProcessBuilder = new ProcessBuilder("git", "rev-parse", "HEAD");
        logProcessBuilder.directory(new File("."));
        Process logProcess = logProcessBuilder.start();

        BufferedReader logReader = new BufferedReader(new InputStreamReader(logProcess.getInputStream()));
        String latestCommitHash = logReader.readLine();
        logReader.close();
        int logExit = logProcess.waitFor();
        
        if (logExit != 0) {
            throw new RuntimeException("Failed to get latest commit");
        }

        if (latestCommitHash == null || latestCommitHash.isEmpty()) {
            throw new RuntimeException("No commit found in repository");
        }
        latestCommitHash = latestCommitHash.trim();

        // 3. 与上一提交对比；若无父提交则与空树对比（首提交场景）
        ProcessBuilder checkProcessBuilder = new ProcessBuilder("git", "rev-parse", latestCommitHash + "^");
        checkProcessBuilder.directory(new File("."));
        Process checkProcess = checkProcessBuilder.start();
        int checkExit = checkProcess.waitFor();

        ProcessBuilder diffProcessBuilder;

        if (checkExit == 0) {
            diffProcessBuilder = new ProcessBuilder("git", "diff", latestCommitHash + "^", latestCommitHash);
        } else {
            diffProcessBuilder = new ProcessBuilder("git", "diff", "4b825dc642cb6eb9a060e54bf8d69288fbee4904", latestCommitHash);
        }

        diffProcessBuilder.directory(new File("."));
        diffProcessBuilder.redirectErrorStream(true);
        Process diffProcess = diffProcessBuilder.start();

        BufferedReader diffReader = new BufferedReader(new InputStreamReader(diffProcess.getInputStream()));
        StringBuilder diffCode = new StringBuilder();
        String line;
        while ((line = diffReader.readLine()) != null) {
            diffCode.append(line).append("\n");
        }
        diffReader.close();

        int exitCode = diffProcess.waitFor();
        if (exitCode != 0) {
            logger.error("git diff failed with exit code: {}, command: {}", exitCode, String.join(" ", diffProcessBuilder.command()));
            throw new RuntimeException("git diff failed with exit code: " + exitCode + ". Please check if the repository has commits and git is working correctly.");
        }

        return diffCode.toString();
    }

    /**
     * 将 AI 生成的代码评审结果提交到一个专门的日志仓库中，并返回可访问的 URL 。
     * @param recommend
     * @return
     */
    public String commitAndPush(String recommend) throws Exception {
        Git git = Git.cloneRepository()
                .setURI(githubReviewLogUri + ".git")
                .setDirectory(new File("repo"))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubToken, ""))
                .call();

        String dateFolderName = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File dateFolder = new File("repo/" + dateFolderName);
        if(!dateFolder.exists()) {
            dateFolder.mkdirs();
        }

        // 项目-分支-作者-时间戳-随机后缀，避免并发冲突
        String fileName = project + "-" +
                          branch + "-" +
                          author +
                          System.currentTimeMillis() + "-" +
                          RandomStingUtils.randomNumeric(4) + ".md";
        
        //第四步：写入评审内容
        File newFile = new File(dateFolder, fileName);
        try (FileWriter fileWriter = new FileWriter(newFile)) {
            fileWriter.write(recommend);
            fileWriter.flush();
        }

        git.add().addFilepattern(dateFolderName + "/" + fileName).call();
        git.commit().setMessage("add code review new file").call();
        git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubToken, "")).call();

        return githubReviewLogUri + "/blob/master/" + dateFolderName + "/" + fileName;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }

    public String getBranch() {
        return branch;
    }

    public String getProject() {
        return project;
    }

}
