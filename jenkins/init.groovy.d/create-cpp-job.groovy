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

// ジョブにビルドトリガー用の認証トークンを設定する (ローカルリポジトリからのビルドトリガーで使用するため)
def authTokenField = WorkflowJob.getDeclaredField('authToken')
authTokenField.accessible = true
authTokenField.set(job, new hudson.model.BuildAuthorizationToken(triggerToken))

// local bare repo から Jenkinsfile を読み込むように設定する
def definition = new CpsScmFlowDefinition(scm, 'Jenkinsfile')
definition.setLightweight(true)
job.setDefinition(definition)
job.save()

// デバッグ用ログ
println "Configured Jenkins pipeline job: ${jobName} from ${repoUrl} (${branchSpec}), token from ${triggerTokenFile}"
