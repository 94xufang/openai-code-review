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
        //第一步：获取最新提交的哈希值
        ProcessBuilder logProcessBuilder = new ProcessBuilder("git","log","-1","--pretty=format=%H");
        logProcessBuilder.directory(new File("."));
        Process logProcess = logProcessBuilder.start();
        //第二步：读取提交哈希值
        BufferedReader logReader = new BufferedReader(new InputStreamReader(logProcess.getInputStream()));
        String latestCommitHash = logReader.readLine();
        logReader.close();
        logProcess.waitFor();
        //第三步：执行 Git Diff 命令
        ProcessBuilder diffProcessBuilder = new ProcessBuilder("git","diff",latestCommitHash + "^",latestCommitHash);
        diffProcessBuilder.directory(new File("."));
        Process diffProcess = diffProcessBuilder.start();
        //第四步：读取 Diff 输出
        BufferedReader diffReader = new BufferedReader(new InputStreamReader(diffProcess.getInputStream()));
        StringBuilder diffCode = new StringBuilder();
        String Line;
        while((Line = diffReader.readLine()) != null) {
            diffCode.append(Line).append("\n");
        }
        diffReader.close();
        //第五步：检查命令执行状态
        int exitCode = diffProcess.waitFor();
        if(exitCode != 0) {
            throw new RuntimeException("get diff failed with exit code " + exitCode);
        }
        //第六步：返回结果
        return diffCode.toString();
    }

    /**
     * 将 AI 生成的代码评审结果提交到一个专门的日志仓库中，并返回可访问的 URL 。
     * @param recommend
     * @return
     */
    public String commitAndPush(String recommend) throws Exception {
        //第一步：克隆评审日志仓库
        Git git = Git.cloneRepository()
                .setURI(githubReviewLogUri + ".git")
                .setDirectory(new File("repo"))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubToken, ""))
                .call();
        //第二步：创建按日期分类的目录
        String dateFolderName = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File dateFolder = new File("repo/" + dateFolderName);
        if(!dateFolder.exists()) {
            dateFolder.mkdirs();
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
        }
        //第五步：Git 添加文件
        git.add().addFilepattern(dateFolderName + "/" + fileName).call();
        //第六步：Git 提交
        git.commit().setMessage("add code review new file").call();
        //第七步：Git 推送
        git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubToken, "")).call();
        //第八步：记录日志
        logger.info("review commit and push finished!");
        //第九步：返回访问 URL
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
