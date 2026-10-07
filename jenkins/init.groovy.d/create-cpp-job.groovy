// "C++をビルドするJenkinsパイプラインジョブ"を作成するgroovy設定
import jenkins.model.Jenkins
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import hudson.plugins.git.extensions.impl.PathRestriction
import hudson.triggers.SCMTrigger
import hudson.security.AuthorizationStrategy
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob


def jenkins = Jenkins.get()
// セキュリティを無効化 (ローカルで動かすだけなのでセキュリティは気にしない)
jenkins.setSecurityRealm(null)
jenkins.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED)
// 設定保存
jenkins.save()

def jobName = 'cpp-hello'
def warning_jobName = 'cppcheck-warning'
def error_jobName = 'cppcheck-error'
def vulkan_jobName = 'vulkan'
def repoUrl = System.getenv('GITLAB_REPO_URL') ?: 'http://gitlab:8929/root/handmade-cicd.git' // GitLabのリポジトリURLを取得，無ければ同じcompose内のGitLabを使用
def branchSpec = '*/main' // とりあえずmainブランチを使用

// GitSCMの設定を作成する (GitLabからJenkinsfileを読み込むための設定)
// PathRestrictionで変更されたファイルを確認する (正規表現で指定)
def scm = new GitSCM(
    [new UserRemoteConfig(repoUrl, null, null, null)],
    [new BranchSpec(branchSpec)],
    null,
    null,
    [new PathRestriction('src/hello\\.cpp\nJenkinsfile\nJenkinsfile_hello', '')]
)

def warningScm = new GitSCM(
    [new UserRemoteConfig(repoUrl, null, null, null)],
    [new BranchSpec(branchSpec)],
    null,
    null,
    [new PathRestriction('src/overflow\\.cpp\nJenkinsfile_warning', '')]
)

def errorScm = new GitSCM(
    [new UserRemoteConfig(repoUrl, null, null, null)],
    [new BranchSpec(branchSpec)],
    null,
    null,
    [new PathRestriction('src/memleak\\.cpp\nJenkinsfile_error', '')]
)

def vulkanScm = new GitSCM(
    [new UserRemoteConfig(repoUrl, null, null, null)],
    [new BranchSpec(branchSpec)],
    null,
    null,
    [new PathRestriction('src/vulkan\\.cpp\nsrc/shader\\.slang\nJenkinsfile_Vulkan', '')]
)

// ジョブが存在しない場合は新規作成、存在する場合は上書き
def job = jenkins.getItem(jobName)
if (job == null) {
    job = jenkins.createProject(WorkflowJob, jobName)
}

def warningJob = jenkins.getItem(warning_jobName)
if (warningJob == null) {
    warningJob = jenkins.createProject(WorkflowJob, warning_jobName)
}

def errorJob = jenkins.getItem(error_jobName)
if (errorJob == null) {
    errorJob = jenkins.createProject(WorkflowJob, error_jobName)
}

def vulkanJob = jenkins.getItem(vulkan_jobName)
if (vulkanJob == null) {
    vulkanJob = jenkins.createProject(WorkflowJob, vulkan_jobName)
}

// GitLabからJenkinsfileを読み込むように設定する
// Poll SCMはスケジュールを空にし，Webhookを受けた時に変更を確認する
def definition = new CpsScmFlowDefinition(scm, 'Jenkinsfile_hello')
definition.setLightweight(true)
job.setDefinition(definition)
job.addTrigger(new SCMTrigger(''))
job.save()

def warningDefinition = new CpsScmFlowDefinition(warningScm, 'Jenkinsfile_warning')
warningDefinition.setLightweight(true)
warningJob.setDefinition(warningDefinition)
warningJob.addTrigger(new SCMTrigger(''))
warningJob.save()

def errorDefinition = new CpsScmFlowDefinition(errorScm, 'Jenkinsfile_error')
errorDefinition.setLightweight(true)
errorJob.setDefinition(errorDefinition)
errorJob.addTrigger(new SCMTrigger(''))
errorJob.save()

def vulkanDefinition = new CpsScmFlowDefinition(vulkanScm, 'Jenkinsfile_Vulkan')
vulkanDefinition.setLightweight(true)
vulkanJob.setDefinition(vulkanDefinition)
vulkanJob.addTrigger(new SCMTrigger(''))
vulkanJob.save()

// デバッグ用ログ
//println "Configured Jenkins pipeline job: ${jobName} from ${repoUrl} (${branchSpec})"
