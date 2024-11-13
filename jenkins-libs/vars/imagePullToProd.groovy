def call(Map config = [:]) {
    /*
        config.project
        config.deploySVCName
        config.imageTag
    */
    def Map imageDict = [:]
    // def String harborAuth = 'd1de0610-67b2-43ce-8ad9-09ca666cb877'
    def String username = 'devops'
    def String password = 'iampassword'
    def String officeRegistry = 'iamIPaddr:11180'
    def String extUsername = 'devops'
    def String extPasswrod = 'iampassword'
    def String prodRegistry = 'harbor.betack.com'
    imageName = "${officeRegistry}/${config.project}/${config.deploySVCName}:${config.imageTag}"
    extranetImageName = "${prodRegistry}/${config.project}/${config.deploySVCName}:${config.imageTag}"
    println("imageName: " + imageName)
    println("extranetImageName: " + extranetImageName)
    imageDict.put('imageName', imageName)
    imageDict.put('extranetImageName', extranetImageName)
    // 第一次检查
    checkImageTagExites = checkImageTag(project: config.project, deploySVCName: config.deploySVCName, imageTag: config.imageTag)
    if (checkImageTagExites) {
        println("镜像仓库中已存在该镜像tag: ${config.imageTag}，无需再传！")
        return imageDict
    } else {
        parallelRsyncDockerImage(project: config.project, deploySVCName: config.deploySVCName, imageTag: config.imageTag, parallelJobs: '20')
    }
    // 第二次检查
    checkImageTagExites = checkImageTag(project: config.project, deploySVCName: config.deploySVCName, imageTag: config.imageTag)
    if (checkImageTagExites) {
        println("镜像仓库中已存在该镜像tag: ${config.imageTag}，无需再传！")
    } else {
        sh """
            nerdctl login -u ${username} -p '${password}' ${officeRegistry}
            nerdctl image pull ${imageName}
            nerdctl image tag ${imageName} ${extranetImageName}
            nerdctl login -u ${extUsername} -p '${extPasswrod}' ${prodRegistry}
            nerdctl image push ${extranetImageName}
            #nerdctl login -u ${extUsername} -p '${extPasswrod}' ${prodRegistry} --tls-verify=false
            #nerdctl image push ${extranetImageName} --tls-verify=false
            nerdctl image rm -f ${imageName} ${extranetImageName}
        """
    }
    return imageDict
}
