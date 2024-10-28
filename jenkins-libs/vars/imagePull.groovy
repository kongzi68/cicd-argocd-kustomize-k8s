def call(Map config = [:]) {
    /*
        源镜像仓库、目的镜像仓库需要用相同的账号
        常用传入值：
        congig.imageSRCHarbor
        congig.imageDSTHarbor
        config.project
        config.deploySVCName
        config.imageTag
    */
    def Map imageDict = [:]
    def String harborAuth = 'd1de0610-67b2-43ce-8ad9-09ca666cb877'
    // 设置容器镜像仓库默认值
    if(!config.containsKey("imageSRCHarbor")){
        config.put("imageSRCHarbor","192.168.189.199:11180")
    }
    if(!config.containsKey("imageDSTHarbor")){
        config.put("imageDSTHarbor","harbor.betack.com")
    }
    imageName = "${config.imageSRCHarbor}/${config.project}/${config.deploySVCName}:${config.imageTag}"
    extranetImageName = "${config.imageDSTHarbor}/${config.project}/${config.deploySVCName}:${config.imageTag}"
    println("imageName: " + imageName)
    println("extranetImageName: " + extranetImageName)
    imageDict.put('imageName', imageName)
    imageDict.put('extranetImageName', extranetImageName)
    withCredentials([usernamePassword(credentialsId: harborAuth, passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
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
                nerdctl login -u ${USERNAME} -p '${PASSWORD}' ${congig.imageSRCHarbor}
                nerdctl image pull ${imageName}
                nerdctl image tag ${imageName} ${extranetImageName}
                nerdctl login -u ${USERNAME} -p '${PASSWORD}' ${congig.imageDSTHarbor}
                nerdctl image push ${extranetImageName}
                #nerdctl login -u ${USERNAME} -p '${PASSWORD}' ${congig.imageDSTHarbor} --tls-verify=false
                #nerdctl image push ${extranetImageName} --tls-verify=false
                nerdctl image rm -f ${imageName} ${extranetImageName}
            """
        }
        return imageDict
    }
}
