def call(Map config = [:]) {
    /*
        config.jarPKGName
        config.tempJarName
        config.deploySVCName
        config.jdkVersion
        config.project
        config.deployJarPKGName
        config.dockerFile
    */
    def String harborAuth = 'd1de0610-67b2-43ce-8ad9-09ca666cb877'
    def String officeRegistry = '192.168.31.199:11180'
    def String prodRegistry = 'harbor.betack.com'
    def Map imageDict = [:]
    // 创建构建docker镜像用的临时目录
    sh """
        [ -d temp_docker_build_dir ] || mkdir temp_docker_build_dir
        cp ${config.jarPKGName} temp_docker_build_dir/${config.tempJarName}
    """
    println("是否从Jenkins传入dockerfile： " + config.containsKey("dockerFile"))
    dir("${env.WORKSPACE}/temp_docker_build_dir") {
        if (config.containsKey("dockerFile") == false) {
            echo "构建服务：${config.deploySVCName} 的docker镜像"
            dockerFile = """
                FROM ${config.jdkVersion}
                LABEL maintainer="colin" version="1.0" datetime="2024-07-18"
                RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
                    echo "Asia/Shanghai" > /etc/timezone
                COPY ${config.tempJarName} /opt/betalpha/${config.deployJarPKGName}
                WORKDIR /opt/betalpha
            """.stripIndent()
            println("使用Jenkins共享库中的dockerfile")
        } else {
            dockerFile = config.get('dockerFile')
            println("使用Jenkins中传入的dockerfile")
        }

        writeFile file: 'Dockerfile', text: "${dockerFile}", encoding: 'UTF-8'
        // 追加，把 Arthas Java 诊断工具打包到镜像中
        def _ARTHAS_TOOLS = Boolean.valueOf("${params.ARTHAS_TOOLS}")
        if (_ARTHAS_TOOLS) {
            sh "echo 'COPY --from=hengyunabc/arthas:latest /opt/arthas /opt/arthas' >> Dockerfile"
        }

        sh 'pwd; ls -lh Dockerfile; cat Dockerfile'

        withCredentials([usernamePassword(credentialsId: "${harborAuth}", passwordVariable: 'password', usernameVariable: 'username')]) {
            imageName = "${officeRegistry}/${config.project}/${config.deploySVCName}:${env.COMMIT_SHORT_ID}-${BUILD_NUMBER}"
            sh """
                pwd; ls -lh
                nerdctl login -u ${username} -p '${password}' ${officeRegistry}
                nerdctl image build -t ${imageName} -f Dockerfile .
                nerdctl image push ${imageName}
                #nerdctl image tag ${imageName} "${officeRegistry}/${config.project}/${config.deploySVCName}:latest"
                #nerdctl image push "${officeRegistry}/${config.project}/${config.deploySVCName}:latest"
            """
            imageDict.put('imageName', imageName)

            // 推送镜像到hwcould仓库：harbor.betack.com
            def _EXTRANET_HARBOR = Boolean.valueOf("${params.EXTRANET_HARBOR}")
            if (_EXTRANET_HARBOR) {
                extranetImageName = "${prodRegistry}/${config.project}/${config.deploySVCName}:${env.COMMIT_SHORT_ID}-${BUILD_NUMBER}"
                sh """
                    nerdctl login -u ${username} -p '${password}' ${prodRegistry}
                    nerdctl image tag ${imageName} ${extranetImageName}
                    nerdctl image push ${extranetImageName}
                    nerdctl image rm ${extranetImageName}
                """
                imageDict.put('extranetImageName', extranetImageName)
            }
        }

        // 镜像打包后，清理jar包，减少docker build上下文，清理构建环境的镜像节约磁盘空间
        sh """
            rm -f ${config.tempJarName}
            #nerdctl image rm ${imageName}
            #nerdctl image rm ${officeRegistry}/${config.project}/${config.deploySVCName}:latest
        """
    }
    return imageDict
}
