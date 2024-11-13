#!groovy
/* 导入Jenkins共享库，默认导入main分支 */
@Library('ck-shared-library') _
// @Library('ck-shared-library@dev') _

// 公共
def office_registry = "iamIPaddr:11180"
def prod_registry = "harbor.mydomain.com"

// 项目，ckChat
def project = "ck-chats"  // HARBAR镜像仓库中的项目名称
def git_address = "ssh://git@iamIPaddr:50022/beyond/chat_action_server.git"

def imageDict = [:]

pipeline {
  agent {
    kubernetes {
      defaultContainer 'jnlp'
      workspaceVolume persistentVolumeClaimWorkspaceVolume(claimName: 'jenkins-agent-workspace-dev', readOnly: false)
      // 注意：ubuntu-jenkins-agent 镜像，需要安装中文语言包
      yaml """
        apiVersion: v1
        kind: Pod
        metadata:
          name: jenkins-slave
          labels:
            app: jenkins-agent
        spec:
          containers:
          - name: jnlp
            image: jenkins/inbound-agent:latest-jdk17
            imagePullPolicy: Always
            resources:
              limits: {}
              requests:
                memory: "1500Mi"
                cpu: "500m"
            env:
              - name: "file.encoding"
                value: "UTF-8"
              - name: "LANG"
                value: "zh_CN.UTF-8"
              - name: "sun.jnu.encoding"
                value: "UTF-8"
            tty: true
          - name: package-tools
            image: "${office_registry}/libs/rust-uv-python:1.72-0.2.29-3.10.14"
            imagePullPolicy: Always
            env:
              - name: "file.encoding"
                value: "UTF-8"
              - name: "LANG"
                value: "zh_CN.UTF-8"
              - name: "sun.jnu.encoding"
                value: "UTF-8"
            tty: true
          - name: nerdctl
            image: "${office_registry}/libs/containerd/nerdctl:v1.7.7"
            env:
              - name: "file.encoding"
                value: "UTF-8"
              - name: "LANG"
                value: "zh_CN.UTF-8"
              - name: "sun.jnu.encoding"
                value: "UTF-8"
            securityContext:
              privileged: true
            tty: true
            volumeMounts:
              - name: buildkitd-config
                mountPath: /etc/buildkit/buildkitd.toml
                subPath: buildkitd.toml
              - name: nerdctl-config
                mountPath: /etc/nerdctl/nerdctl.toml
                subPath: nerdctl.toml
              - name: nerdctl-storage
                mountPath: /var/lib/containerd
          dnsConfig:
            nameservers:
            - iamIPaddr
            - iamIPaddr
          imagePullSecrets:
          - name: harbor-aliyun
          - name: harbor-inner
          volumes:
            - name: buildkitd-config
              configMap:
                name: buildkitd-configmap
            - name: nerdctl-config
              configMap:
                name: nerdctl-configmap
            - name: nerdctl-storage
              hostPath:
                path: /data/nerdctl-storage-containerd
                type: DirectoryOrCreate
      """.stripIndent()
    }
  }

  // 设置pipeline Jenkins选项参数
  options {
    skipDefaultCheckout true          // 忽略默认的checkout
    skipStagesAfterUnstable()         // 忽略报错后面的步骤
    // retry(2)                          // 重试次数
    disableConcurrentBuilds()         // java项目禁止并发构建：主要是gradle有锁，导致无法并发构建
    timestamps()                      // 添加时间戳
    timeout(time: 60, unit:'MINUTES') // 设置此次发版运行20分钟后超时
    buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', numToKeepStr: '100')  // 设置保留100次构建记录
  }

  parameters {
    booleanParam defaultValue: false,
                 description: '默认不启用，即不执行依赖更新；若有依赖更新，请勾选启用。',
                 name: 'IS_ENABLED_UPDATE_DEPENDENCIES'
    gitParameter branch: '',
                 branchFilter: '.*',
                 defaultValue: 'feature/rye',
                 listSize: '5',
                 description: '选择需要发布的代码分支',
                 name: 'BRANCH_TAG',
                 quickFilterEnabled: true,
                 selectedValue: 'NONE',
                 sortMode: 'NONE',
                 tagFilter: '*',
                 type: 'PT_BRANCH',
                 useRepository: "${git_address}"
    choice choices: ['server-name-python-rust'],
           description: '请选择本次发版需要部署的服务',
           name: 'DEPLOY_SVC_NAME'
    choice choices: ['hx-dev-1','hx-staging-1'],
           description: '发版到选中的运行环境',
           name: 'DEPLOY_TO_ENV'
    booleanParam defaultValue: false,
                 description: '默认不启用，pipeline中修改Jenkins parameters后需要重新生成新选项【运维调试用】。',
                 name: 'IS_DEBUG'
  }

  stages {
    stage('拉取代码') {
      steps {
        script {
          isDebugPipeline(params.IS_DEBUG)
          checkWhetherToContinue()
          echo '正在拉取代码...'
          env.COMMIT_SHORT_ID = gitCheckout(git_address, params.BRANCH_TAG, true)
          println(env.COMMIT_SHORT_ID)
        }
      }
    }

    stage('代码编译打包') {
      when {
        expression {
          return (params.IS_ENABLED_UPDATE_DEPENDENCIES)
        }
      }
      steps {
        container('package-tools') {
          echo '当前步骤为：代码编译环境准备与打包'
          script {
            echo "rust 国内依赖仓库配置"
            selectCargoBuildEnv()
            echo "当前正在构建服务：${params.DEPLOY_SVC_NAME}"
            withCredentials([usernameColonPassword(credentialsId: '3d040389-9dfe-4c0d-9dab-9f6487f10409', variable: 'USERPASS')]) {
              sh """
                pwd; ls -lha
                [ -d ${WORKSPACE}/.cargo ] || mkdir ${WORKSPACE}/.cargo
                cp -a config_file ${WORKSPACE}/.cargo/config
                export CARGO_HOME=${WORKSPACE}/.cargo
                export CARGO_NET_GIT_FETCH_WITH_CLI=true
                cargo --version
                uv --version
                echo 'https://${USERPASS}@code.betack.com' > ${WORKSPACE}/git-credentials
                git config --global credential.helper 'store --file ${WORKSPACE}/git-credentials'
                git config --global credential.helper
                export UV_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
                export UV_CACHE_DIR=${WORKSPACE}/.bf-cache-uv
                export VIRTUAL_ENV=${WORKSPACE}/.venv
                uv python list
                uv venv --python 3.10.14 --python-preference only-managed
                [ -d .bf-cache-uv ] || mkdir .bf-cache-uv
                uv pip compile pyproject.toml -o requirements.txt
                uv pip install -r requirements.txt -v
                #uv pip install -r requirements.lock --verbose
                #uv pip install --verbose -r pyproject.toml --pre
              """
            }
          }
        }
      }
    }

    stage('构建镜像上传HARBOR仓库') {
      steps {
        container('nerdctl') {
          script{
            for (deploy_svc_name in params.DEPLOY_SVC_NAME.tokenize(',')) {
              echo "创建Dockerfile"
              if (params.IS_ENABLED_UPDATE_DEPENDENCIES) {
                dockerFile = """
                  FROM ${office_registry}/libs/python:bf-v3.10.14-bookworm
                  LABEL maintainer="colin" version="1.0" datetime="2024-07-17"
                  COPY .venv/lib/python3.10/site-packages /usr/local/lib/python3.10/site-packages
                  COPY src /opt/betack/app
                  WORKDIR /opt/betack/app
                  CMD ["python", "endpoint/__main__.py"]
                """.stripIndent()
              } else {
                dockerFile = """
                  #FROM ${office_registry}/ck-chats/server-name-python-rust:latest
                  FROM ${office_registry}/ck-chats/server-name-python-rust1:bd7f744-900
                  LABEL maintainer="colin" version="1.0" datetime="2024-07-17"
                  COPY src /opt/betack/app
                  WORKDIR /opt/betack/app
                  CMD ["python", "endpoint/__main__.py"]
                """.stripIndent()
              }
              // ${office_registry}/betack/beta-chat-baseimage:v-python3.10.14
              // FROM ${office_registry}/libs/python:bf-v3.10.14-bookworm
              // FROM ${office_registry}/betack/server-name-python-rust1:latest
              imageDict = pythonCodeBuildContainerImageByUv(dockerFile: dockerFile,
                                                            project: project,
                                                            deploySVCName: params.DEPLOY_SVC_NAME)
            }
          }
        }
      }
    }

    stage('部署服务') {
      when {
        expression {
          return (params.DEPLOY_TO_ENV != '')
        }
      }
      steps {
        script {
          echo '正在从gitlab拉取项目的kustomization代码...'
          sh '[ -d temp_jenkins_workspace ] || mkdir temp_jenkins_workspace'
          dir("${env.WORKSPACE}/temp_jenkins_workspace") {
            gitCheckout('ssh://git@iamIPaddr:50022/argocd/hx-kustomize.git', 'main')
            sh 'pwd; ls -lh'
            // 循环处理需要部署的服务
            for (deploy_svc_name in params.DEPLOY_SVC_NAME.tokenize(',')) {
              // 循环处理需要部署的命名空间
              for (namespaces in params.DEPLOY_TO_ENV.tokenize(',')) {
                configEnv = libTools.splitNamespaces(namespaces)
                configEnvPrefix = configEnv[0]
                configEnvSuffix = configEnv[1]
                configENV = configEnv[2]
                println("CONFIG_ENV，configEnvSuffix：" + configEnvSuffix)
                println("项目简称，用于命名空间的前缀，configEnvPrefix：" + configEnvPrefix)
                println("configENV：" + configENV)
                switch(configENV) {
                  case 'staging1':
                    kustomizationYaml = "overlays/staging1/kustomization.yaml"
                  break
                  /* 生产环境根据各自发版流程决定是否在这里处理
                  case 'prod':
                    kustomizationYaml = "overlays/staging/kustomization.yaml"
                  break
                  */
                  default:
                    kustomizationYaml = "overlays/dev1/kustomization.yaml"
                  break
                }
                svcYaml = readYaml file: kustomizationYaml
                svcYaml['namespace'] = namespaces
                image_tag = "${COMMIT_SHORT_ID}-${BUILD_NUMBER}"
                // svcYaml['images'][0]['newName'] = imageDict.imageName.replaceAll(':' + image_tag, "")
                // svcYaml['images'][0]['newTag'] = image_tag
                // 因原有服务依赖项太多，pod会启动失败。用nginx镜像代替，拉起服务进行演示
                svcYaml['images'][0]['newName'] = 'nginx'
                svcYaml['images'][0]['newTag'] = '1.27.2'
                svcYaml['replicas'][0]['count'] = 1
                // println(svcYaml)
                writeYaml file: kustomizationYaml, data: svcYaml, overwrite: true
                sh "cat ${kustomizationYaml}"
                commit_message = "Automated commit: " + deploy_svc_name + ", " + image_tag
                // println(commit_message)
                gitPublisher(projectHTTPAddr: 'argocd/hx-kustomize.git',
                             branchTag: 'main',
                             commitMessage: commit_message)
                // error("this is test!!")
              }
            }
          }
        }
      }
    }
  }
}

