def call() {
    echo "helm登录registry仓库"
    withCredentials([usernamePassword(credentialsId: 'd1de0610-67b2-43ce-8ad9-09ca666cb877', passwordVariable: 'password', usernameVariable: 'username')]) {
        sh """
            export HELM_EXPERIMENTAL_OCI=1
            helm registry login harbor.betack.com --username ${username} --password ${password}
        """
    }
}