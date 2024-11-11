def call(Map config = [:]) {
    /* 提交代码到gitlab仓库
        config.projectHTTPAddr        // argocd/hx-kustomize.git
        config.branchTag
        config.commitMessage
    */
    sh """#!/bin/bash
        # 设置用户信息
        git config user.name "jenkins"
        git config user.email "jenkins@test88.com"
        # 添加并提交更改
        git add --all .
        git commit --all -m '${config.commitMessage}' || echo "No changes to commit"
    """
    // 使用 Jenkins 凭据进行推送
    withCredentials([usernamePassword(credentialsId: '3d040389-9dfe-4c0d-9dab-9f6487f10409', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
        GIT_URL="http://${GIT_USERNAME}:${GIT_PASSWORD}@192.168.31.199:50080/${config.projectHTTPAddr}"
    }
    // 推送更改到远程仓库
    sh """#!/bin/bash
        git push '${GIT_URL}' HEAD:${config.branchTag}
    """
}
