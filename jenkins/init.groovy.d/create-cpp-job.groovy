// "C++をビルドするJenkinsパイプラインジョブ"を作成するgroovy設定
import jenkins.model.Jenkins
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
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
def repoUrl = System.getenv('LOCAL_GIT_REPO_URL') ?: 'file:///workspace/.local/git/handmade-cicd.git' // ローカルリポジトリのURLを取得，無ければ手動で指定されたURLを使用 (どのみち.local/git/handmade-cicd.gitを指すはず)
def branchSpec = System.getenv('LOCAL_GIT_BRANCH') ?: '*/main' // ブランチ指定を取得
def triggerTokenFile = new File('/workspace/.local/jenkins-trigger-token') // トリガートークンをファイルから取得するためのFileオブジェクト (存在しない場合は後でデフォルト値を使用)
def triggerToken = System.getenv('LOCAL_GIT_TRIGGER_TOKEN') ?: (triggerTokenFile.exists() ? triggerTokenFile.text.trim() : 'handmade-cicd-local-trigger') // トリガートークンを環境変数から取得，無ければファイルから取得，それもなければデフォルト値を使用

// GitSCMの設定を作成する (ローカルリポジトリからJenkinsfileを読み込むための設定)
def scm = new GitSCM(
    [new UserRemoteConfig(repoUrl, null, null, null)],
    [new BranchSpec(branchSpec)],
    null,
    null,
    []
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

// ジョブにビルドトリガー用の認証トークンを設定する (ローカルリポジトリからのビルドトリガーで使用するため)
def authTokenField = WorkflowJob.getDeclaredField('authToken')
authTokenField.accessible = true
authTokenField.set(job, new hudson.model.BuildAuthorizationToken(triggerToken))
authTokenField.set(warningJob, new hudson.model.BuildAuthorizationToken(triggerToken))
authTokenField.set(errorJob, new hudson.model.BuildAuthorizationToken(triggerToken))
authTokenField.set(vulkanJob, new hudson.model.BuildAuthorizationToken(triggerToken))

// local bare repo から Jenkinsfile を読み込むように設定する
def definition = new CpsScmFlowDefinition(scm, 'Jenkinsfile_hello')
definition.setLightweight(true)
job.setDefinition(definition)
job.save()

def warningDefinition = new CpsScmFlowDefinition(scm, 'Jenkinsfile_warning')
warningDefinition.setLightweight(true)
warningJob.setDefinition(warningDefinition)
warningJob.save()

def errorDefinition = new CpsScmFlowDefinition(scm, 'Jenkinsfile_error')
errorDefinition.setLightweight(true)
errorJob.setDefinition(errorDefinition)
errorJob.save()

def vulkanDefinition = new CpsScmFlowDefinition(scm, 'Jenkinsfile_Vulkan')
vulkanDefinition.setLightweight(true)
vulkanJob.setDefinition(vulkanDefinition)
vulkanJob.save()

// デバッグ用ログ
//println "Configured Jenkins pipeline job: ${jobName} from ${repoUrl} (${branchSpec}), token from ${triggerTokenFile}"
