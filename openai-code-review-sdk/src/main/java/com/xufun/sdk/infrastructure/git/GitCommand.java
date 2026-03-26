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
        logger.info("start getting git diff...");
        
        // 1️⃣ 检查当前目录是否是 git 仓库
        ProcessBuilder isGitProcessBuilder = new ProcessBuilder("git", "rev-parse", "--git-dir");
        isGitProcessBuilder.directory(new File("."));
        isGitProcessBuilder.redirectErrorStream(true);
        Process isGitProcess = isGitProcessBuilder.start();
        int isGitExit = isGitProcess.waitFor();
        
        if (isGitExit != 0) {
            throw new RuntimeException("Current directory is not a git repository");
        }
        logger.info("current directory is a git repository");
        
        // 2️⃣ 获取最新 commit hash
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
        logger.info("latest commit: {}", latestCommitHash.substring(0, 7));

        // 3️⃣ 判断是否有上一个 commit
        ProcessBuilder checkProcessBuilder = new ProcessBuilder("git", "rev-parse", latestCommitHash + "^");
        checkProcessBuilder.directory(new File("."));
        Process checkProcess = checkProcessBuilder.start();
        int checkExit = checkProcess.waitFor();

        ProcessBuilder diffProcessBuilder;

        if (checkExit == 0) {
            // 有上一个 commit
            logger.info("found previous commit, using diff with previous commit");
            diffProcessBuilder = new ProcessBuilder("git", "diff", latestCommitHash + "^", latestCommitHash);
        } else {
            // 只有一个 commit 或者是初始提交
            logger.info("only one commit found, using diff with empty tree");
            diffProcessBuilder = new ProcessBuilder("git", "diff", "4b825dc642cb6eb9a060e54bf8d69288fbee4904", latestCommitHash);
        }

        logger.info("executing git command: {}", String.join(" ", diffProcessBuilder.command()));
        diffProcessBuilder.directory(new File("."));
        diffProcessBuilder.redirectErrorStream(true);
        Process diffProcess = diffProcessBuilder.start();

        // 4️⃣ 读取输出
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
        
        logger.info("got diff code, length: {}", diffCode.length());
        return diffCode.toString();
    }

    /**
     * 将 AI 生成的代码评审结果提交到一个专门的日志仓库中，并返回可访问的 URL 。
     * @param recommend
     * @return
     */
    public String commitAndPush(String recommend) throws Exception {
        logger.info("start commitAndPush, project: {}, branch: {}", project, branch);
        
        //第一步：克隆评审日志仓库
        logger.info("cloning review log repository: {}", githubReviewLogUri);
        Git git = Git.cloneRepository()
                .setURI(githubReviewLogUri + ".git")
                .setDirectory(new File("repo"))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubToken, ""))
                .call();
        logger.info("repository cloned successfully");
        
        //第二步：创建按日期分类的目录
        String dateFolderName = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File dateFolder = new File("repo/" + dateFolderName);
        if(!dateFolder.exists()) {
            dateFolder.mkdirs();
            logger.info("created date folder: {}", dateFolderName);
        }
        
        //第三步：生成唯一的文件名
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
        logger.info("review content written to file: {}", fileName);
        
        //第五步：Git 添加文件
        git.add().addFilepattern(dateFolderName + "/" + fileName).call();
        logger.info("file added to git staging area");
        
        //第六步：Git 提交
        git.commit().setMessage("add code review new file").call();
        logger.info("file committed to local repository");
        
        //第七步：Git 推送
        git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubToken, "")).call();
        logger.info("file pushed to remote repository");
        
        //第八步：记录日志
        logger.info("review commit and push finished!");
        
        //第九步：返回访问 URL
        String logUrl = githubReviewLogUri + "/blob/master/" + dateFolderName + "/" + fileName;
        logger.info("log url: {}", logUrl);
        return logUrl;
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
