// "C++をビルドするJenkinsパイプラインジョブ"を作成するgroovy設定
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def jenkins = Jenkins.get()
def jobName = 'cpp-hello'
def pipelineFile = new File('/workspace/Jenkinsfile')

if (!pipelineFile.exists()) { // Jenkinsfileが存在しない場合はジョブを作成せずにスキップ
    println "Skipping ${jobName}: /workspace/Jenkinsfile was not found"
    return
}

// ジョブが存在しない場合は新規作成、存在する場合は上書き
def job = jenkins.getItem(jobName)
if (job == null) {
    job = jenkins.createProject(WorkflowJob, jobName)
}

// Jenkinsfileの内容をジョブの定義として設定
job.setDefinition(new CpsFlowDefinition(pipelineFile.text, true))
job.save()

println "Configured Jenkins pipeline job: ${jobName}"
