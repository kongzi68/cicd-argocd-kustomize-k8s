def call(String gitAddress, String branchTag='main', Boolean isReturnCommintID=false) {
    /* 拉取代码，并返回代码提交的短ID */
    def gitAuth = '09117eaa-37c5-4a40-ac54-5516be97e9a1'
    try {
        checkout([$class: 'GitSCM',
            branches: [[name: "${branchTag}"]],
            browser: [$class: 'GitLab', repoUrl: ''],
            extensions: [[$class: 'CloneOption', depth: 1, noTags: false, reference: '', shallow: true, timeout: 30]],
            userRemoteConfigs: [[credentialsId: "${gitAuth}", url: "${gitAddress}"]]])
    } catch(Exception err) {
        echo err.getMessage()
        echo err.toString()
        error '拉取代码失败'
    }
    // 获取提交代码的ID，用于打包容器镜像
    if (isReturnCommintID) {
        def String gitCommitShortID = sh (script: 'git rev-parse --short HEAD', returnStdout: true).trim()
        return gitCommitShortID
    }
}